/* Copyright 2012--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.writer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.GraphHistory;
import org.torproject.onionoo.docs.WeightsDocument;
import org.torproject.onionoo.docs.WeightsStatus;
import org.torproject.onionoo.updater.DescriptorSource;
import org.torproject.onionoo.updater.DescriptorSourceFactory;
import org.torproject.onionoo.updater.DescriptorType;
import org.torproject.onionoo.updater.FingerprintListener;
import org.torproject.onionoo.util.Logger;
import org.torproject.onionoo.util.TimeFactory;

public class WeightsDocumentWriter implements FingerprintListener,
    DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public WeightsDocumentWriter() {
    this.descriptorSource = DescriptorSourceFactory.getDescriptorSource();
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.now = TimeFactory.getTime().currentTimeMillis();
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
      WeightsDocument weightsDocument = this.compileWeightsDocument(
          fingerprint, history);
      this.documentStore.store(weightsDocument, fingerprint);
    }
    Logger.printStatusTime("Wrote weights document files");
  }

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

  private WeightsDocument compileWeightsDocument(String fingerprint,
      SortedMap<long[], double[]> history) {
    WeightsDocument weightsDocument = new WeightsDocument();
    weightsDocument.setFingerprint(fingerprint);
    weightsDocument.setAdvertisedBandwidthFraction(
        this.compileGraphType(history, 0));
    weightsDocument.setConsensusWeightFraction(
        this.compileGraphType(history, 1));
    weightsDocument.setGuardProbability(
        this.compileGraphType(history, 2));
    weightsDocument.setMiddleProbability(
        this.compileGraphType(history, 3));
    weightsDocument.setExitProbability(
        this.compileGraphType(history, 4));
    weightsDocument.setAdvertisedBandwidth(
        this.compileGraphType(history, 5));
    weightsDocument.setConsensusWeight(
        this.compileGraphType(history, 6));
    return weightsDocument;
  }

  private Map<String, GraphHistory> compileGraphType(
      SortedMap<long[], double[]> history, int graphTypeIndex) {
    Map<String, GraphHistory> graphs =
        new LinkedHashMap<String, GraphHistory>();
    for (int graphIntervalIndex = 0; graphIntervalIndex <
        this.graphIntervals.length; graphIntervalIndex++) {
      String graphName = this.graphNames[graphIntervalIndex];
      GraphHistory graphHistory = this.compileWeightsHistory(
          graphTypeIndex, graphIntervalIndex, history);
      if (graphHistory != null) {
        graphs.put(graphName, graphHistory);
      }
    }
    return graphs;
  }

  private GraphHistory compileWeightsHistory(int graphTypeIndex,
      int graphIntervalIndex, SortedMap<long[], double[]> history) {
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
      if (weight >= 0.0) {
        totalWeightTimesMillis += weight
            * ((double) (endMillis - startMillis));
        totalMillis += (endMillis - startMillis);
      }
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
    GraphHistory graphHistory = new GraphHistory();
    graphHistory.setFirst(firstDataPointMillis);
    graphHistory.setLast(lastDataPointMillis);
    graphHistory.setInterval((int) (dataPointInterval
        / DateTimeHelper.ONE_SECOND));
    graphHistory.setFactor(factor);
    graphHistory.setCount(count);
    int previousNonNullIndex = -2;
    boolean foundTwoAdjacentDataPoints = false;
    List<Integer> values = new ArrayList<Integer>();
    for (int dataPointIndex = firstNonNullIndex; dataPointIndex <=
        lastNonNullIndex; dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (dataPointIndex - previousNonNullIndex == 1) {
          foundTwoAdjacentDataPoints = true;
        }
        previousNonNullIndex = dataPointIndex;
      }
      values.add(dataPoint < 0.0 ? null :
          (int) ((dataPoint * 999.0) / maxValue));
    }
    graphHistory.setValues(values);
    if (foundTwoAdjacentDataPoints) {
      return graphHistory;
    } else {
      return null;
    }
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}
