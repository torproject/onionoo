/* Copyright 2011--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public class BandwidthDocumentWriter implements FingerprintListener,
    DocumentWriter{

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public BandwidthDocumentWriter(DescriptorSource descriptorSource,
      DocumentStore documentStore, Time time) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
    this.registerFingerprintListeners();
  }

  private void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_EXTRA_INFOS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_EXTRA_INFOS);
  }

  private Set<String> updateBandwidthDocuments = new HashSet<String>();

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    this.updateBandwidthDocuments.addAll(fingerprints);
  }

  public void writeDocuments() {
    for (String fingerprint : this.updateBandwidthDocuments) {
      BandwidthStatus bandwidthStatus = this.documentStore.retrieve(
          BandwidthStatus.class, true, fingerprint);
      if (bandwidthStatus == null) {
        continue;
      }
      this.writeBandwidthDataFileToDisk(fingerprint,
          bandwidthStatus.writeHistory, bandwidthStatus.readHistory);
    }
    Logger.printStatusTime("Wrote bandwidth document files");
  }

  private void writeBandwidthDataFileToDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    String writeHistoryString = formatHistoryString(writeHistory);
    String readHistoryString = formatHistoryString(readHistory);
    StringBuilder sb = new StringBuilder();
    sb.append("{\"fingerprint\":\"" + fingerprint + "\",\n"
        + "\"write_history\":{\n" + writeHistoryString + "},\n"
        + "\"read_history\":{\n" + readHistoryString + "}}\n");
    BandwidthDocument bandwidthDocument = new BandwidthDocument();
    bandwidthDocument.documentString = sb.toString();
    this.documentStore.store(bandwidthDocument, fingerprint);
  }

  private String[] graphNames = new String[] {
      "3_days",
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private long[] graphIntervals = new long[] {
      72L * 60L * 60L * 1000L,
      7L * 24L * 60L * 60L * 1000L,
      31L * 24L * 60L * 60L * 1000L,
      92L * 24L * 60L * 60L * 1000L,
      366L * 24L * 60L * 60L * 1000L,
      5L * 366L * 24L * 60L * 60L * 1000L };

  private long[] dataPointIntervals = new long[] {
      15L * 60L * 1000L,
      60L * 60L * 1000L,
      4L * 60L * 60L * 1000L,
      12L * 60L * 60L * 1000L,
      2L * 24L * 60L * 60L * 1000L,
      10L * 24L * 60L * 60L * 1000L };

  private String formatHistoryString(SortedMap<Long, long[]> history) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < this.graphIntervals.length; i++) {
      String graphName = this.graphNames[i];
      long graphInterval = this.graphIntervals[i];
      long dataPointInterval = this.dataPointIntervals[i];
      List<Long> dataPoints = new ArrayList<Long>();
      long intervalStartMillis = ((this.now - graphInterval)
          / dataPointInterval) * dataPointInterval;
      long totalMillis = 0L, totalBandwidth = 0L;
      for (long[] v : history.values()) {
        long startMillis = v[0], endMillis = v[1], bandwidth = v[2];
        if (endMillis < intervalStartMillis) {
          continue;
        }
        while ((intervalStartMillis / dataPointInterval) !=
            (endMillis / dataPointInterval)) {
          dataPoints.add(totalMillis * 5L < dataPointInterval
              ? -1L : (totalBandwidth * 1000L) / totalMillis);
          totalBandwidth = 0L;
          totalMillis = 0L;
          intervalStartMillis += dataPointInterval;
        }
        totalBandwidth += bandwidth;
        totalMillis += (endMillis - startMillis);
      }
      dataPoints.add(totalMillis * 5L < dataPointInterval
          ? -1L : (totalBandwidth * 1000L) / totalMillis);
      long maxValue = 1L;
      int firstNonNullIndex = -1, lastNonNullIndex = -1;
      for (int j = 0; j < dataPoints.size(); j++) {
        long dataPoint = dataPoints.get(j);
        if (dataPoint >= 0L) {
          if (firstNonNullIndex < 0) {
            firstNonNullIndex = j;
          }
          lastNonNullIndex = j;
          if (dataPoint > maxValue) {
            maxValue = dataPoint;
          }
        }
      }
      if (firstNonNullIndex < 0) {
        continue;
      }
      long firstDataPointMillis = (((this.now - graphInterval)
          / dataPointInterval) + firstNonNullIndex) * dataPointInterval
          + dataPointInterval / 2L;
      if (i > 0 &&
          firstDataPointMillis >= this.now - graphIntervals[i - 1]) {
        /* Skip bandwidth history object, because it doesn't contain
         * anything new that wasn't already contained in the last
         * bandwidth history object(s). */
        continue;
      }
      long lastDataPointMillis = firstDataPointMillis
          + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
      double factor = ((double) maxValue) / 999.0;
      int count = lastNonNullIndex - firstNonNullIndex + 1;
      StringBuilder sb2 = new StringBuilder();
      sb2.append("\"" + graphName + "\":{"
          + "\"first\":\""
          + DateTimeHelper.format(firstDataPointMillis) + "\","
          + "\"last\":\""
          + DateTimeHelper.format(lastDataPointMillis) + "\","
          +"\"interval\":" + String.valueOf(dataPointInterval / 1000L)
          + ",\"factor\":" + String.format(Locale.US, "%.3f", factor)
          + ",\"count\":" + String.valueOf(count) + ",\"values\":[");
      int written = 0, previousNonNullIndex = -2;
      boolean foundTwoAdjacentDataPoints = false;
      for (int j = firstNonNullIndex; j <= lastNonNullIndex; j++) {
        long dataPoint = dataPoints.get(j);
        if (dataPoint >= 0L) {
          if (j - previousNonNullIndex == 1) {
            foundTwoAdjacentDataPoints = true;
          }
          previousNonNullIndex = j;
        }
        sb2.append((written++ > 0 ? "," : "") + (dataPoint < 0L ? "null" :
            String.valueOf((dataPoint * 999L) / maxValue)));
      }
      sb2.append("]},\n");
      if (foundTwoAdjacentDataPoints) {
        sb.append(sb2.toString());
      }
    }
    String result = sb.toString();
    if (result.length() >= 2) {
      result = result.substring(0, result.length() - 2) + "\n";
    }
    return result;
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}
