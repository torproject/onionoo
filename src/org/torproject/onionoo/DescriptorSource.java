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
import java.util.TreeMap;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;

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

enum DescriptorHistory {
  RELAY_CONSENSUS_HISTORY,
  RELAY_EXTRAINFO_HISTORY,
  EXIT_LIST_HISTORY,
  BRIDGE_STATUS_HISTORY,
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
    this.descriptorReader.addDirectory(new File(this.inDir,
        directoryName));
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
    default:
      System.err.println("Unknown descriptor history.  Not excluding "
          + "files.");
      return;
    }
    this.historyFile = new File(this.statusDir, historyFileName);
    if (this.historyFile.exists()) {
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

  public void registerListener(DescriptorListener listener,
      DescriptorType descriptorType) {
    if (!this.descriptorListeners.containsKey(descriptorType)) {
      this.descriptorListeners.put(descriptorType,
          new HashSet<DescriptorListener>());
    }
    this.descriptorListeners.get(descriptorType).add(listener);
  }

  public void readRelayNetworkConsensuses() {
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
  }

  public void readRelayServerDescriptors() {
    // TODO Use parse history as soon as all listeners can handle it.
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS, null,
        true);
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
    // TODO Use parse history as soon as all listeners can handle it.
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS, null,
        false);
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
    Set<DescriptorListener> descriptorListeners =
        this.descriptorListeners.get(descriptorType);
    if (descriptorListeners == null || descriptorListeners.isEmpty()) {
      return;
    }
    DescriptorQueue descriptorQueue = this.getDescriptorQueue(
        descriptorType, descriptorHistory);
    Descriptor descriptor;
    while ((descriptor = descriptorQueue.nextDescriptor()) != null) {
      for (DescriptorListener descriptorListener : descriptorListeners) {
        descriptorListener.processDescriptor(descriptor, relay);
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
    sb.append("    " + String.format("%,d", historySizeBefore)
        + " descriptors excluded from this execution\n");
    sb.append("    " + String.format("%,d", descriptors)
        + " descriptors provided\n");
    sb.append("    " + formatBytes(bytes) + " provided\n");
    sb.append("    " + String.format("%,d", historySizeAfter)
        + " descriptors excluded from next execution\n");
    return sb.toString();
  }

  // TODO This method should go into a utility class.
  private static String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else {
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      return String.format("%.1f %siB", bytes / Math.pow(1024, exp),
          "KMGTPE".charAt(exp-1));
    }
  }
}

