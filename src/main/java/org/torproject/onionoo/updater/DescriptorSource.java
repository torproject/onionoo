/* Copyright 2013--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.descriptor.Descriptor;
import org.torproject.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DescriptorSource {

  private static final Logger log = LoggerFactory.getLogger(
      DescriptorSource.class);

  private final File inRecentDir = new File("in/recent");

  private final File inArchiveDir = new File("in/archive");

  private final File statusDir = new File("status");

  private List<DescriptorQueue> descriptorQueues;

  private DescriptorQueue archiveDescriptorQueue;

  /** Instantiates a new descriptor source. */
  public DescriptorSource() {
    this.descriptorQueues = new ArrayList<>();
    this.descriptorListeners = new HashMap<>();
  }

  private DescriptorQueue getDescriptorQueue(
      DescriptorType descriptorType,
      DescriptorHistory descriptorHistory) {
    DescriptorQueue descriptorQueue = new DescriptorQueue(
        this.inRecentDir, descriptorType, this.statusDir);
    if (descriptorHistory != null) {
      descriptorQueue.readHistoryFile(descriptorHistory);
    }
    this.descriptorQueues.add(descriptorQueue);
    return descriptorQueue;
  }

  private Map<DescriptorType, Set<DescriptorListener>>
      descriptorListeners;

  /** Registers a descriptor listener for a given descriptor type. */
  public void registerDescriptorListener(DescriptorListener listener,
      DescriptorType descriptorType) {
    if (!this.descriptorListeners.containsKey(descriptorType)) {
      this.descriptorListeners.put(descriptorType,
          new HashSet<DescriptorListener>());
    }
    this.descriptorListeners.get(descriptorType).add(listener);
  }

  /** Downloads descriptors from CollecTor. */
  public void downloadDescriptors() {
    for (DescriptorType descriptorType : DescriptorType.values()) {
      log.info("Loading: " + descriptorType);
      this.downloadDescriptors(descriptorType);
    }
  }

  private int localFilesBefore = 0;

  private int foundRemoteFiles = 0;

  private int downloadedFiles = 0;

  private int deletedLocalFiles = 0;

  private void downloadDescriptors(DescriptorType descriptorType) {
    DescriptorDownloader descriptorDownloader =
        new DescriptorDownloader(descriptorType);
    this.localFilesBefore += descriptorDownloader.statLocalFiles();
    this.foundRemoteFiles +=
        descriptorDownloader.fetchRemoteDirectory();
    this.downloadedFiles += descriptorDownloader.fetchRemoteFiles();
    this.deletedLocalFiles += descriptorDownloader.deleteOldLocalFiles();
  }

  /** Reads archived and recent descriptors from disk and feeds them into
   * any registered listeners. */
  public void readDescriptors() {
    this.readArchivedDescriptors();
    log.debug("Reading recent " + DescriptorType.RELAY_SERVER_DESCRIPTORS
        + " ...");
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS,
        DescriptorHistory.RELAY_SERVER_HISTORY, true);
    log.debug("Reading recent " + DescriptorType.RELAY_EXTRA_INFOS + " ...");
    this.readDescriptors(DescriptorType.RELAY_EXTRA_INFOS,
        DescriptorHistory.RELAY_EXTRAINFO_HISTORY, true);
    log.debug("Reading recent " + DescriptorType.EXIT_LISTS + " ...");
    this.readDescriptors(DescriptorType.EXIT_LISTS,
        DescriptorHistory.EXIT_LIST_HISTORY, true);
    log.debug("Reading recent " + DescriptorType.RELAY_CONSENSUSES + " ...");
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
    log.debug("Reading recent " + DescriptorType.BRIDGE_SERVER_DESCRIPTORS
        + " ...");
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS,
        DescriptorHistory.BRIDGE_SERVER_HISTORY, false);
    log.debug("Reading recent " + DescriptorType.BRIDGE_EXTRA_INFOS + " ...");
    this.readDescriptors(DescriptorType.BRIDGE_EXTRA_INFOS,
        DescriptorHistory.BRIDGE_EXTRAINFO_HISTORY, false);
    log.debug("Reading recent " + DescriptorType.BRIDGE_STATUSES + " ...");
    this.readDescriptors(DescriptorType.BRIDGE_STATUSES,
        DescriptorHistory.BRIDGE_STATUS_HISTORY, false);
  }

  private void readDescriptors(DescriptorType descriptorType,
      DescriptorHistory descriptorHistory, boolean relay) {
    if (!this.descriptorListeners.containsKey(descriptorType)) {
      return;
    }
    Set<DescriptorListener> descriptorListeners =
        this.descriptorListeners.get(descriptorType);
    DescriptorQueue descriptorQueue = this.getDescriptorQueue(
        descriptorType, descriptorHistory);
    Descriptor descriptor;
    while ((descriptor = descriptorQueue.nextDescriptor()) != null) {
      for (DescriptorListener descriptorListener : descriptorListeners) {
        descriptorListener.processDescriptor(descriptor, relay);
      }
    }
    switch (descriptorType) {
      case RELAY_CONSENSUSES:
        log.info("Read recent relay network consensuses");
        break;
      case RELAY_SERVER_DESCRIPTORS:
        log.info("Read recent relay server descriptors");
        break;
      case RELAY_EXTRA_INFOS:
        log.info("Read recent relay extra-info descriptors");
        break;
      case EXIT_LISTS:
        log.info("Read recent exit lists");
        break;
      case BRIDGE_STATUSES:
        log.info("Read recent bridge network statuses");
        break;
      case BRIDGE_SERVER_DESCRIPTORS:
        log.info("Read recent bridge server descriptors");
        break;
      case BRIDGE_EXTRA_INFOS:
        log.info("Read recent bridge extra-info descriptors");
        break;
      default:
        /* We shouldn't run into this default case, but if we do, it's
         * because we added a new type to DescriptorType but forgot to
         * update this switch statement.  It's just logging, so not the
         * end of the world. */
        log.info("Read recent descriptors of type " + descriptorType);
    }
  }

  /** Reads archived descriptors from disk and feeds them into any
   * registered listeners. */
  public void readArchivedDescriptors() {
    if (!this.inArchiveDir.exists()) {
      return;
    }
    log.info("Reading archived descriptors...");
    this.archiveDescriptorQueue = new DescriptorQueue(this.inArchiveDir,
        null, this.statusDir);
    this.archiveDescriptorQueue.readHistoryFile(
        DescriptorHistory.ARCHIVED_HISTORY);
    Descriptor descriptor;
    while ((descriptor = this.archiveDescriptorQueue.nextDescriptor())
        != null) {
      DescriptorType descriptorType = null;
      boolean relay = false;
      for (String annotation : descriptor.getAnnotations()) {
        if (annotation.startsWith(
            "@type network-status-consensus-3 1.")) {
          descriptorType = DescriptorType.RELAY_CONSENSUSES;
          relay = true;
        } else if (annotation.startsWith("@type server-descriptor 1.")) {
          descriptorType = DescriptorType.RELAY_SERVER_DESCRIPTORS;
          relay = true;
        } else if (annotation.startsWith("@type extra-info 1.")) {
          descriptorType = DescriptorType.RELAY_EXTRA_INFOS;
          relay = true;
        } else if (annotation.startsWith("@type tordnsel 1.")) {
          descriptorType = DescriptorType.EXIT_LISTS;
          relay = true;
        } else if (annotation.startsWith(
            "@type bridge-network-status 1.")) {
          descriptorType = DescriptorType.BRIDGE_STATUSES;
          relay = false;
        } else if (annotation.startsWith(
            "@type bridge-server-descriptor 1.")) {
          descriptorType = DescriptorType.BRIDGE_SERVER_DESCRIPTORS;
          relay = false;
        } else if (annotation.startsWith("@type bridge-extra-info 1.")) {
          descriptorType = DescriptorType.BRIDGE_EXTRA_INFOS;
          relay = false;
        }
      }
      if (descriptorType == null) {
        log.warn("Unrecognized descriptor in "
            + this.inArchiveDir.getAbsolutePath() + " with annotations "
            + descriptor.getAnnotations() + ".  Not reading any further"
            + "archived descriptors.");
        break;
      }
      for (DescriptorListener descriptorListener :
          this.descriptorListeners.get(descriptorType)) {
        descriptorListener.processDescriptor(descriptor, relay);
      }
    }
    this.archiveDescriptorQueue.writeHistoryFile();
    log.info("Read archived descriptors");
  }

  /** Writes parse histories for recent descriptors to disk. */
  public void writeHistoryFiles() {
    log.debug("Writing parse histories for recent descriptors...");
    for (DescriptorQueue descriptorQueue : this.descriptorQueues) {
      descriptorQueue.writeHistoryFile();
    }
  }

  /** Returns a string with statistics on the number of processed
   * descriptors during the current execution. */
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + this.localFilesBefore + " recent descriptor files "
        + "found locally\n");
    sb.append("    " + this.foundRemoteFiles + " recent descriptor files "
        + "found remotely\n");
    sb.append("    " + this.downloadedFiles + " recent descriptor files "
        + "downloaded from remote\n");
    sb.append("    " + this.deletedLocalFiles + " recent descriptor "
        + "files deleted locally\n");
    sb.append("    " + this.descriptorQueues.size() + " descriptor "
        + "queues created for recent descriptors\n");
    int historySizeBefore = 0;
    int historySizeAfter = 0;
    long descriptors = 0L;
    long bytes = 0L;
    for (DescriptorQueue descriptorQueue : this.descriptorQueues) {
      historySizeBefore += descriptorQueue.getHistorySizeBefore();
      historySizeAfter += descriptorQueue.getHistorySizeAfter();
      descriptors += descriptorQueue.getReturnedDescriptors();
      bytes += descriptorQueue.getReturnedBytes();
    }
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        historySizeBefore) + " recent descriptors excluded from this "
        + "execution\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(descriptors)
        + " recent descriptors provided\n");
    sb.append("    " + FormattingUtils.formatBytes(bytes)
        + " of recent descriptors provided\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        historySizeAfter) + " recent descriptors excluded from next "
        + "execution\n");
    if (this.archiveDescriptorQueue != null) {
      sb.append("    " + FormattingUtils.formatDecimalNumber(
          this.archiveDescriptorQueue.getReturnedDescriptors())
          + " archived descriptors provided\n");
      sb.append("    " + FormattingUtils.formatBytes(
          this.archiveDescriptorQueue.getReturnedBytes()) + " of "
          + "archived descriptors provided\n");
    }
    return sb.toString();
  }
}

