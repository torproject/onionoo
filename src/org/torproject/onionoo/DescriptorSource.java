/* Copyright 2013, 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.BridgePoolAssignment;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

enum DescriptorType {
  RELAY_CONSENSUSES,
  RELAY_SERVER_DESCRIPTORS,
  RELAY_EXTRA_INFOS,
  EXIT_LISTS,
  BRIDGE_STATUSES,
  BRIDGE_SERVER_DESCRIPTORS,
  BRIDGE_EXTRA_INFOS,
  BRIDGE_POOL_ASSIGNMENTS,
}

interface DescriptorListener {
  abstract void processDescriptor(Descriptor descriptor, boolean relay);
}

interface FingerprintListener {
  abstract void processFingerprints(SortedSet<String> fingerprints,
      boolean relay);
}

enum DescriptorHistory {
  RELAY_CONSENSUS_HISTORY,
  RELAY_SERVER_HISTORY,
  RELAY_EXTRAINFO_HISTORY,
  EXIT_LIST_HISTORY,
  BRIDGE_STATUS_HISTORY,
  BRIDGE_SERVER_HISTORY,
  BRIDGE_EXTRAINFO_HISTORY,
  BRIDGE_POOLASSIGN_HISTORY,
}

class DescriptorDownloader {

  private final String protocolHostNameResourcePrefix =
      "https://collector.torproject.org/recent/";

  private String directory;

  private final File inDir = new File("in/recent");

  public DescriptorDownloader(DescriptorType descriptorType) {
    switch (descriptorType) {
    case RELAY_CONSENSUSES:
      this.directory = "relay-descriptors/consensuses/";
      break;
    case RELAY_SERVER_DESCRIPTORS:
      this.directory = "relay-descriptors/server-descriptors/";
      break;
    case RELAY_EXTRA_INFOS:
      this.directory = "relay-descriptors/extra-infos/";
      break;
    case EXIT_LISTS:
      this.directory = "exit-lists/";
      break;
    case BRIDGE_STATUSES:
      this.directory = "bridge-descriptors/statuses/";
      break;
    case BRIDGE_SERVER_DESCRIPTORS:
      this.directory = "bridge-descriptors/server-descriptors/";
      break;
    case BRIDGE_EXTRA_INFOS:
      this.directory = "bridge-descriptors/extra-infos/";
      break;
    case BRIDGE_POOL_ASSIGNMENTS:
      this.directory = "bridge-pool-assignments/";
      break;
    default:
      System.err.println("Unknown descriptor type.");
      return;
    }
  }

  private SortedSet<String> localFiles = new TreeSet<String>();

  public int statLocalFiles() {
    File localDirectory = new File(this.inDir, this.directory);
    if (localDirectory.exists()) {
      for (File file : localDirectory.listFiles()) {
        this.localFiles.add(file.getName());
      }
    }
    return this.localFiles.size();
  }

  private SortedSet<String> remoteFiles = new TreeSet<String>();

  public int fetchRemoteDirectory() {
    String directoryUrl = this.protocolHostNameResourcePrefix
        + this.directory;
    try {
      URL u = new URL(directoryUrl);
      HttpURLConnection huc = (HttpURLConnection) u.openConnection();
      huc.setRequestMethod("GET");
      huc.connect();
      if (huc.getResponseCode() != 200) {
        System.err.println("Could not fetch " + directoryUrl
            + ": " + huc.getResponseCode() + " "
            + huc.getResponseMessage() + ".  Skipping.");
        return 0;
      }
      BufferedReader br = new BufferedReader(new InputStreamReader(
          huc.getInputStream()));
      String line;
      while ((line = br.readLine()) != null) {
        if (!line.trim().startsWith("<tr>") ||
            !line.contains("<a href=\"")) {
          continue;
        }
        String linePart = line.substring(
            line.indexOf("<a href=\"") + "<a href=\"".length());
        if (!linePart.contains("\"")) {
          continue;
        }
        linePart = linePart.substring(0, linePart.indexOf("\""));
        if (linePart.endsWith("/")) {
          continue;
        }
        this.remoteFiles.add(linePart);
      }
      br.close();
    } catch (IOException e) {
      System.err.println("Could not fetch or parse " + directoryUrl
          + ".  Skipping.");
    }
    return this.remoteFiles.size();
  }

  public int fetchRemoteFiles() {
    int fetchedFiles = 0;
    for (String remoteFile : this.remoteFiles) {
      if (this.localFiles.contains(remoteFile)) {
        continue;
      }
      String fileUrl = this.protocolHostNameResourcePrefix
          + this.directory + remoteFile;
      File localTempFile = new File(this.inDir, this.directory
          + remoteFile + ".tmp");
      File localFile = new File(this.inDir, this.directory + remoteFile);
      try {
        localFile.getParentFile().mkdirs();
        URL u = new URL(fileUrl);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.addRequestProperty("Accept-Encoding", "gzip");
        huc.connect();
        if (huc.getResponseCode() != 200) {
          System.err.println("Could not fetch " + fileUrl
              + ": " + huc.getResponseCode() + " "
              + huc.getResponseMessage() + ".  Skipping.");
          continue;
        }
        long lastModified = huc.getHeaderFieldDate("Last-Modified", -1L);
        InputStream is;
        if (huc.getContentEncoding() != null &&
            huc.getContentEncoding().equalsIgnoreCase("gzip")) {
          is = new GZIPInputStream(huc.getInputStream());
        } else {
          is = huc.getInputStream();
        }
        BufferedInputStream bis = new BufferedInputStream(is);
        BufferedOutputStream bos = new BufferedOutputStream(
            new FileOutputStream(localTempFile));
        int len;
        byte[] data = new byte[1024];
        while ((len = bis.read(data, 0, 1024)) >= 0) {
          bos.write(data, 0, len);
        }
        bis.close();
        bos.close();
        localTempFile.renameTo(localFile);
        if (lastModified >= 0) {
          localFile.setLastModified(lastModified);
        }
        fetchedFiles++;
      } catch (IOException e) {
        System.err.println("Could not fetch or store " + fileUrl
            + ".  Skipping.");
      }
    }
    return fetchedFiles;
  }

  public int deleteOldLocalFiles() {
    int deletedFiles = 0;
    for (String localFile : this.localFiles) {
      if (!this.remoteFiles.contains(localFile)) {
        new File(this.inDir, this.directory + localFile).delete();
        deletedFiles++;
      }
    }
    return deletedFiles;
  }
}

class DescriptorQueue {

  private File inDir;

  private File statusDir;

  private DescriptorReader descriptorReader;

  private File historyFile;

  private Iterator<DescriptorFile> descriptorFiles;

  private List<Descriptor> descriptors;

  private int historySizeBefore;
  public int getHistorySizeBefore() {
    return this.historySizeBefore;
  }

  private int historySizeAfter;
  public int getHistorySizeAfter() {
    return this.historySizeAfter;
  }

  private long returnedDescriptors = 0L;
  public long getReturnedDescriptors() {
    return this.returnedDescriptors;
  }

  private long returnedBytes = 0L;
  public long getReturnedBytes() {
    return this.returnedBytes;
  }

  public DescriptorQueue(File inDir, File statusDir) {
    this.inDir = inDir;
    this.statusDir = statusDir;
    this.descriptorReader =
        DescriptorSourceFactory.createDescriptorReader();
  }

  public void addDirectory(DescriptorType descriptorType) {
    String directoryName = null;
    switch (descriptorType) {
    case RELAY_CONSENSUSES:
      directoryName = "relay-descriptors/consensuses";
      break;
    case RELAY_SERVER_DESCRIPTORS:
      directoryName = "relay-descriptors/server-descriptors";
      break;
    case RELAY_EXTRA_INFOS:
      directoryName = "relay-descriptors/extra-infos";
      break;
    case BRIDGE_STATUSES:
      directoryName = "bridge-descriptors/statuses";
      break;
    case BRIDGE_SERVER_DESCRIPTORS:
      directoryName = "bridge-descriptors/server-descriptors";
      break;
    case BRIDGE_EXTRA_INFOS:
      directoryName = "bridge-descriptors/extra-infos";
      break;
    case BRIDGE_POOL_ASSIGNMENTS:
      directoryName = "bridge-pool-assignments";
      break;
    case EXIT_LISTS:
      directoryName = "exit-lists";
      break;
    default:
      System.err.println("Unknown descriptor type.  Not adding directory "
          + "to descriptor reader.");
      return;
    }
    File directory = new File(this.inDir, directoryName);
    if (directory.exists() && directory.isDirectory()) {
      this.descriptorReader.addDirectory(directory);
      this.descriptorReader.setMaxDescriptorFilesInQueue(1);
    } else {
      System.err.println("Directory " + directory.getAbsolutePath()
          + " either does not exist or is not a directory.  Not adding "
          + "to descriptor reader.");
    }
  }

  public void readHistoryFile(DescriptorHistory descriptorHistory) {
    String historyFileName = null;
    switch (descriptorHistory) {
    case RELAY_EXTRAINFO_HISTORY:
      historyFileName = "relay-extrainfo-history";
      break;
    case BRIDGE_EXTRAINFO_HISTORY:
      historyFileName = "bridge-extrainfo-history";
      break;
    case EXIT_LIST_HISTORY:
      historyFileName = "exit-list-history";
      break;
    case BRIDGE_POOLASSIGN_HISTORY:
      historyFileName = "bridge-poolassign-history";
      break;
    case RELAY_CONSENSUS_HISTORY:
      historyFileName = "relay-consensus-history";
      break;
    case BRIDGE_STATUS_HISTORY:
      historyFileName = "bridge-status-history";
      break;
    case RELAY_SERVER_HISTORY:
      historyFileName = "relay-server-history";
      break;
    case BRIDGE_SERVER_HISTORY:
      historyFileName = "bridge-server-history";
      break;
    default:
      System.err.println("Unknown descriptor history.  Not excluding "
          + "files.");
      return;
    }
    this.historyFile = new File(this.statusDir, historyFileName);
    if (this.historyFile.exists() && this.historyFile.isFile()) {
      SortedMap<String, Long> excludedFiles = new TreeMap<String, Long>();
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            this.historyFile));
        String line;
        while ((line = br.readLine()) != null) {
          try {
            String[] parts = line.split(" ", 2);
            excludedFiles.put(parts[1], Long.parseLong(parts[0]));
          } catch (NumberFormatException e) {
            System.err.println("Illegal line '" + line + "' in parse "
                + "history.  Skipping line.");
          }
        }
        br.close();
      } catch (IOException e) {
        System.err.println("Could not read history file '"
            + this.historyFile.getAbsolutePath() + "'.  Not excluding "
            + "descriptors in this execution.");
        e.printStackTrace();
        return;
      }
      this.historySizeBefore = excludedFiles.size();
      this.descriptorReader.setExcludedFiles(excludedFiles);
    }
  }

  public void writeHistoryFile() {
    if (this.historyFile == null) {
      return;
    }
    SortedMap<String, Long> excludedAndParsedFiles =
        new TreeMap<String, Long>();
    excludedAndParsedFiles.putAll(
        this.descriptorReader.getExcludedFiles());
    excludedAndParsedFiles.putAll(this.descriptorReader.getParsedFiles());
    this.historySizeAfter = excludedAndParsedFiles.size();
    try {
      this.historyFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.historyFile));
      for (Map.Entry<String, Long> e : excludedAndParsedFiles.entrySet()) {
        String absolutePath = e.getKey();
        long lastModifiedMillis = e.getValue();
        bw.write(String.valueOf(lastModifiedMillis) + " " + absolutePath
            + "\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write history file '"
          + this.historyFile.getAbsolutePath() + "'.  Not excluding "
          + "descriptors in next execution.");
      return;
    }
  }

  public Descriptor nextDescriptor() {
    Descriptor nextDescriptor = null;
    if (this.descriptorFiles == null) {
      this.descriptorFiles = this.descriptorReader.readDescriptors();
    }
    while (this.descriptors == null && this.descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = this.descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        System.err.println("Could not parse "
            + descriptorFile.getFileName());
        descriptorFile.getException().printStackTrace();
      }
      if (descriptorFile.getDescriptors() != null &&
          !descriptorFile.getDescriptors().isEmpty()) {
        this.descriptors = descriptorFile.getDescriptors();
      }
    }
    if (this.descriptors != null) {
      nextDescriptor = this.descriptors.remove(0);
      this.returnedDescriptors++;
      this.returnedBytes += nextDescriptor.getRawDescriptorBytes().length;
      if (this.descriptors.isEmpty()) {
        this.descriptors = null;
      }
    }
    return nextDescriptor;
  }
}

public class DescriptorSource {

  private final File inDir = new File("in/recent");

  private final File statusDir = new File("status");

  private List<DescriptorQueue> descriptorQueues;

  public DescriptorSource() {
    this.descriptorQueues = new ArrayList<DescriptorQueue>();
    this.descriptorListeners =
        new HashMap<DescriptorType, Set<DescriptorListener>>();
    this.fingerprintListeners =
        new HashMap<DescriptorType, Set<FingerprintListener>>();
  }

  private DescriptorQueue getDescriptorQueue(
      DescriptorType descriptorType,
      DescriptorHistory descriptorHistory) {
    DescriptorQueue descriptorQueue = new DescriptorQueue(this.inDir,
        this.statusDir);
    descriptorQueue.addDirectory(descriptorType);
    if (descriptorHistory != null) {
      descriptorQueue.readHistoryFile(descriptorHistory);
    }
    this.descriptorQueues.add(descriptorQueue);
    return descriptorQueue;
  }

  private Map<DescriptorType, Set<DescriptorListener>>
      descriptorListeners;

  private Map<DescriptorType, Set<FingerprintListener>>
      fingerprintListeners;

  public void registerDescriptorListener(DescriptorListener listener,
      DescriptorType descriptorType) {
    if (!this.descriptorListeners.containsKey(descriptorType)) {
      this.descriptorListeners.put(descriptorType,
          new HashSet<DescriptorListener>());
    }
    this.descriptorListeners.get(descriptorType).add(listener);
  }

  public void registerFingerprintListener(FingerprintListener listener,
      DescriptorType descriptorType) {
    if (!this.fingerprintListeners.containsKey(descriptorType)) {
      this.fingerprintListeners.put(descriptorType,
          new HashSet<FingerprintListener>());
    }
    this.fingerprintListeners.get(descriptorType).add(listener);
  }

  public void downloadDescriptors() {
    for (DescriptorType descriptorType : DescriptorType.values()) {
      this.downloadDescriptors(descriptorType);
    }
  }

  private int localFilesBefore = 0, foundRemoteFiles = 0,
      downloadedFiles = 0, deletedLocalFiles = 0;

  private void downloadDescriptors(DescriptorType descriptorType) {
    if (!this.descriptorListeners.containsKey(descriptorType) &&
        !this.fingerprintListeners.containsKey(descriptorType)) {
      return;
    }
    DescriptorDownloader descriptorDownloader =
        new DescriptorDownloader(descriptorType);
    this.localFilesBefore += descriptorDownloader.statLocalFiles();
    this.foundRemoteFiles +=
        descriptorDownloader.fetchRemoteDirectory();
    this.downloadedFiles += descriptorDownloader.fetchRemoteFiles();
    this.deletedLocalFiles += descriptorDownloader.deleteOldLocalFiles();
  }

  public void readDescriptors() {
    /* Careful when changing the order of parsing descriptor types!  The
     * various status updaters may base assumptions on this order. */
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS,
        DescriptorHistory.RELAY_SERVER_HISTORY, true);
    this.readDescriptors(DescriptorType.RELAY_EXTRA_INFOS,
        DescriptorHistory.RELAY_EXTRAINFO_HISTORY, true);
    this.readDescriptors(DescriptorType.EXIT_LISTS,
        DescriptorHistory.EXIT_LIST_HISTORY, true);
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS,
        DescriptorHistory.BRIDGE_SERVER_HISTORY, false);
    this.readDescriptors(DescriptorType.BRIDGE_EXTRA_INFOS,
        DescriptorHistory.BRIDGE_EXTRAINFO_HISTORY, false);
    this.readDescriptors(DescriptorType.BRIDGE_POOL_ASSIGNMENTS,
        DescriptorHistory.BRIDGE_POOLASSIGN_HISTORY, false);
    this.readDescriptors(DescriptorType.BRIDGE_STATUSES,
        DescriptorHistory.BRIDGE_STATUS_HISTORY, false);
  }

  private void readDescriptors(DescriptorType descriptorType,
      DescriptorHistory descriptorHistory, boolean relay) {
    if (!this.descriptorListeners.containsKey(descriptorType) &&
        !this.fingerprintListeners.containsKey(descriptorType)) {
      return;
    }
    Set<DescriptorListener> descriptorListeners =
        this.descriptorListeners.get(descriptorType);
    Set<FingerprintListener> fingerprintListeners =
        this.fingerprintListeners.get(descriptorType);
    DescriptorQueue descriptorQueue = this.getDescriptorQueue(
        descriptorType, descriptorHistory);
    Descriptor descriptor;
    while ((descriptor = descriptorQueue.nextDescriptor()) != null) {
      for (DescriptorListener descriptorListener : descriptorListeners) {
        descriptorListener.processDescriptor(descriptor, relay);
      }
      if (fingerprintListeners == null) {
        continue;
      }
      SortedSet<String> fingerprints = new TreeSet<String>();
      if (descriptorType == DescriptorType.RELAY_CONSENSUSES &&
          descriptor instanceof RelayNetworkStatusConsensus) {
        fingerprints.addAll(((RelayNetworkStatusConsensus) descriptor).
            getStatusEntries().keySet());
      } else if (descriptorType
          == DescriptorType.RELAY_SERVER_DESCRIPTORS &&
          descriptor instanceof ServerDescriptor) {
        fingerprints.add(((ServerDescriptor) descriptor).
            getFingerprint());
      } else if (descriptorType == DescriptorType.RELAY_EXTRA_INFOS &&
          descriptor instanceof ExtraInfoDescriptor) {
        fingerprints.add(((ExtraInfoDescriptor) descriptor).
            getFingerprint());
      } else if (descriptorType == DescriptorType.EXIT_LISTS &&
          descriptor instanceof ExitList) {
        for (ExitListEntry entry :
            ((ExitList) descriptor).getExitListEntries()) {
          fingerprints.add(entry.getFingerprint());
        }
      } else if (descriptorType == DescriptorType.BRIDGE_STATUSES &&
          descriptor instanceof BridgeNetworkStatus) {
        fingerprints.addAll(((BridgeNetworkStatus) descriptor).
            getStatusEntries().keySet());
      } else if (descriptorType ==
          DescriptorType.BRIDGE_SERVER_DESCRIPTORS &&
          descriptor instanceof ServerDescriptor) {
        fingerprints.add(((ServerDescriptor) descriptor).
            getFingerprint());
      } else if (descriptorType == DescriptorType.BRIDGE_EXTRA_INFOS &&
          descriptor instanceof ExtraInfoDescriptor) {
        fingerprints.add(((ExtraInfoDescriptor) descriptor).
            getFingerprint());
      } else if (descriptorType ==
          DescriptorType.BRIDGE_POOL_ASSIGNMENTS &&
          descriptor instanceof BridgePoolAssignment) {
        fingerprints.addAll(((BridgePoolAssignment) descriptor).
            getEntries().keySet());
      }
      for (FingerprintListener fingerprintListener :
          fingerprintListeners) {
        fingerprintListener.processFingerprints(fingerprints, relay);
      }
    }
    switch (descriptorType) {
    case RELAY_CONSENSUSES:
      Logger.printStatusTime("Read relay network consensuses");
      break;
    case RELAY_SERVER_DESCRIPTORS:
      Logger.printStatusTime("Read relay server descriptors");
      break;
    case RELAY_EXTRA_INFOS:
      Logger.printStatusTime("Read relay extra-info descriptors");
      break;
    case EXIT_LISTS:
      Logger.printStatusTime("Read exit lists");
      break;
    case BRIDGE_STATUSES:
      Logger.printStatusTime("Read bridge network statuses");
      break;
    case BRIDGE_SERVER_DESCRIPTORS:
      Logger.printStatusTime("Read bridge server descriptors");
      break;
    case BRIDGE_EXTRA_INFOS:
      Logger.printStatusTime("Read bridge extra-info descriptors");
      break;
    case BRIDGE_POOL_ASSIGNMENTS:
      Logger.printStatusTime("Read bridge-pool assignments");
      break;
    }
  }

  public void writeHistoryFiles() {
    for (DescriptorQueue descriptorQueue : this.descriptorQueues) {
      descriptorQueue.writeHistoryFile();
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + this.localFilesBefore + " descriptor files found "
        + "locally\n");
    sb.append("    " + this.foundRemoteFiles + " descriptor files found "
        + "remotely\n");
    sb.append("    " + this.downloadedFiles + " descriptor files "
        + "downloaded from remote\n");
    sb.append("    " + this.deletedLocalFiles + " descriptor files "
        + "deleted locally\n");
    sb.append("    " + this.descriptorQueues.size() + " descriptor "
        + "queues created\n");
    int historySizeBefore = 0, historySizeAfter = 0;
    long descriptors = 0L, bytes = 0L;
    for (DescriptorQueue descriptorQueue : descriptorQueues) {
      historySizeBefore += descriptorQueue.getHistorySizeBefore();
      historySizeAfter += descriptorQueue.getHistorySizeAfter();
      descriptors += descriptorQueue.getReturnedDescriptors();
      bytes += descriptorQueue.getReturnedBytes();
    }
    sb.append("    " + Logger.formatDecimalNumber(historySizeBefore)
        + " descriptors excluded from this execution\n");
    sb.append("    " + Logger.formatDecimalNumber(descriptors)
        + " descriptors provided\n");
    sb.append("    " + Logger.formatBytes(bytes) + " provided\n");
    sb.append("    " + Logger.formatDecimalNumber(historySizeAfter)
        + " descriptors excluded from next execution\n");
    return sb.toString();
  }
}

