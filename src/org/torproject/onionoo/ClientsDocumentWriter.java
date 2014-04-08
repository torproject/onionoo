/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/*
 * Clients status file produced as intermediate output:
 *
 * 2014-02-15 16:42:11 2014-02-16 00:00:00
 *   259.042 in=86.347,se=86.347  v4=259.042
 * 2014-02-16 00:00:00 2014-02-16 16:42:11
 *   592.958 in=197.653,se=197.653  v4=592.958
 *
 * Clients document file produced as output:
 *
 * "1_month":{
 *   "first":"2014-02-03 12:00:00",
 *   "last":"2014-02-28 12:00:00",
 *   "interval":86400,
 *   "factor":0.139049349,
 *   "count":26,
 *   "values":[371,354,349,374,432,null,485,458,493,536,null,null,524,576,
 *             607,622,null,635,null,566,774,999,945,690,656,681],
 *   "countries":{"cn":0.0192,"in":0.1768,"ir":0.2487,"ru":0.0104,
 *                "se":0.1698,"sy":0.0325,"us":0.0406},
 *   "transports":{"obfs2":0.4581},
 *   "versions":{"v4":1.0000}}
 */
public class ClientsDocumentWriter implements FingerprintListener,
    DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public ClientsDocumentWriter(DescriptorSource descriptorSource,
      DocumentStore documentStore, Time time) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
    this.registerFingerprintListeners();
  }

  private void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_EXTRA_INFOS);
  }

  private SortedSet<String> updateDocuments = new TreeSet<String>();

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    if (!relay) {
      this.updateDocuments.addAll(fingerprints);
    }
  }

  private int writtenDocuments = 0;

  public void writeDocuments() {
    for (String hashedFingerprint : this.updateDocuments) {
      ClientsStatus clientsStatus = this.documentStore.retrieve(
          ClientsStatus.class, true, hashedFingerprint);
      if (clientsStatus == null) {
        continue;
      }
      SortedSet<ClientsHistory> history = clientsStatus.getHistory();
      ClientsDocument clientsDocument = new ClientsDocument();
      clientsDocument.setDocumentString(this.formatHistoryString(
          hashedFingerprint, history));
      this.documentStore.store(clientsDocument, hashedFingerprint);
      this.writtenDocuments++;
    }
    Logger.printStatusTime("Wrote clients document files");
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
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private String formatHistoryString(String hashedFingerprint,
      SortedSet<ClientsHistory> history) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"fingerprint\":\"" + hashedFingerprint + "\"");
    sb.append(",\n\"average_clients\":{");
    int graphIntervalsWritten = 0;
    for (int graphIntervalIndex = 0; graphIntervalIndex <
        this.graphIntervals.length; graphIntervalIndex++) {
      String timeline = this.formatTimeline(graphIntervalIndex, history);
      if (timeline != null) {
        sb.append((graphIntervalsWritten++ > 0 ? "," : "") + "\n"
            + timeline);
      }
    }
    sb.append("}");
    sb.append("\n}\n");
    return sb.toString();
  }

  private String formatTimeline(int graphIntervalIndex,
      SortedSet<ClientsHistory> history) {
    String graphName = this.graphNames[graphIntervalIndex];
    long graphInterval = this.graphIntervals[graphIntervalIndex];
    long dataPointInterval =
        this.dataPointIntervals[graphIntervalIndex];
    List<Double> dataPoints = new ArrayList<Double>();
    long intervalStartMillis = ((this.now - graphInterval)
        / dataPointInterval) * dataPointInterval;
    long millis = 0L;
    double responses = 0.0, totalResponses = 0.0;
    SortedMap<String, Double>
        totalResponsesByCountry = new TreeMap<String, Double>(),
        totalResponsesByTransport = new TreeMap<String, Double>(),
        totalResponsesByVersion = new TreeMap<String, Double>();
    for (ClientsHistory hist : history) {
      if (hist.getEndMillis() < intervalStartMillis) {
        continue;
      }
      while ((intervalStartMillis / dataPointInterval) !=
          (hist.getEndMillis() / dataPointInterval)) {
        dataPoints.add(millis * 2L < dataPointInterval
            ? -1.0 : responses * ((double) DateTimeHelper.ONE_DAY)
            / (((double) millis) * 10.0));
        responses = 0.0;
        millis = 0L;
        intervalStartMillis += dataPointInterval;
      }
      responses += hist.getTotalResponses();
      totalResponses += hist.getTotalResponses();
      for (Map.Entry<String, Double> e :
          hist.getResponsesByCountry().entrySet()) {
        if (!totalResponsesByCountry.containsKey(e.getKey())) {
          totalResponsesByCountry.put(e.getKey(), 0.0);
        }
        totalResponsesByCountry.put(e.getKey(), e.getValue()
            + totalResponsesByCountry.get(e.getKey()));
      }
      for (Map.Entry<String, Double> e :
          hist.getResponsesByTransport().entrySet()) {
        if (!totalResponsesByTransport.containsKey(e.getKey())) {
          totalResponsesByTransport.put(e.getKey(), 0.0);
        }
        totalResponsesByTransport.put(e.getKey(), e.getValue()
            + totalResponsesByTransport.get(e.getKey()));
      }
      for (Map.Entry<String, Double> e :
          hist.getResponsesByVersion().entrySet()) {
        if (!totalResponsesByVersion.containsKey(e.getKey())) {
          totalResponsesByVersion.put(e.getKey(), 0.0);
        }
        totalResponsesByVersion.put(e.getKey(), e.getValue()
            + totalResponsesByVersion.get(e.getKey()));
      }
      millis += (hist.getEndMillis() - hist.getStartMillis());
    }
    dataPoints.add(millis * 2L < dataPointInterval
        ? -1.0 : responses * ((double) DateTimeHelper.ONE_DAY)
        / (((double) millis) * 10.0));
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
      /* Skip clients history object, because it doesn't contain
       * anything new that wasn't already contained in the last
       * clients history object(s). */
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
    sb.append("]");
    if (!totalResponsesByCountry.isEmpty()) {
      sb.append(",\"countries\":{");
      int written = 0;
      for (Map.Entry<String, Double> e :
          totalResponsesByCountry.entrySet()) {
        if (e.getValue() > totalResponses / 100.0) {
          sb.append((written++ > 0 ? "," : "") + "\"" + e.getKey()
              + "\":" + String.format(Locale.US, "%.4f",
              e.getValue() / totalResponses));
        }
      }
      sb.append("}");
    }
    if (!totalResponsesByTransport.isEmpty()) {
      sb.append(",\"transports\":{");
      int written = 0;
      for (Map.Entry<String, Double> e :
          totalResponsesByTransport.entrySet()) {
        if (e.getValue() > totalResponses / 100.0) {
          sb.append((written++ > 0 ? "," : "") + "\"" + e.getKey()
              + "\":" + String.format(Locale.US, "%.4f",
              e.getValue() / totalResponses));
        }
      }
      sb.append("}");
    }
    if (!totalResponsesByVersion.isEmpty()) {
      sb.append(",\"versions\":{");
      int written = 0;
      for (Map.Entry<String, Double> e :
          totalResponsesByVersion.entrySet()) {
        if (e.getValue() > totalResponses / 100.0) {
          sb.append((written++ > 0 ? "," : "") + "\"" + e.getKey()
              + "\":" + String.format(Locale.US, "%.4f",
              e.getValue() / totalResponses));
        }
      }
      sb.append("}");
    }
    sb.append("}");
    if (foundTwoAdjacentDataPoints) {
      return sb.toString();
    } else {
      return null;
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + Logger.formatDecimalNumber(this.writtenDocuments)
        + " clients document files updated\n");
    return sb.toString();
  }
}
