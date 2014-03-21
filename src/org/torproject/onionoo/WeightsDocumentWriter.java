/* Copyright 2012--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public class WeightsDocumentWriter implements FingerprintListener,
    DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public WeightsDocumentWriter() {
    this.descriptorSource = ApplicationFactory.getDescriptorSource();
    this.documentStore = ApplicationFactory.getDocumentStore();
    this.now = ApplicationFactory.getTime().currentTimeMillis();
    this.registerFingerprintListeners();
  }

  private void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_SERVER_DESCRIPTORS);
  }

  private Set<String> updateWeightsDocuments = new HashSet<String>();

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    if (relay) {
      this.updateWeightsDocuments.addAll(fingerprints);
    }
  }

  public void writeDocuments() {
    this.writeWeightsDataFiles();
    Logger.printStatusTime("Wrote weights document files");
  }

  private void writeWeightsDataFiles() {
    for (String fingerprint : this.updateWeightsDocuments) {
      WeightsStatus weightsStatus = this.documentStore.retrieve(
          WeightsStatus.class, true, fingerprint);
      if (weightsStatus == null) {
        continue;
      }
      SortedMap<long[], double[]> history = weightsStatus.getHistory();
      WeightsDocument weightsDocument = new WeightsDocument();
      weightsDocument.setDocumentString(this.formatHistoryString(
          fingerprint, history));
      this.documentStore.store(weightsDocument, fingerprint);
    }
  }

  private String[] graphTypes = new String[] {
      "advertised_bandwidth_fraction",
      "consensus_weight_fraction",
      "guard_probability",
      "middle_probability",
      "exit_probability"
  };

  private String[] graphNames = new String[] {
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private long[] graphIntervals = new long[] {
      DateTimeHelper.ONE_WEEK,
      DateTimeHelper.ROUGHLY_ONE_MONTH,
      DateTimeHelper.ROUGHLY_THREE_MONTHS,
      DateTimeHelper.ROUGHLY_ONE_YEAR,
      DateTimeHelper.ROUGHLY_FIVE_YEARS };

  private long[] dataPointIntervals = new long[] {
      DateTimeHelper.ONE_HOUR,
      DateTimeHelper.FOUR_HOURS,
      DateTimeHelper.TWELVE_HOURS,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private String formatHistoryString(String fingerprint,
      SortedMap<long[], double[]> history) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"fingerprint\":\"" + fingerprint + "\"");
    for (int graphTypeIndex = 0; graphTypeIndex < this.graphTypes.length;
        graphTypeIndex++) {
      String graphType = this.graphTypes[graphTypeIndex];
      sb.append(",\n\"" + graphType + "\":{");
      int graphIntervalsWritten = 0;
      for (int graphIntervalIndex = 0; graphIntervalIndex <
          this.graphIntervals.length; graphIntervalIndex++) {
        String timeline = this.formatTimeline(graphTypeIndex,
            graphIntervalIndex, history);
        if (timeline != null) {
          sb.append((graphIntervalsWritten++ > 0 ? "," : "") + "\n"
            + timeline);
        }
      }
      sb.append("}");
    }
    sb.append("\n}\n");
    return sb.toString();
  }

  private String formatTimeline(int graphTypeIndex,
      int graphIntervalIndex, SortedMap<long[], double[]> history) {
    String graphName = this.graphNames[graphIntervalIndex];
    long graphInterval = this.graphIntervals[graphIntervalIndex];
    long dataPointInterval =
        this.dataPointIntervals[graphIntervalIndex];
    List<Double> dataPoints = new ArrayList<Double>();
    long intervalStartMillis = ((this.now - graphInterval)
        / dataPointInterval) * dataPointInterval;
    long totalMillis = 0L;
    double totalWeightTimesMillis = 0.0;
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long startMillis = e.getKey()[0], endMillis = e.getKey()[1];
      double weight = e.getValue()[graphTypeIndex];
      if (endMillis < intervalStartMillis) {
        continue;
      }
      while ((intervalStartMillis / dataPointInterval) !=
          (endMillis / dataPointInterval)) {
        dataPoints.add(totalMillis * 5L < dataPointInterval
            ? -1.0 : totalWeightTimesMillis / (double) totalMillis);
        totalWeightTimesMillis = 0.0;
        totalMillis = 0L;
        intervalStartMillis += dataPointInterval;
      }
      totalWeightTimesMillis += weight
          * ((double) (endMillis - startMillis));
      totalMillis += (endMillis - startMillis);
    }
    dataPoints.add(totalMillis * 5L < dataPointInterval
        ? -1.0 : totalWeightTimesMillis / (double) totalMillis);
    double maxValue = 0.0;
    int firstNonNullIndex = -1, lastNonNullIndex = -1;
    for (int dataPointIndex = 0; dataPointIndex < dataPoints.size();
        dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (firstNonNullIndex < 0) {
          firstNonNullIndex = dataPointIndex;
        }
        lastNonNullIndex = dataPointIndex;
        if (dataPoint > maxValue) {
          maxValue = dataPoint;
        }
      }
    }
    if (firstNonNullIndex < 0) {
      return null;
    }
    long firstDataPointMillis = (((this.now - graphInterval)
        / dataPointInterval) + firstNonNullIndex) * dataPointInterval
        + dataPointInterval / 2L;
    if (graphIntervalIndex > 0 && firstDataPointMillis >=
        this.now - graphIntervals[graphIntervalIndex - 1]) {
      /* Skip weights history object, because it doesn't contain
       * anything new that wasn't already contained in the last
       * weights history object(s). */
      return null;
    }
    long lastDataPointMillis = firstDataPointMillis
        + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
    double factor = ((double) maxValue) / 999.0;
    int count = lastNonNullIndex - firstNonNullIndex + 1;
    StringBuilder sb = new StringBuilder();
    sb.append("\"" + graphName + "\":{"
        + "\"first\":\"" + DateTimeHelper.format(firstDataPointMillis)
        + "\",\"last\":\"" + DateTimeHelper.format(lastDataPointMillis)
        + "\",\"interval\":" + String.valueOf(dataPointInterval
        / DateTimeHelper.ONE_SECOND)
        + ",\"factor\":" + String.format(Locale.US, "%.9f", factor)
        + ",\"count\":" + String.valueOf(count) + ",\"values\":[");
    int dataPointsWritten = 0, previousNonNullIndex = -2;
    boolean foundTwoAdjacentDataPoints = false;
    for (int dataPointIndex = firstNonNullIndex; dataPointIndex <=
        lastNonNullIndex; dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (dataPointIndex - previousNonNullIndex == 1) {
          foundTwoAdjacentDataPoints = true;
        }
        previousNonNullIndex = dataPointIndex;
      }
      sb.append((dataPointsWritten++ > 0 ? "," : "")
          + (dataPoint < 0.0 ? "null" :
          String.valueOf((long) ((dataPoint * 999.0) / maxValue))));
    }
    sb.append("]}");
    if (foundTwoAdjacentDataPoints) {
      return sb.toString();
    } else {
      return null;
    }
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}
