/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExtraInfoDescriptor;

/*
 * Example extra-info descriptor used as input:
 *
 * extra-info ndnop2 DE6397A047ABE5F78B4C87AF725047831B221AAB
 * dirreq-stats-end 2014-02-16 16:42:11 (86400 s)
 * dirreq-v3-resp ok=856,not-enough-sigs=0,unavailable=0,not-found=0,
 *   not-modified=40,busy=0
 * bridge-stats-end 2014-02-16 16:42:17 (86400 s)
 * bridge-ips ??=8,in=8,se=8
 * bridge-ip-versions v4=8,v6=0
 *
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
public class ClientsDataWriter implements DescriptorListener,
    StatusUpdater, FingerprintListener, DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public ClientsDataWriter(DescriptorSource descriptorSource,
      DocumentStore documentStore, Time time) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
    this.registerDescriptorListeners();
    this.registerFingerprintListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_EXTRA_INFOS);
  }

  private void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_EXTRA_INFOS);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof ExtraInfoDescriptor && !relay) {
      this.processBridgeExtraInfoDescriptor(
          (ExtraInfoDescriptor) descriptor);
    }
  }

  private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L,
      ONE_DAY_MILLIS = 24L * ONE_HOUR_MILLIS;

  private SortedMap<String, SortedSet<ClientsHistory>> newResponses =
      new TreeMap<String, SortedSet<ClientsHistory>>();

  private void processBridgeExtraInfoDescriptor(
      ExtraInfoDescriptor descriptor) {
    long dirreqStatsEndMillis = descriptor.getDirreqStatsEndMillis();
    long dirreqStatsIntervalLengthMillis =
        descriptor.getDirreqStatsIntervalLength() * 1000L;
    SortedMap<String, Integer> responses = descriptor.getDirreqV3Resp();
    if (dirreqStatsEndMillis < 0L ||
        dirreqStatsIntervalLengthMillis != ONE_DAY_MILLIS ||
        responses == null || !responses.containsKey("ok")) {
      return;
    }
    double okResponses = (double) (responses.get("ok") - 4);
    if (okResponses < 0.0) {
      return;
    }
    String hashedFingerprint = descriptor.getFingerprint().toUpperCase();
    long dirreqStatsStartMillis = dirreqStatsEndMillis
        - dirreqStatsIntervalLengthMillis;
    long utcBreakMillis = (dirreqStatsEndMillis / ONE_DAY_MILLIS)
        * ONE_DAY_MILLIS;
    for (int i = 0; i < 2; i++) {
      long startMillis = i == 0 ? dirreqStatsStartMillis : utcBreakMillis;
      long endMillis = i == 0 ? utcBreakMillis : dirreqStatsEndMillis;
      if (startMillis >= endMillis) {
        continue;
      }
      double totalResponses = okResponses
          * ((double) (endMillis - startMillis))
          / ((double) ONE_DAY_MILLIS);
      SortedMap<String, Double> responsesByCountry =
          this.weightResponsesWithUniqueIps(totalResponses,
          descriptor.getBridgeIps(), "??");
      SortedMap<String, Double> responsesByTransport =
          this.weightResponsesWithUniqueIps(totalResponses,
          descriptor.getBridgeIpTransports(), "<??>");
      SortedMap<String, Double> responsesByVersion =
          this.weightResponsesWithUniqueIps(totalResponses,
          descriptor.getBridgeIpVersions(), "");
      ClientsHistory newResponseHistory = new ClientsHistory(
          startMillis, endMillis, totalResponses, responsesByCountry,
          responsesByTransport, responsesByVersion); 
      if (!this.newResponses.containsKey(hashedFingerprint)) {
        this.newResponses.put(hashedFingerprint,
            new TreeSet<ClientsHistory>());
      }
      this.newResponses.get(hashedFingerprint).add(
          newResponseHistory);
    }
  }

  private SortedMap<String, Double> weightResponsesWithUniqueIps(
      double totalResponses, SortedMap<String, Integer> uniqueIps,
      String omitString) {
    SortedMap<String, Double> weightedResponses =
        new TreeMap<String, Double>();
    int totalUniqueIps = 0;
    if (uniqueIps != null) {
      for (Map.Entry<String, Integer> e : uniqueIps.entrySet()) {
        if (e.getValue() > 4) {
          totalUniqueIps += e.getValue() - 4;
        }
      }
    }
    if (totalUniqueIps > 0) {
      for (Map.Entry<String, Integer> e : uniqueIps.entrySet()) {
        if (!e.getKey().equals(omitString) && e.getValue() > 4) {
          weightedResponses.put(e.getKey(),
              (((double) (e.getValue() - 4)) * totalResponses)
              / ((double) totalUniqueIps));
        }
      }
    }
    return weightedResponses;
  }

  public void updateStatuses() {
    for (Map.Entry<String, SortedSet<ClientsHistory>> e :
        this.newResponses.entrySet()) {
      String hashedFingerprint = e.getKey();
      ClientsStatus clientsStatus = this.documentStore.retrieve(
          ClientsStatus.class, true, hashedFingerprint);
      if (clientsStatus == null) {
        clientsStatus = new ClientsStatus();
      }
      this.addToHistory(clientsStatus, e.getValue());
      this.compressHistory(clientsStatus);
      this.documentStore.store(clientsStatus, hashedFingerprint);
    }
    Logger.printStatusTime("Updated clients status files");
  }

  private void addToHistory(ClientsStatus clientsStatus,
      SortedSet<ClientsHistory> newIntervals) {
    SortedSet<ClientsHistory> history = clientsStatus.history;
    for (ClientsHistory interval : newIntervals) {
      if ((history.headSet(interval).isEmpty() ||
          history.headSet(interval).last().endMillis <=
          interval.startMillis) &&
          (history.tailSet(interval).isEmpty() ||
          history.tailSet(interval).first().startMillis >=
          interval.endMillis)) {
        history.add(interval);
      }
    }
  }

  private void compressHistory(ClientsStatus clientsStatus) {
    SortedSet<ClientsHistory> history = clientsStatus.history;
    SortedSet<ClientsHistory> compressedHistory =
        new TreeSet<ClientsHistory>();
    ClientsHistory lastResponses = null;
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String lastMonthString = "1970-01";
    for (ClientsHistory responses : history) {
      long intervalLengthMillis;
      if (this.now - responses.endMillis <=
          92L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 24L * 60L * 60L * 1000L;
      } else if (this.now - responses.endMillis <=
          366L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 2L * 24L * 60L * 60L * 1000L;
      } else {
        intervalLengthMillis = 10L * 24L * 60L * 60L * 1000L;
      }
      String monthString = dateTimeFormat.format(responses.startMillis);
      if (lastResponses != null &&
          lastResponses.endMillis == responses.startMillis &&
          ((lastResponses.endMillis - 1L) / intervalLengthMillis) ==
          ((responses.endMillis - 1L) / intervalLengthMillis) &&
          lastMonthString.equals(monthString)) {
        lastResponses.addResponses(responses);
      } else {
        if (lastResponses != null) {
          compressedHistory.add(lastResponses);
        }
        lastResponses = responses;
      }
      lastMonthString = monthString;
    }
    if (lastResponses != null) {
      compressedHistory.add(lastResponses);
    }
    clientsStatus.history = compressedHistory;
  }

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    if (!relay) {
      this.updateDocuments.addAll(fingerprints);
    }
  }

  private SortedSet<String> updateDocuments = new TreeSet<String>();

  public void writeDocuments() {
    for (String hashedFingerprint : this.updateDocuments) {
      ClientsStatus clientsStatus = this.documentStore.retrieve(
          ClientsStatus.class, true, hashedFingerprint);
      if (clientsStatus == null) {
        continue;
      }
      SortedSet<ClientsHistory> history = clientsStatus.history;
      ClientsDocument clientsDocument = new ClientsDocument();
      clientsDocument.documentString = this.formatHistoryString(
          hashedFingerprint, history);
      this.documentStore.store(clientsDocument, hashedFingerprint);
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
      7L * 24L * 60L * 60L * 1000L,
      31L * 24L * 60L * 60L * 1000L,
      92L * 24L * 60L * 60L * 1000L,
      366L * 24L * 60L * 60L * 1000L,
      5L * 366L * 24L * 60L * 60L * 1000L };

  private long[] dataPointIntervals = new long[] {
      24L * 60L * 60L * 1000L,
      24L * 60L * 60L * 1000L,
      24L * 60L * 60L * 1000L,
      2L * 24L * 60L * 60L * 1000L,
      10L * 24L * 60L * 60L * 1000L };

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
      if (hist.endMillis < intervalStartMillis) {
        continue;
      }
      while ((intervalStartMillis / dataPointInterval) !=
          (hist.endMillis / dataPointInterval)) {
        dataPoints.add(millis * 2L < dataPointInterval
            ? -1.0 : responses * ((double) ONE_DAY_MILLIS)
            / (((double) millis) * 10.0));
        responses = 0.0;
        millis = 0L;
        intervalStartMillis += dataPointInterval;
      }
      responses += hist.totalResponses;
      totalResponses += hist.totalResponses;
      for (Map.Entry<String, Double> e :
          hist.responsesByCountry.entrySet()) {
        if (!totalResponsesByCountry.containsKey(e.getKey())) {
          totalResponsesByCountry.put(e.getKey(), 0.0);
        }
        totalResponsesByCountry.put(e.getKey(), e.getValue()
            + totalResponsesByCountry.get(e.getKey()));
      }
      for (Map.Entry<String, Double> e :
          hist.responsesByTransport.entrySet()) {
        if (!totalResponsesByTransport.containsKey(e.getKey())) {
          totalResponsesByTransport.put(e.getKey(), 0.0);
        }
        totalResponsesByTransport.put(e.getKey(), e.getValue()
            + totalResponsesByTransport.get(e.getKey()));
      }
      for (Map.Entry<String, Double> e :
          hist.responsesByVersion.entrySet()) {
        if (!totalResponsesByVersion.containsKey(e.getKey())) {
          totalResponsesByVersion.put(e.getKey(), 0.0);
        }
        totalResponsesByVersion.put(e.getKey(), e.getValue()
            + totalResponsesByVersion.get(e.getKey()));
      }
      millis += (hist.endMillis - hist.startMillis);
    }
    dataPoints.add(millis * 2L < dataPointInterval
        ? -1.0 : responses * ((double) ONE_DAY_MILLIS)
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
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    sb.append("\"" + graphName + "\":{"
        + "\"first\":\"" + dateTimeFormat.format(firstDataPointMillis)
        + "\",\"last\":\"" + dateTimeFormat.format(lastDataPointMillis)
        + "\",\"interval\":" + String.valueOf(dataPointInterval / 1000L)
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
    int newIntervals = 0;
    for (SortedSet<ClientsHistory> hist : this.newResponses.values()) {
      newIntervals += hist.size();
    }
    StringBuilder sb = new StringBuilder();
    sb.append("    "
        + Logger.formatDecimalNumber(newIntervals / 2)
        + " client statistics processed from extra-info descriptors\n");
    sb.append("    "
        + Logger.formatDecimalNumber(this.newResponses.size())
        + " client status files updated\n");
    sb.append("    "
        + Logger.formatDecimalNumber(this.updateDocuments.size())
        + " client document files updated\n");
    return sb.toString();
  }
}

