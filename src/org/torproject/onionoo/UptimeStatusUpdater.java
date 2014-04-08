/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

public class UptimeStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  public UptimeStatusUpdater(DescriptorSource descriptorSource,
      DocumentStore documentStore) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_STATUSES);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof RelayNetworkStatusConsensus) {
      this.processRelayNetworkStatusConsensus(
          (RelayNetworkStatusConsensus) descriptor);
    } else if (descriptor instanceof BridgeNetworkStatus) {
      this.processBridgeNetworkStatus(
          (BridgeNetworkStatus) descriptor);
    }
  }

  private SortedSet<Long> newRelayStatuses = new TreeSet<Long>(),
      newBridgeStatuses = new TreeSet<Long>();
  private SortedMap<String, SortedSet<Long>>
      newRunningRelays = new TreeMap<String, SortedSet<Long>>(),
      newRunningBridges = new TreeMap<String, SortedSet<Long>>();

  private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;

  private void processRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    SortedSet<String> fingerprints = new TreeSet<String>();
    for (NetworkStatusEntry entry :
        consensus.getStatusEntries().values()) {
      if (entry.getFlags().contains("Running")) {
        fingerprints.add(entry.getFingerprint());
      }
    }
    if (!fingerprints.isEmpty()) {
      long dateHourMillis = (consensus.getValidAfterMillis()
          / ONE_HOUR_MILLIS) * ONE_HOUR_MILLIS;
      for (String fingerprint : fingerprints) {
        if (!this.newRunningRelays.containsKey(fingerprint)) {
          this.newRunningRelays.put(fingerprint, new TreeSet<Long>());
        }
        this.newRunningRelays.get(fingerprint).add(dateHourMillis);
      }
      this.newRelayStatuses.add(dateHourMillis);
    }
  }

  private void processBridgeNetworkStatus(BridgeNetworkStatus status) {
    SortedSet<String> fingerprints = new TreeSet<String>();
    for (NetworkStatusEntry entry :
        status.getStatusEntries().values()) {
      if (entry.getFlags().contains("Running")) {
        fingerprints.add(entry.getFingerprint());
      }
    }
    if (!fingerprints.isEmpty()) {
      long dateHourMillis = (status.getPublishedMillis()
          / ONE_HOUR_MILLIS) * ONE_HOUR_MILLIS;
      for (String fingerprint : fingerprints) {
        if (!this.newRunningBridges.containsKey(fingerprint)) {
          this.newRunningBridges.put(fingerprint, new TreeSet<Long>());
        }
        this.newRunningBridges.get(fingerprint).add(dateHourMillis);
      }
      this.newBridgeStatuses.add(dateHourMillis);
    }
  }

  public void updateStatuses() {
    for (Map.Entry<String, SortedSet<Long>> e :
        this.newRunningRelays.entrySet()) {
      this.updateStatus(true, e.getKey(), e.getValue());
    }
    this.updateStatus(true, null, this.newRelayStatuses);
    for (Map.Entry<String, SortedSet<Long>> e :
        this.newRunningBridges.entrySet()) {
      this.updateStatus(false, e.getKey(), e.getValue());
    }
    this.updateStatus(false, null, this.newBridgeStatuses);
    Logger.printStatusTime("Updated uptime status files");
  }

  private void updateStatus(boolean relay, String fingerprint,
      SortedSet<Long> newUptimeHours) {
    UptimeStatus uptimeStatus = this.readHistory(fingerprint);
    if (uptimeStatus == null) {
      uptimeStatus = new UptimeStatus();
    }
    this.addToHistory(uptimeStatus, relay, newUptimeHours);
    this.compressHistory(uptimeStatus);
    this.writeHistory(fingerprint, uptimeStatus);
  }

  private UptimeStatus readHistory(String fingerprint) {
    return fingerprint == null ?
        documentStore.retrieve(UptimeStatus.class, true) :
        documentStore.retrieve(UptimeStatus.class, true, fingerprint);
  }

  private void addToHistory(UptimeStatus uptimeStatus, boolean relay,
      SortedSet<Long> newIntervals) {
    SortedSet<UptimeHistory> history = uptimeStatus.history;
    for (long startMillis : newIntervals) {
      UptimeHistory interval = new UptimeHistory(relay, startMillis, 1);
      if (!history.headSet(interval).isEmpty()) {
        UptimeHistory prev = history.headSet(interval).last();
        if (prev.relay == interval.relay &&
            prev.startMillis + ONE_HOUR_MILLIS * prev.uptimeHours >
            interval.startMillis) {
          continue;
        }
      }
      if (!history.tailSet(interval).isEmpty()) {
        UptimeHistory next = history.tailSet(interval).first();
        if (next.relay == interval.relay &&
            next.startMillis < interval.startMillis + ONE_HOUR_MILLIS) {
          continue;
        }
      }
      history.add(interval);
    }
  }

  private void compressHistory(UptimeStatus uptimeStatus) {
    SortedSet<UptimeHistory> history = uptimeStatus.history;
    SortedSet<UptimeHistory> compressedHistory =
        new TreeSet<UptimeHistory>();
    UptimeHistory lastInterval = null;
    for (UptimeHistory interval : history) {
      if (lastInterval != null &&
          lastInterval.startMillis + ONE_HOUR_MILLIS
          * lastInterval.uptimeHours == interval.startMillis &&
          lastInterval.relay == interval.relay) {
        lastInterval.addUptime(interval);
      } else {
        if (lastInterval != null) {
          compressedHistory.add(lastInterval);
        }
        lastInterval = interval;
      }
    }
    if (lastInterval != null) {
      compressedHistory.add(lastInterval);
    }
    uptimeStatus.history = compressedHistory;
  }

  private void writeHistory(String fingerprint,
      UptimeStatus uptimeStatus) {
    if (fingerprint == null) {
      this.documentStore.store(uptimeStatus);
    } else {
      this.documentStore.store(uptimeStatus, fingerprint);
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + Logger.formatDecimalNumber(
            this.newRelayStatuses.size()) + " hours of relay uptimes "
            + "processed\n");
    sb.append("    " + Logger.formatDecimalNumber(
        this.newBridgeStatuses.size()) + " hours of bridge uptimes "
        + "processed\n");
    sb.append("    " + Logger.formatDecimalNumber(
        this.newRunningRelays.size() + this.newRunningBridges.size())
        + " uptime status files updated\n");
    return sb.toString();
  }
}

