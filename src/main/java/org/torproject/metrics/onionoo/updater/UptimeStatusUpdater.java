/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.metrics.onionoo.docs.DateTimeHelper;
import org.torproject.metrics.onionoo.docs.DocumentStore;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.docs.UptimeStatus;
import org.torproject.metrics.onionoo.util.FormattingUtils;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class UptimeStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  /** Initializes a new status updater, obtains references to all relevant
   * singleton instances, and registers as listener at the (singleton)
   * descriptor source. */
  public UptimeStatusUpdater() {
    this.descriptorSource = DescriptorSourceFactory.getDescriptorSource();
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_STATUSES);
  }

  @Override
  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof RelayNetworkStatusConsensus) {
      this.processRelayNetworkStatusConsensus(
          (RelayNetworkStatusConsensus) descriptor);
    } else if (descriptor instanceof BridgeNetworkStatus) {
      this.processBridgeNetworkStatus(
          (BridgeNetworkStatus) descriptor);
    }
  }

  private static class Flags {

    private static Map<String, Integer> flagIndexes = new HashMap<>();

    private static Map<Integer, String> flagStrings = new HashMap<>();

    private BitSet flags;

    private Flags(SortedSet<String> flags) {
      this.flags = new BitSet(flagIndexes.size());
      for (String flag : flags) {
        if (!flagIndexes.containsKey(flag)) {
          flagStrings.put(flagIndexes.size(), flag);
          flagIndexes.put(flag, flagIndexes.size());
        }
        this.flags.set(flagIndexes.get(flag));
      }
    }

    public SortedSet<String> getFlags() {
      SortedSet<String> result = new TreeSet<>();
      if (this.flags != null) {
        for (int i = this.flags.nextSetBit(0); i >= 0;
            i = this.flags.nextSetBit(i + 1)) {
          result.add(flagStrings.get(i));
        }
      }
      return result;
    }
  }

  private SortedMap<Long, Flags> newRelayStatuses = new TreeMap<>();

  private SortedMap<String, SortedMap<Long, Flags>>
      newRunningRelays = new TreeMap<>();

  private SortedSet<Long> newBridgeStatuses = new TreeSet<>();

  private SortedMap<String, SortedSet<Long>>
      newRunningBridges = new TreeMap<>();

  private void processRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    long dateHourMillis = (consensus.getValidAfterMillis()
        / DateTimeHelper.ONE_HOUR) * DateTimeHelper.ONE_HOUR;
    for (NetworkStatusEntry entry :
        consensus.getStatusEntries().values()) {
      String fingerprint = entry.getFingerprint();
      this.newRunningRelays.putIfAbsent(fingerprint, new TreeMap<>());
      this.newRunningRelays.get(fingerprint).put(dateHourMillis,
          new Flags(entry.getFlags()));
    }
    this.newRelayStatuses.put(dateHourMillis,
        new Flags(consensus.getKnownFlags()));
  }

  private void processBridgeNetworkStatus(BridgeNetworkStatus status) {
    SortedSet<String> fingerprints = new TreeSet<>();
    for (NetworkStatusEntry entry :
        status.getStatusEntries().values()) {
      fingerprints.add(entry.getFingerprint());
    }
    if (!fingerprints.isEmpty()) {
      long dateHourMillis = (status.getPublishedMillis()
          / DateTimeHelper.ONE_HOUR) * DateTimeHelper.ONE_HOUR;
      for (String fingerprint : fingerprints) {
        this.newRunningBridges.putIfAbsent(fingerprint, new TreeSet<>());
        this.newRunningBridges.get(fingerprint).add(dateHourMillis);
      }
      this.newBridgeStatuses.add(dateHourMillis);
    }
  }

  @Override
  public void updateStatuses() {
    for (Map.Entry<String, SortedMap<Long, Flags>> e :
        this.newRunningRelays.entrySet()) {
      this.updateStatus(true, e.getKey(), e.getValue());
    }
    this.updateStatus(true, null, this.newRelayStatuses);
    for (Map.Entry<String, SortedSet<Long>> e :
        this.newRunningBridges.entrySet()) {
      SortedMap<Long, Flags> dateHourMillisNoFlags = new TreeMap<>();
      for (long dateHourMillis : e.getValue()) {
        dateHourMillisNoFlags.put(dateHourMillis, null);
      }
      this.updateStatus(false, e.getKey(), dateHourMillisNoFlags);
    }
    SortedMap<Long, Flags> dateHourMillisNoFlags = new TreeMap<>();
    for (long dateHourMillis : this.newBridgeStatuses) {
      dateHourMillisNoFlags.put(dateHourMillis, null);
    }
    this.updateStatus(false, null, dateHourMillisNoFlags);
  }

  private void updateStatus(boolean relay, String fingerprint,
      SortedMap<Long, Flags> dateHourMillisFlags) {
    UptimeStatus uptimeStatus = (fingerprint == null)
        ? this.documentStore.retrieve(UptimeStatus.class, true)
        : this.documentStore.retrieve(UptimeStatus.class, true,
        fingerprint);
    if (uptimeStatus == null) {
      uptimeStatus = new UptimeStatus();
    }
    for (Map.Entry<Long, Flags> e : dateHourMillisFlags.entrySet()) {
      uptimeStatus.addToHistory(relay, e.getKey(),
          e.getValue() == null ? null : e.getValue().getFlags());
    }
    if (uptimeStatus.isDirty()) {
      uptimeStatus.compressHistory();
      if (fingerprint == null) {
        this.documentStore.store(uptimeStatus);
      } else {
        this.documentStore.store(uptimeStatus, fingerprint);
      }
      uptimeStatus.clearDirty();
    }
  }

  @Override
  public String getStatsString() {
    return String.format("    %s hours of relay uptimes processed\n"
        + "    %s hours of bridge uptimes processed\n"
        + "    %s uptime status files updated\n",
        FormattingUtils.formatDecimalNumber(this.newRelayStatuses.size()),
        FormattingUtils.formatDecimalNumber(this.newBridgeStatuses.size()),
        FormattingUtils.formatDecimalNumber(
        this.newRunningRelays.size() + this.newRunningBridges.size()));
  }
}

