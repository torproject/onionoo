/* Copyright 2011--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.torproject.descriptor.BandwidthHistory;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.onionoo.docs.BandwidthStatus;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.util.DateTimeHelper;
import org.torproject.onionoo.util.TimeFactory;

public class BandwidthStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public BandwidthStatusUpdater() {
    this.descriptorSource = DescriptorSourceFactory.getDescriptorSource();
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.now = TimeFactory.getTime().currentTimeMillis();
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_EXTRA_INFOS);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_EXTRA_INFOS);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof ExtraInfoDescriptor) {
      this.parseDescriptor((ExtraInfoDescriptor) descriptor);
    }
  }

  public void updateStatuses() {
    /* Status files are already updated while processing descriptors. */
  }

  private void parseDescriptor(ExtraInfoDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    BandwidthStatus bandwidthStatus = this.documentStore.retrieve(
        BandwidthStatus.class, true, fingerprint);
    if (bandwidthStatus == null) {
      bandwidthStatus = new BandwidthStatus();
    }
    if (descriptor.getWriteHistory() != null) {
      this.parseHistory(descriptor.getWriteHistory(),
          bandwidthStatus.getWriteHistory());
    }
    if (descriptor.getReadHistory() != null) {
      this.parseHistory(descriptor.getReadHistory(),
          bandwidthStatus.getReadHistory());
    }
    this.compressHistory(bandwidthStatus.getWriteHistory());
    this.compressHistory(bandwidthStatus.getReadHistory());
    this.documentStore.store(bandwidthStatus, fingerprint);
  }

  private void parseHistory(BandwidthHistory bandwidthHistory,
      SortedMap<Long, long[]> history) {
    long intervalMillis = bandwidthHistory.getIntervalLength()
        * DateTimeHelper.ONE_SECOND;
    for (Map.Entry<Long, Long> e :
        bandwidthHistory.getBandwidthValues().entrySet()) {
      long endMillis = e.getKey(),
          startMillis = endMillis - intervalMillis;
      long bandwidthValue = e.getValue();
      /* TODO Should we first check whether an interval is already
       * contained in history? */
      history.put(startMillis, new long[] { startMillis, endMillis,
          bandwidthValue });
    }
  }

  private void compressHistory(SortedMap<Long, long[]> history) {
    SortedMap<Long, long[]> uncompressedHistory =
        new TreeMap<Long, long[]>(history);
    history.clear();
    long lastStartMillis = 0L, lastEndMillis = 0L, lastBandwidth = 0L;
    String lastMonthString = "1970-01";
    for (long[] v : uncompressedHistory.values()) {
      long startMillis = v[0], endMillis = v[1], bandwidth = v[2];
      long intervalLengthMillis;
      if (this.now - endMillis <= DateTimeHelper.THREE_DAYS) {
        intervalLengthMillis = DateTimeHelper.FIFTEEN_MINUTES;
      } else if (this.now - endMillis <= DateTimeHelper.ONE_WEEK) {
        intervalLengthMillis = DateTimeHelper.ONE_HOUR;
      } else if (this.now - endMillis <=
          DateTimeHelper.ROUGHLY_ONE_MONTH) {
        intervalLengthMillis = DateTimeHelper.FOUR_HOURS;
      } else if (this.now - endMillis <=
          DateTimeHelper.ROUGHLY_THREE_MONTHS) {
        intervalLengthMillis = DateTimeHelper.TWELVE_HOURS;
      } else if (this.now - endMillis <=
          DateTimeHelper.ROUGHLY_ONE_YEAR) {
        intervalLengthMillis = DateTimeHelper.TWO_DAYS;
      } else {
        intervalLengthMillis = DateTimeHelper.TEN_DAYS;
      }
      String monthString = DateTimeHelper.format(startMillis,
          DateTimeHelper.ISO_YEARMONTH_FORMAT);
      if (lastEndMillis == startMillis &&
          ((lastEndMillis - 1L) / intervalLengthMillis) ==
          ((endMillis - 1L) / intervalLengthMillis) &&
          lastMonthString.equals(monthString)) {
        lastEndMillis = endMillis;
        lastBandwidth += bandwidth;
      } else {
        if (lastStartMillis > 0L) {
          history.put(lastStartMillis, new long[] { lastStartMillis,
              lastEndMillis, lastBandwidth });
        }
        lastStartMillis = startMillis;
        lastEndMillis = endMillis;
        lastBandwidth = bandwidth;
      }
      lastMonthString = monthString;
    }
    if (lastStartMillis > 0L) {
      history.put(lastStartMillis, new long[] { lastStartMillis,
          lastEndMillis, lastBandwidth });
    }
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}

