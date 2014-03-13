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

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

public class UptimeDataWriter implements DescriptorListener,
    StatusUpdater, FingerprintListener, DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public UptimeDataWriter(DescriptorSource descriptorSource,
      DocumentStore documentStore, Time time) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
    this.registerDescriptorListeners();
    this.registerFingerprintListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_STATUSES);
  }

  public void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerFingerprintListener(this,
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

  private static class UptimeHistory
      implements Comparable<UptimeHistory> {
    private boolean relay;
    private long startMillis;
    private int uptimeHours;
    private UptimeHistory(boolean relay, long startMillis,
        int uptimeHours) {
      this.relay = relay;
      this.startMillis = startMillis;
      this.uptimeHours = uptimeHours;
    }
    public static UptimeHistory fromString(String uptimeHistoryString) {
      String[] parts = uptimeHistoryString.split(" ", 3);
      if (parts.length != 3) {
        return null;
      }
      boolean relay = false;
      if (parts[0].equals("r")) {
        relay = true;
      } else if (!parts[0].equals("b")) {
        return null;
      }
      long startMillis = -1L;
      SimpleDateFormat dateHourFormat = new SimpleDateFormat(
          "yyyy-MM-dd-HH");
      dateHourFormat.setLenient(false);
      dateHourFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      try {
        startMillis = dateHourFormat.parse(parts[1]).getTime();
      } catch (ParseException e) {
        return null;
      }
      int uptimeHours = -1;
      try {
        uptimeHours = Integer.parseInt(parts[2]);
      } catch (NumberFormatException e) {
        return null;
      }
      return new UptimeHistory(relay, startMillis, uptimeHours);
    }
    public String toString() {
      StringBuilder sb = new StringBuilder();
      SimpleDateFormat dateHourFormat = new SimpleDateFormat(
          "yyyy-MM-dd-HH");
      dateHourFormat.setLenient(false);
      dateHourFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      sb.append(this.relay ? "r" : "b");
      sb.append(" " + dateHourFormat.format(this.startMillis));
      sb.append(" " + String.format("%d", this.uptimeHours));
      return sb.toString();
    }
    public void addUptime(UptimeHistory other) {
      this.uptimeHours += other.uptimeHours;
      if (this.startMillis > other.startMillis) {
        this.startMillis = other.startMillis;
      }
    }
    public int compareTo(UptimeHistory other) {
      if (this.relay && !other.relay) {
        return -1;
      } else if (!this.relay && other.relay) {
        return 1;
      }
      return this.startMillis < other.startMillis ? -1 :
          this.startMillis > other.startMillis ? 1 : 0;
    }
    public boolean equals(Object other) {
      return other instanceof UptimeHistory &&
          this.relay == ((UptimeHistory) other).relay &&
          this.startMillis == ((UptimeHistory) other).startMillis;
    }
  }

  private void updateStatus(boolean relay, String fingerprint,
      SortedSet<Long> newUptimeHours) {
    SortedSet<UptimeHistory> history = this.readHistory(fingerprint);
    this.addToHistory(history, relay, newUptimeHours);
    history = this.compressHistory(history);
    this.writeHistory(fingerprint, history);
  }

  private SortedSet<UptimeHistory> readHistory(String fingerprint) {
    SortedSet<UptimeHistory> history = new TreeSet<UptimeHistory>();
    UptimeStatus uptimeStatus = fingerprint == null ?
        documentStore.retrieve(UptimeStatus.class, false) :
        documentStore.retrieve(UptimeStatus.class, false, fingerprint);
    if (uptimeStatus != null) {
      Scanner s = new Scanner(uptimeStatus.documentString);
      while (s.hasNextLine()) {
        String line = s.nextLine();
        UptimeHistory parsedLine = UptimeHistory.fromString(line);
        if (parsedLine != null) {
          history.add(parsedLine);
        } else {
          System.err.println("Could not parse uptime history line '"
              + line + "' for fingerprint '" + fingerprint
              + "'.  Skipping.");
        }
      }
      s.close();
    }
    return history;
  }

  private void addToHistory(SortedSet<UptimeHistory> history,
      boolean relay, SortedSet<Long> newIntervals) {
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

  private SortedSet<UptimeHistory> compressHistory(
      SortedSet<UptimeHistory> history) {
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
    return compressedHistory;
  }

  private void writeHistory(String fingerprint,
      SortedSet<UptimeHistory> history) {
    StringBuilder sb = new StringBuilder();
    for (UptimeHistory interval : history) {
      sb.append(interval.toString() + "\n");
    }
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.documentString = sb.toString();
    if (fingerprint == null) {
      this.documentStore.store(uptimeStatus);
    } else {
      this.documentStore.store(uptimeStatus, fingerprint);
    }
  }

  private SortedSet<String> newRelayFingerprints = new TreeSet<String>(),
      newBridgeFingerprints = new TreeSet<String>();

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    if (relay) {
      this.newRelayFingerprints.addAll(fingerprints);
    } else {
      this.newBridgeFingerprints.addAll(fingerprints);
    }
  }

  public void writeDocuments() {
    SortedSet<UptimeHistory>
        knownRelayStatuses = new TreeSet<UptimeHistory>(),
        knownBridgeStatuses = new TreeSet<UptimeHistory>();
    SortedSet<UptimeHistory> knownStatuses = this.readHistory(null);
    for (UptimeHistory status : knownStatuses) {
      if (status.relay) {
        knownRelayStatuses.add(status);
      } else {
        knownBridgeStatuses.add(status);
      }
    }
    for (String fingerprint : this.newRelayFingerprints) {
      this.updateDocument(true, fingerprint, knownRelayStatuses);
    }
    for (String fingerprint : this.newBridgeFingerprints) {
      this.updateDocument(false, fingerprint, knownBridgeStatuses);
    }
    Logger.printStatusTime("Wrote uptime document files");
  }

  private void updateDocument(boolean relay, String fingerprint,
      SortedSet<UptimeHistory> knownStatuses) {
    SortedSet<UptimeHistory> history = this.readHistory(fingerprint);
    UptimeDocument uptimeDocument = new UptimeDocument();
    uptimeDocument.documentString = this.formatHistoryString(relay,
        fingerprint, history, knownStatuses);
    this.documentStore.store(uptimeDocument, fingerprint);
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
      60L * 60L * 1000L,
      4L * 60L * 60L * 1000L,
      12L * 60L * 60L * 1000L,
      2L * 24L * 60L * 60L * 1000L,
      10L * 24L * 60L * 60L * 1000L };

  private String formatHistoryString(boolean relay, String fingerprint,
      SortedSet<UptimeHistory> history,
      SortedSet<UptimeHistory> knownStatuses) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"fingerprint\":\"" + fingerprint + "\"");
    sb.append(",\n\"uptime\":{");
    int graphIntervalsWritten = 0;
    for (int graphIntervalIndex = 0; graphIntervalIndex <
        this.graphIntervals.length; graphIntervalIndex++) {
      String timeline = this.formatTimeline(graphIntervalIndex, relay,
          history, knownStatuses);
      if (timeline != null) {
        sb.append((graphIntervalsWritten++ > 0 ? "," : "") + "\n"
            + timeline);
      }
    }
    sb.append("}");
    sb.append("\n}\n");
    return sb.toString();
  }

  private String formatTimeline(int graphIntervalIndex, boolean relay,
      SortedSet<UptimeHistory> history,
      SortedSet<UptimeHistory> knownStatuses) {
    String graphName = this.graphNames[graphIntervalIndex];
    long graphInterval = this.graphIntervals[graphIntervalIndex];
    long dataPointInterval =
        this.dataPointIntervals[graphIntervalIndex];
    int dataPointIntervalHours = (int) (dataPointInterval
        / ONE_HOUR_MILLIS);
    List<Integer> statusDataPoints = new ArrayList<Integer>();
    long intervalStartMillis = ((this.now - graphInterval)
        / dataPointInterval) * dataPointInterval;
    int statusHours = 0;
    for (UptimeHistory hist : knownStatuses) {
      if (hist.relay != relay) {
        continue;
      }
      long histEndMillis = hist.startMillis + ONE_HOUR_MILLIS
          * hist.uptimeHours;
      if (histEndMillis < intervalStartMillis) {
        continue;
      }
      while (hist.startMillis >= intervalStartMillis
          + dataPointInterval) {
        statusDataPoints.add(statusHours * 5 > dataPointIntervalHours
            ? statusHours : -1);
        statusHours = 0;
        intervalStartMillis += dataPointInterval;
      }
      while (histEndMillis >= intervalStartMillis + dataPointInterval) {
        statusHours += (int) ((intervalStartMillis + dataPointInterval
            - Math.max(hist.startMillis, intervalStartMillis))
            / ONE_HOUR_MILLIS);
        statusDataPoints.add(statusHours * 5 > dataPointIntervalHours
            ? statusHours : -1);
        statusHours = 0;
        intervalStartMillis += dataPointInterval;
      }
      statusHours += (int) ((histEndMillis - Math.max(hist.startMillis,
          intervalStartMillis)) / ONE_HOUR_MILLIS);
    }
    statusDataPoints.add(statusHours * 5 > dataPointIntervalHours
        ? statusHours : -1);
    List<Integer> uptimeDataPoints = new ArrayList<Integer>();
    intervalStartMillis = ((this.now - graphInterval)
        / dataPointInterval) * dataPointInterval;
    int uptimeHours = 0;
    long firstStatusStartMillis = -1L;
    for (UptimeHistory hist : history) {
      if (hist.relay != relay) {
        continue;
      }
      if (firstStatusStartMillis < 0L) {
        firstStatusStartMillis = hist.startMillis;
      }
      long histEndMillis = hist.startMillis + ONE_HOUR_MILLIS
          * hist.uptimeHours;
      if (histEndMillis < intervalStartMillis) {
        continue;
      }
      while (hist.startMillis >= intervalStartMillis
          + dataPointInterval) {
        if (firstStatusStartMillis < intervalStartMillis
            + dataPointInterval) {
          uptimeDataPoints.add(uptimeHours);
        } else {
          uptimeDataPoints.add(-1);
        }
        uptimeHours = 0;
        intervalStartMillis += dataPointInterval;
      }
      while (histEndMillis >= intervalStartMillis + dataPointInterval) {
        uptimeHours += (int) ((intervalStartMillis + dataPointInterval
            - Math.max(hist.startMillis, intervalStartMillis))
            / ONE_HOUR_MILLIS);
        uptimeDataPoints.add(uptimeHours);
        uptimeHours = 0;
        intervalStartMillis += dataPointInterval;
      }
      uptimeHours += (int) ((histEndMillis - Math.max(hist.startMillis,
          intervalStartMillis)) / ONE_HOUR_MILLIS);
    }
    uptimeDataPoints.add(uptimeHours);
    List<Double> dataPoints = new ArrayList<Double>();
    for (int dataPointIndex = 0; dataPointIndex < statusDataPoints.size();
        dataPointIndex++) {
      if (dataPointIndex >= uptimeDataPoints.size()) {
        dataPoints.add(0.0);
      } else if (uptimeDataPoints.get(dataPointIndex) >= 0 &&
          statusDataPoints.get(dataPointIndex) > 0) {
        dataPoints.add(((double) uptimeDataPoints.get(dataPointIndex))
            / ((double) statusDataPoints.get(dataPointIndex)));
      } else {
        dataPoints.add(-1.0);
      }
    }
    int firstNonNullIndex = -1, lastNonNullIndex = -1;
    for (int dataPointIndex = 0; dataPointIndex < dataPoints.size();
        dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (firstNonNullIndex < 0) {
          firstNonNullIndex = dataPointIndex;
        }
        lastNonNullIndex = dataPointIndex;
      }
    }
    if (firstNonNullIndex < 0) {
      return null;
    }
    long firstDataPointMillis = (((this.now - graphInterval)
        / dataPointInterval) + firstNonNullIndex)
        * dataPointInterval + dataPointInterval / 2L;
    if (graphIntervalIndex > 0 && firstDataPointMillis >=
        this.now - graphIntervals[graphIntervalIndex - 1]) {
      /* Skip uptime history object, because it doesn't contain
       * anything new that wasn't already contained in the last
       * uptime history object(s). */
      return null;
    }
    long lastDataPointMillis = firstDataPointMillis
        + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
    double factor = 1.0 / 999.0;
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
          + (dataPoint < -0.5 ? "null" :
          String.valueOf((long) (dataPoint * 999.0))));
    }
    sb.append("]}");
    if (foundTwoAdjacentDataPoints) {
      return sb.toString();
    } else {
      return null;
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
    sb.append("    " + Logger.formatDecimalNumber(
        this.newRunningRelays.size() + this.newRunningBridges.size())
        + " uptime document files updated\n");
    return sb.toString();
  }
}

