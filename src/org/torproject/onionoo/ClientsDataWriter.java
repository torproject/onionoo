/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
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
public class ClientsDataWriter implements DataWriter, DescriptorListener {

  private static class ResponseHistory
      implements Comparable<ResponseHistory> {
    private long startMillis;
    private long endMillis;
    private double totalResponses;
    private SortedMap<String, Double> responsesByCountry;
    private SortedMap<String, Double> responsesByTransport;
    private SortedMap<String, Double> responsesByVersion;
    private ResponseHistory(long startMillis, long endMillis,
        double totalResponses,
        SortedMap<String, Double> responsesByCountry,
        SortedMap<String, Double> responsesByTransport,
        SortedMap<String, Double> responsesByVersion) {
      this.startMillis = startMillis;
      this.endMillis = endMillis;
      this.totalResponses = totalResponses;
      this.responsesByCountry = responsesByCountry;
      this.responsesByTransport = responsesByTransport;
      this.responsesByVersion = responsesByVersion;
    }
    public static ResponseHistory fromString(
        String responseHistoryString) {
      String[] parts = responseHistoryString.split(" ", 8);
      if (parts.length != 8) {
        return null;
      }
      long startMillis = -1L, endMillis = -1L;
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setLenient(false);
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      try {
        startMillis = dateTimeFormat.parse(parts[0] + " " + parts[1]).
            getTime();
        endMillis = dateTimeFormat.parse(parts[2] + " " + parts[3]).
            getTime();
      } catch (ParseException e) {
        return null;
      }
      if (startMillis >= endMillis) {
        return null;
      }
      double totalResponses = 0.0;
      try {
        totalResponses = Double.parseDouble(parts[4]);
      } catch (NumberFormatException e) {
        return null;
      }
      SortedMap<String, Double> responsesByCountry =
          parseResponses(parts[5]);
      SortedMap<String, Double> responsesByTransport =
          parseResponses(parts[6]);
      SortedMap<String, Double> responsesByVersion =
          parseResponses(parts[7]);
      if (responsesByCountry == null || responsesByTransport == null ||
          responsesByVersion == null) {
        return null;
      }
      return new ResponseHistory(startMillis, endMillis, totalResponses,
          responsesByCountry, responsesByTransport, responsesByVersion);
    }
    private static SortedMap<String, Double> parseResponses(
        String responsesString) {
      SortedMap<String, Double> responses = new TreeMap<String, Double>();
      if (responsesString.length() > 0) {
        for (String pair : responsesString.split(",")) {
          String[] keyValue = pair.split("=");
          if (keyValue.length != 2) {
            return null;
          }
          double value = 0.0;
          try {
            value = Double.parseDouble(keyValue[1]);
          } catch (NumberFormatException e) {
            return null;
          }
          responses.put(keyValue[0], value);
        }
      }
      return responses;
    }
    public String toString() {
      StringBuilder sb = new StringBuilder();
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setLenient(false);
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      sb.append(dateTimeFormat.format(startMillis));
      sb.append(" " + dateTimeFormat.format(endMillis));
      sb.append(" " + String.format("%.3f", this.totalResponses));
      this.appendResponses(sb, this.responsesByCountry);
      this.appendResponses(sb, this.responsesByTransport);
      this.appendResponses(sb, this.responsesByVersion);
      return sb.toString();
    }
    private void appendResponses(StringBuilder sb,
        SortedMap<String, Double> responses) {
      sb.append(" ");
      int written = 0;
      for (Map.Entry<String, Double> e : responses.entrySet()) {
        sb.append((written++ > 0 ? "," : "") + e.getKey() + "="
            + String.format("%.3f", e.getValue()));
      }
    }
    public void addResponses(ResponseHistory other) {
      this.totalResponses += other.totalResponses;
      this.addResponsesByCategory(this.responsesByCountry,
          other.responsesByCountry);
      this.addResponsesByCategory(this.responsesByTransport,
          other.responsesByTransport);
      this.addResponsesByCategory(this.responsesByVersion,
          other.responsesByVersion);
      if (this.startMillis > other.startMillis) {
        this.startMillis = other.startMillis;
      }
      if (this.endMillis < other.endMillis) {
        this.endMillis = other.endMillis;
      }
    }
    private void addResponsesByCategory(
        SortedMap<String, Double> thisResponses,
        SortedMap<String, Double> otherResponses) {
      for (Map.Entry<String, Double> e : otherResponses.entrySet()) {
        if (thisResponses.containsKey(e.getKey())) {
          thisResponses.put(e.getKey(), thisResponses.get(e.getKey())
              + e.getValue());
        } else {
          thisResponses.put(e.getKey(), e.getValue());
        }
      }
    }
    public int compareTo(ResponseHistory other) {
      return this.startMillis < other.startMillis ? -1 :
          this.startMillis > other.startMillis ? 1 : 0;
    }
    public boolean equals(Object other) {
      return other instanceof ResponseHistory &&
          this.startMillis == ((ResponseHistory) other).startMillis;
    }
  }

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public ClientsDataWriter(DescriptorSource descriptorSource,
      DocumentStore documentStore, Time time) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerListener(this,
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

  private SortedMap<String, SortedSet<ResponseHistory>> newResponses =
      new TreeMap<String, SortedSet<ResponseHistory>>();

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
      ResponseHistory newResponseHistory = new ResponseHistory(
          startMillis, endMillis, totalResponses, responsesByCountry,
          responsesByTransport, responsesByVersion); 
      if (!this.newResponses.containsKey(hashedFingerprint)) {
        this.newResponses.put(hashedFingerprint,
            new TreeSet<ResponseHistory>());
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
    for (Map.Entry<String, SortedSet<ResponseHistory>> e :
        this.newResponses.entrySet()) {
      String hashedFingerprint = e.getKey();
      SortedSet<ResponseHistory> history =
          this.readHistory(hashedFingerprint);
      this.addToHistory(history, e.getValue());
      history = this.compressHistory(history);
      this.writeHistory(hashedFingerprint, history);
    }
    Logger.printStatusTime("Updated clients status files");
  }

  private SortedSet<ResponseHistory> readHistory(
      String hashedFingerprint) {
    SortedSet<ResponseHistory> history = new TreeSet<ResponseHistory>();
    ClientsStatus clientsStatus = this.documentStore.retrieve(
        ClientsStatus.class, false, hashedFingerprint);
    if (clientsStatus != null) {
      Scanner s = new Scanner(clientsStatus.documentString);
      while (s.hasNextLine()) {
        String line = s.nextLine();
        ResponseHistory parsedLine = ResponseHistory.fromString(line);
        if (parsedLine != null) {
          history.add(parsedLine);
        } else {
          System.err.println("Could not parse clients history line '"
              + line + "' for fingerprint '" + hashedFingerprint
              + "'.  Skipping."); 
        }
      }
      s.close();
    }
    return history;
  }

  private void addToHistory(SortedSet<ResponseHistory> history,
      SortedSet<ResponseHistory> newIntervals) {
    for (ResponseHistory interval : newIntervals) {
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

  private SortedSet<ResponseHistory> compressHistory(
      SortedSet<ResponseHistory> history) {
    SortedSet<ResponseHistory> compressedHistory =
        new TreeSet<ResponseHistory>();
    ResponseHistory lastResponses = null;
    for (ResponseHistory responses : history) {
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
      if (lastResponses != null &&
          lastResponses.endMillis == responses.startMillis &&
          ((lastResponses.endMillis - 1L) / intervalLengthMillis) ==
          ((responses.endMillis - 1L) / intervalLengthMillis)) {
        lastResponses.addResponses(responses);
      } else {
        if (lastResponses != null) {
          compressedHistory.add(lastResponses);
        }
        lastResponses = responses;
      }
    }
    if (lastResponses != null) {
      compressedHistory.add(lastResponses);
    }
    return compressedHistory;
  }

  private void writeHistory(String hashedFingerprint,
      SortedSet<ResponseHistory> history) {
    StringBuilder sb = new StringBuilder();
    for (ResponseHistory responses : history) {
      sb.append(responses.toString() + "\n");
    }
    ClientsStatus clientsStatus = new ClientsStatus();
    clientsStatus.documentString = sb.toString();
    this.documentStore.store(clientsStatus, hashedFingerprint);
  }

  public void updateDocuments() {
    for (String hashedFingerprint : this.newResponses.keySet()) {
      SortedSet<ResponseHistory> history =
          this.readHistory(hashedFingerprint);
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
      SortedSet<ResponseHistory> history) {
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
      SortedSet<ResponseHistory> history) {
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
    for (ResponseHistory hist : history) {
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
    for (SortedSet<ResponseHistory> hist : this.newResponses.values()) {
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
        + Logger.formatDecimalNumber(this.newResponses.size())
        + " client document files updated\n");
    return sb.toString();
  }
}

