/* Copyright 2013--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorCollector;
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

  private final File inDir = new File("in");

  private final File inRecentDir = new File(inDir, "recent");

  private final File inArchiveDir = new File(inDir, "archive");

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
    List<String> remoteDirectories = new ArrayList<>();
    for (DescriptorType descriptorType : DescriptorType.values()) {
      remoteDirectories.add("/recent/" + descriptorType.getDir());
    }
    DescriptorCollector dc = org.torproject.descriptor.DescriptorSourceFactory
        .createDescriptorCollector();
    dc.collectDescriptors("https://collector.torproject.org",
        remoteDirectories.toArray(new String[0]), 0L, inDir, true);
  }

  /** Reads archived and recent descriptors from disk and feeds them into
   * any registered listeners. */
  public void readDescriptors() {
    this.readArchivedDescriptors();
    log.debug("Reading recent {} ...", DescriptorType.RELAY_SERVER_DESCRIPTORS);
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS,
        DescriptorHistory.RELAY_SERVER_HISTORY, true);
    log.debug("Reading recent {} ...", DescriptorType.RELAY_EXTRA_INFOS);
    this.readDescriptors(DescriptorType.RELAY_EXTRA_INFOS,
        DescriptorHistory.RELAY_EXTRAINFO_HISTORY, true);
    log.debug("Reading recent {} ...", DescriptorType.EXIT_LISTS);
    this.readDescriptors(DescriptorType.EXIT_LISTS,
        DescriptorHistory.EXIT_LIST_HISTORY, true);
    log.debug("Reading recent {} ...", DescriptorType.RELAY_CONSENSUSES);
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
    log.debug("Reading recent {} ...",
        DescriptorType.BRIDGE_SERVER_DESCRIPTORS);
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS,
        DescriptorHistory.BRIDGE_SERVER_HISTORY, false);
    log.debug("Reading recent {} ...", DescriptorType.BRIDGE_EXTRA_INFOS);
    this.readDescriptors(DescriptorType.BRIDGE_EXTRA_INFOS,
        DescriptorHistory.BRIDGE_EXTRAINFO_HISTORY, false);
    log.debug("Reading recent {} ...", DescriptorType.BRIDGE_STATUSES);
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
    log.info("Read recent/{}.", descriptorType.getDir());
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
            + descriptor.getAnnotations() + ".  Skipping descriptor.");
        continue;
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
    sb.append("    ").append(this.descriptorQueues.size())
      .append(" descriptor ").append("queues created for recent descriptors\n");
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
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        historySizeBefore)).append(" recent descriptors excluded from this ")
        .append("execution\n");
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(descriptors))
        .append(" recent descriptors provided\n");
    sb.append("    ").append(FormattingUtils.formatBytes(bytes))
        .append(" of recent descriptors provided\n");
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        historySizeAfter)).append(" recent descriptors excluded from next ")
        .append("execution\n");
    if (this.archiveDescriptorQueue != null) {
      sb.append("    ").append(FormattingUtils.formatDecimalNumber(
          this.archiveDescriptorQueue.getReturnedDescriptors()))
        .append(" archived descriptors provided\n");
      sb.append("    ").append(FormattingUtils.formatBytes(
          this.archiveDescriptorQueue.getReturnedBytes())).append(" of ")
        .append("archived descriptors provided\n");
    }
    return sb.toString();
  }
}

