/* Copyright 2013--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorCollector;
import org.torproject.metrics.onionoo.util.FormattingUtils;

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

  private static final Logger logger = LoggerFactory.getLogger(
      DescriptorSource.class);

  private final File inDir = new File("in");

  private final String[] collecTorHosts = new String[] {
      "collector.torproject.org", "collector2.torproject.org" };

  private File[] inCollecTorHostDirs;

  private File[] inCollecTorHostRecentDirs;

  private List<DescriptorQueue> recentDescriptorQueues;

  private final File inArchiveDir = new File(inDir, "archive");

  private final File statusDir = new File("status");

  private DescriptorQueue archiveDescriptorQueue;

  /** Instantiates a new descriptor source. */
  public DescriptorSource() {
    this.inCollecTorHostDirs = new File[this.collecTorHosts.length];
    this.inCollecTorHostRecentDirs = new File[this.collecTorHosts.length];
    for (int collecTorHostIndex = 0;
         collecTorHostIndex < this.collecTorHosts.length;
         collecTorHostIndex++) {
      this.inCollecTorHostDirs[collecTorHostIndex]
          = new File(this.statusDir, this.collecTorHosts[collecTorHostIndex]);
      this.inCollecTorHostRecentDirs[collecTorHostIndex]
          = new File(this.inCollecTorHostDirs[collecTorHostIndex], "recent");
    }
    this.recentDescriptorQueues = new ArrayList<>();
    this.descriptorListeners = new HashMap<>();
  }

  private List<DescriptorQueue> getDescriptorQueues(
      DescriptorType descriptorType,
      DescriptorHistory descriptorHistory) {
    List<DescriptorQueue> descriptorQueues = new ArrayList<>();
    for (int collecTorHostIndex = 0;
         collecTorHostIndex < this.collecTorHosts.length;
         collecTorHostIndex++) {
      DescriptorQueue descriptorQueue = new DescriptorQueue(
          this.inCollecTorHostRecentDirs[collecTorHostIndex], descriptorType,
          this.inCollecTorHostDirs[collecTorHostIndex]);
      if (descriptorHistory != null) {
        descriptorQueue.readHistoryFile(descriptorHistory);
      }
      descriptorQueues.add(descriptorQueue);
    }
    this.recentDescriptorQueues.addAll(descriptorQueues);
    return descriptorQueues;
  }

  private Map<DescriptorType, Set<DescriptorListener>>
      descriptorListeners;

  /** Registers a descriptor listener for a given descriptor type. */
  public void registerDescriptorListener(DescriptorListener listener,
      DescriptorType descriptorType) {
    this.descriptorListeners.putIfAbsent(descriptorType, new HashSet<>());
    this.descriptorListeners.get(descriptorType).add(listener);
  }

  /** Downloads descriptors from CollecTor. */
  public void downloadDescriptors() {
    List<String> remoteDirectoriesList = new ArrayList<>();
    for (DescriptorType descriptorType : DescriptorType.values()) {
      remoteDirectoriesList.add("/recent/" + descriptorType.getDir());
    }
    for (int collecTorHostIndex = 0;
         collecTorHostIndex < this.collecTorHosts.length;
         collecTorHostIndex++) {
      String collecTorBaseUrl = "https://"
          + this.collecTorHosts[collecTorHostIndex];
      String[] remoteDirectories = remoteDirectoriesList.toArray(new String[0]);
      long minLastModified = 0L;
      File localDirectory = this.inCollecTorHostDirs[collecTorHostIndex];
      boolean deleteExtraneousLocalFiles = true;
      DescriptorCollector dc = org.torproject.descriptor.DescriptorSourceFactory
          .createDescriptorCollector();
      dc.collectDescriptors(collecTorBaseUrl, remoteDirectories,
          minLastModified, localDirectory, deleteExtraneousLocalFiles);
    }
  }

  /** Reads archived and recent descriptors from disk and feeds them into
   * any registered listeners. */
  public void readDescriptors() {
    this.readArchivedDescriptors();
    logger.debug("Reading recent {} ...",
        DescriptorType.RELAY_SERVER_DESCRIPTORS);
    this.readDescriptors(DescriptorType.RELAY_SERVER_DESCRIPTORS,
        DescriptorHistory.RELAY_SERVER_HISTORY, true);
    logger.debug("Reading recent {} ...", DescriptorType.RELAY_EXTRA_INFOS);
    this.readDescriptors(DescriptorType.RELAY_EXTRA_INFOS,
        DescriptorHistory.RELAY_EXTRAINFO_HISTORY, true);
    logger.debug("Reading recent {} ...", DescriptorType.EXIT_LISTS);
    this.readDescriptors(DescriptorType.EXIT_LISTS,
        DescriptorHistory.EXIT_LIST_HISTORY, true);
    logger.debug("Reading recent {} ...", DescriptorType.RELAY_CONSENSUSES);
    this.readDescriptors(DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY, true);
    logger.debug("Reading recent {} ...",
        DescriptorType.BRIDGE_SERVER_DESCRIPTORS);
    this.readDescriptors(DescriptorType.BRIDGE_SERVER_DESCRIPTORS,
        DescriptorHistory.BRIDGE_SERVER_HISTORY, false);
    logger.debug("Reading recent {} ...", DescriptorType.BRIDGE_EXTRA_INFOS);
    this.readDescriptors(DescriptorType.BRIDGE_EXTRA_INFOS,
        DescriptorHistory.BRIDGE_EXTRAINFO_HISTORY, false);
    logger.debug("Reading recent {} ...", DescriptorType.BRIDGE_STATUSES);
    this.readDescriptors(DescriptorType.BRIDGE_STATUSES,
        DescriptorHistory.BRIDGE_STATUS_HISTORY, false);
    logger.debug("Reading recent {} ...",
        DescriptorType.BRIDGE_POOL_ASSIGNMENTS);
    this.readDescriptors(DescriptorType.BRIDGE_POOL_ASSIGNMENTS,
        DescriptorHistory.BRIDGE_POOL_ASSIGNMENTS_HISTORY, false);
  }

  private void readDescriptors(DescriptorType descriptorType,
      DescriptorHistory descriptorHistory, boolean relay) {
    if (!this.descriptorListeners.containsKey(descriptorType)) {
      return;
    }
    Set<DescriptorListener> descriptorListeners =
        this.descriptorListeners.get(descriptorType);
    for (DescriptorQueue descriptorQueue
        : this.getDescriptorQueues(descriptorType, descriptorHistory)) {
      Descriptor descriptor;
      while ((descriptor = descriptorQueue.nextDescriptor()) != null) {
        for (DescriptorListener descriptorListener : descriptorListeners) {
          descriptorListener.processDescriptor(descriptor, relay);
        }
      }
    }
    logger.info("Read recent/{}.", descriptorType.getDir());
  }

  /** Reads archived descriptors from disk and feeds them into any
   * registered listeners. */
  public void readArchivedDescriptors() {
    if (!this.inArchiveDir.exists()) {
      return;
    }
    logger.info("Reading archived descriptors...");
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
        } else if (annotation.startsWith("@type bridge-pool-assignment 1.")) {
          descriptorType = DescriptorType.BRIDGE_POOL_ASSIGNMENTS;
          relay = false;
        }
      }
      if (descriptorType == null) {
        logger.warn("Unrecognized descriptor in {} with annotations {}. "
            + "Skipping descriptor.", this.inArchiveDir.getAbsolutePath(),
            descriptor.getAnnotations());
        continue;
      }
      for (DescriptorListener descriptorListener :
          this.descriptorListeners.get(descriptorType)) {
        descriptorListener.processDescriptor(descriptor, relay);
      }
    }
    this.archiveDescriptorQueue.writeHistoryFile();
    logger.info("Read archived descriptors");
  }

  /** Writes parse histories for recent descriptors to disk. */
  public void writeHistoryFiles() {
    logger.debug("Writing parse histories for recent descriptors...");
    for (DescriptorQueue descriptorQueue : this.recentDescriptorQueues) {
      descriptorQueue.writeHistoryFile();
    }
  }

  /** Returns a string with statistics on the number of processed
   * descriptors during the current execution. */
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    ").append(this.recentDescriptorQueues.size())
      .append(" descriptor ").append("queues created for recent descriptors\n");
    int historySizeBefore = 0;
    int historySizeAfter = 0;
    long descriptors = 0L;
    long bytes = 0L;
    for (DescriptorQueue descriptorQueue : this.recentDescriptorQueues) {
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

