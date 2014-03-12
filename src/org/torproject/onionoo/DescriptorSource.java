/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

class DescriptorQueue {

  private File inDir;

  private File statusDir;

  private DescriptorReader descriptorReader;

  private File historyFile;

  private Iterator<DescriptorFile> descriptorFiles;

  private List<Descriptor> descriptors;

  int historySizeBefore;

  int historySizeAfter;

  long returnedDescriptors = 0L;

  long returnedBytes = 0L;

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

  private File inDir;

  private File statusDir;

  private List<DescriptorQueue> descriptorQueues;

  public DescriptorSource(File inDir, File statusDir) {
    this.inDir = inDir;
    this.statusDir = statusDir;
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

  public void readRelayNetworkConsensuses() {
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
  }

  public void readRelayServerDescriptors() {
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS,
        DescriptorHistory.RELAY_SERVER_HISTORY, true);
  }

  public void readRelayExtraInfos() {
    this.readDescriptors(DescriptorType.RELAY_EXTRA_INFOS,
        DescriptorHistory.RELAY_EXTRAINFO_HISTORY, true);
  }

  public void readExitLists() {
    this.readDescriptors(DescriptorType.EXIT_LISTS,
        DescriptorHistory.EXIT_LIST_HISTORY, true);
  }

  public void readBridgeNetworkStatuses() {
    this.readDescriptors(DescriptorType.BRIDGE_STATUSES,
        DescriptorHistory.BRIDGE_STATUS_HISTORY, false);
  }

  public void readBridgeServerDescriptors() {
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS,
        DescriptorHistory.BRIDGE_SERVER_HISTORY, false);
  }

  public void readBridgeExtraInfos() {
    this.readDescriptors(DescriptorType.BRIDGE_EXTRA_INFOS,
        DescriptorHistory.BRIDGE_EXTRAINFO_HISTORY, false);
  }

  public void readBridgePoolAssignments() {
    this.readDescriptors(DescriptorType.BRIDGE_POOL_ASSIGNMENTS,
        DescriptorHistory.BRIDGE_POOLASSIGN_HISTORY, false);
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
  }

  public void writeHistoryFiles() {
    for (DescriptorQueue descriptorQueue : this.descriptorQueues) {
      descriptorQueue.writeHistoryFile();
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + this.descriptorQueues.size() + " descriptor "
        + "queues created\n");
    int historySizeBefore = 0, historySizeAfter = 0;
    long descriptors = 0L, bytes = 0L;
    for (DescriptorQueue descriptorQueue : descriptorQueues) {
      historySizeBefore += descriptorQueue.historySizeBefore;
      historySizeAfter += descriptorQueue.historySizeAfter;
      descriptors += descriptorQueue.returnedDescriptors;
      bytes += descriptorQueue.returnedBytes;
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

