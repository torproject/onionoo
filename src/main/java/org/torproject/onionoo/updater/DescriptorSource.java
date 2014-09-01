/* Copyright 2013, 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.BridgePoolAssignment;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;
import org.torproject.onionoo.util.FormattingUtils;

public class DescriptorSource {

  private static final Logger log = LoggerFactory.getLogger(
      DescriptorSource.class);

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
      log.info("Loading: " + descriptorType);
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

    log.debug("Reading " + DescriptorType.RELAY_SERVER_DESCRIPTORS
        + " ...");
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS,
        DescriptorHistory.RELAY_SERVER_HISTORY, true);
    log.debug("Reading " + DescriptorType.RELAY_EXTRA_INFOS + " ...");
    this.readDescriptors(DescriptorType.RELAY_EXTRA_INFOS,
        DescriptorHistory.RELAY_EXTRAINFO_HISTORY, true);
    log.debug("Reading " + DescriptorType.EXIT_LISTS + " ...");
    this.readDescriptors(DescriptorType.EXIT_LISTS,
        DescriptorHistory.EXIT_LIST_HISTORY, true);
    log.debug("Reading " + DescriptorType.RELAY_CONSENSUSES + " ...");
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
    log.debug("Reading " + DescriptorType.BRIDGE_SERVER_DESCRIPTORS
        + " ...");
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS,
        DescriptorHistory.BRIDGE_SERVER_HISTORY, false);
    log.debug("Reading " + DescriptorType.BRIDGE_EXTRA_INFOS + " ...");
    this.readDescriptors(DescriptorType.BRIDGE_EXTRA_INFOS,
        DescriptorHistory.BRIDGE_EXTRAINFO_HISTORY, false);
    log.debug("Reading " + DescriptorType.BRIDGE_POOL_ASSIGNMENTS
        + " ...");
    this.readDescriptors(DescriptorType.BRIDGE_POOL_ASSIGNMENTS,
        DescriptorHistory.BRIDGE_POOLASSIGN_HISTORY, false);
    log.debug("Reading " + DescriptorType.BRIDGE_STATUSES + " ...");
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
      log.info("Read relay network consensuses");
      break;
    case RELAY_SERVER_DESCRIPTORS:
      log.info("Read relay server descriptors");
      break;
    case RELAY_EXTRA_INFOS:
      log.info("Read relay extra-info descriptors");
      break;
    case EXIT_LISTS:
      log.info("Read exit lists");
      break;
    case BRIDGE_STATUSES:
      log.info("Read bridge network statuses");
      break;
    case BRIDGE_SERVER_DESCRIPTORS:
      log.info("Read bridge server descriptors");
      break;
    case BRIDGE_EXTRA_INFOS:
      log.info("Read bridge extra-info descriptors");
      break;
    case BRIDGE_POOL_ASSIGNMENTS:
      log.info("Read bridge-pool assignments");
      break;
    }
  }

  public void writeHistoryFiles() {
    log.debug("Writing history ");
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
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        historySizeBefore) + " descriptors excluded from this "
        + "execution\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(descriptors)
        + " descriptors provided\n");
    sb.append("    " + FormattingUtils.formatBytes(bytes)
        + " provided\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        historySizeAfter) + " descriptors excluded from next "
        + "execution\n");
    return sb.toString();
  }
}

