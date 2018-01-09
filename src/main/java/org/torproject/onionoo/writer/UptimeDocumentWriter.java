/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.GraphHistory;
import org.torproject.onionoo.docs.NodeStatus;
import org.torproject.onionoo.docs.UpdateStatus;
import org.torproject.onionoo.docs.UptimeDocument;
import org.torproject.onionoo.docs.UptimeHistory;
import org.torproject.onionoo.docs.UptimeStatus;
import org.torproject.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class UptimeDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      UptimeDocumentWriter.class);

  private DocumentStore documentStore;

  public UptimeDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  @Override
  public void writeDocuments() {
    UptimeStatus uptimeStatus = this.documentStore.retrieve(
        UptimeStatus.class, true);
    if (uptimeStatus == null) {
      /* No global uptime information available. */
      return;
    }
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updatedUptimeStatuses = this.documentStore.list(
        UptimeStatus.class, updatedMillis);
    for (String fingerprint : updatedUptimeStatuses) {
      this.updateDocument(fingerprint, uptimeStatus);
    }
    log.info("Wrote uptime document files");
  }

  private int writtenDocuments = 0;

  private void updateDocument(String fingerprint,
      UptimeStatus knownStatuses) {
    NodeStatus nodeStatus = this.documentStore.retrieve(NodeStatus.class,
        true, fingerprint);
    UptimeStatus uptimeStatus = this.documentStore.retrieve(
        UptimeStatus.class, true, fingerprint);
    if (null != nodeStatus && null != uptimeStatus) {
      boolean relay = uptimeStatus.getBridgeHistory().isEmpty();
      SortedSet<UptimeHistory> history = relay
          ? uptimeStatus.getRelayHistory()
          : uptimeStatus.getBridgeHistory();
      SortedSet<UptimeHistory> knownStatusesHistory = relay
          ? knownStatuses.getRelayHistory()
          : knownStatuses.getBridgeHistory();
      long lastSeenMillis = nodeStatus.getLastSeenMillis();
      UptimeDocument uptimeDocument = this.compileUptimeDocument(relay,
          fingerprint, history, knownStatusesHistory, lastSeenMillis);
      this.documentStore.store(uptimeDocument, fingerprint);
      this.writtenDocuments++;
    }
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

  private UptimeDocument compileUptimeDocument(boolean relay,
      String fingerprint, SortedSet<UptimeHistory> history,
      SortedSet<UptimeHistory> knownStatuses, long lastSeenMillis) {
    UptimeDocument uptimeDocument = new UptimeDocument();
    uptimeDocument.setFingerprint(fingerprint);
    Map<String, GraphHistory> uptime = new LinkedHashMap<>();
    for (int graphIntervalIndex = 0; graphIntervalIndex
        < this.graphIntervals.length; graphIntervalIndex++) {
      String graphName = this.graphNames[graphIntervalIndex];
      GraphHistory graphHistory = this.compileUptimeHistory(
          graphIntervalIndex, relay, history, knownStatuses, lastSeenMillis,
          null);
      if (graphHistory != null) {
        uptime.put(graphName, graphHistory);
      }
    }
    uptimeDocument.setUptime(uptime);
    SortedMap<String, Map<String, GraphHistory>> flags = new TreeMap<>();
    SortedSet<String> allFlags = new TreeSet<>();
    for (UptimeHistory hist : history) {
      if (hist.getFlags() != null) {
        allFlags.addAll(hist.getFlags());
      }
    }
    for (String flag : allFlags) {
      Map<String, GraphHistory> graphsForFlags = new LinkedHashMap<>();
      for (int graphIntervalIndex = 0; graphIntervalIndex
          < this.graphIntervals.length; graphIntervalIndex++) {
        String graphName = this.graphNames[graphIntervalIndex];
        GraphHistory graphHistory = this.compileUptimeHistory(
            graphIntervalIndex, relay, history, knownStatuses, lastSeenMillis,
            flag);
        if (graphHistory != null) {
          graphsForFlags.put(graphName, graphHistory);
        }
      }
      if (!graphsForFlags.isEmpty()) {
        flags.put(flag, graphsForFlags);
      }
    }
    if (!flags.isEmpty()) {
      uptimeDocument.setFlags(flags);
    }
    return uptimeDocument;
  }

  private GraphHistory compileUptimeHistory(int graphIntervalIndex,
      boolean relay, SortedSet<UptimeHistory> history,
      SortedSet<UptimeHistory> knownStatuses, long lastSeenMillis,
      String flag) {
    long graphInterval = this.graphIntervals[graphIntervalIndex];
    long dataPointInterval =
        this.dataPointIntervals[graphIntervalIndex];
    int dataPointIntervalHours = (int) (dataPointInterval
        / DateTimeHelper.ONE_HOUR);
    List<Integer> uptimeDataPoints = new ArrayList<>();
    long graphEndMillis = ((lastSeenMillis + DateTimeHelper.ONE_HOUR)
        / dataPointInterval) * dataPointInterval;
    long graphStartMillis = graphEndMillis - graphInterval;
    long intervalStartMillis = graphStartMillis;
    int uptimeHours = 0;
    long firstStatusStartMillis = -1L;
    for (UptimeHistory hist : history) {
      if (hist.isRelay() != relay
          || (flag != null && (hist.getFlags() == null
          || !hist.getFlags().contains(flag)))) {
        continue;
      }
      if (firstStatusStartMillis < 0L) {
        firstStatusStartMillis = hist.getStartMillis();
      }
      long histEndMillis = hist.getStartMillis() + DateTimeHelper.ONE_HOUR
          * hist.getUptimeHours();
      if (histEndMillis < intervalStartMillis) {
        continue;
      } else if (histEndMillis > graphEndMillis) {
        histEndMillis = graphEndMillis;
      }
      while (hist.getStartMillis() >= intervalStartMillis
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
            - Math.max(hist.getStartMillis(), intervalStartMillis))
            / DateTimeHelper.ONE_HOUR);
        uptimeDataPoints.add(uptimeHours);
        uptimeHours = 0;
        intervalStartMillis += dataPointInterval;
      }
      uptimeHours += (int) ((histEndMillis - Math.max(
          hist.getStartMillis(), intervalStartMillis))
          / DateTimeHelper.ONE_HOUR);
    }
    uptimeDataPoints.add(uptimeHours);
    List<Integer> statusDataPoints = new ArrayList<>();
    intervalStartMillis = graphStartMillis;
    int statusHours = -1;
    for (UptimeHistory hist : knownStatuses) {
      if (hist.getStartMillis() >= graphEndMillis) {
        break;
      }
      if (hist.isRelay() != relay
          || (flag != null && (hist.getFlags() == null
          || !hist.getFlags().contains(flag)))) {
        continue;
      }
      long histEndMillis = hist.getStartMillis() + DateTimeHelper.ONE_HOUR
          * hist.getUptimeHours();
      if (histEndMillis < intervalStartMillis) {
        continue;
      } else if (histEndMillis > graphEndMillis) {
        histEndMillis = graphEndMillis;
      }
      while (hist.getStartMillis() >= intervalStartMillis
          + dataPointInterval) {
        statusDataPoints.add(statusHours * 5 > dataPointIntervalHours
            ? statusHours : -1);
        statusHours = -1;
        intervalStartMillis += dataPointInterval;
      }
      while (histEndMillis >= intervalStartMillis + dataPointInterval) {
        if (statusHours < 0) {
          statusHours = 0;
        }
        statusHours += (int) ((intervalStartMillis + dataPointInterval
            - Math.max(Math.max(hist.getStartMillis(),
            firstStatusStartMillis), intervalStartMillis))
            / DateTimeHelper.ONE_HOUR);
        statusDataPoints.add(statusHours * 5 > dataPointIntervalHours
            ? statusHours : -1);
        statusHours = -1;
        intervalStartMillis += dataPointInterval;
      }
      if (statusHours < 0) {
        statusHours = 0;
      }
      statusHours += (int) ((histEndMillis - Math.max(Math.max(
          hist.getStartMillis(), firstStatusStartMillis),
          intervalStartMillis)) / DateTimeHelper.ONE_HOUR);
    }
    if (statusHours > 0) {
      statusDataPoints.add(statusHours * 5 > dataPointIntervalHours
          ? statusHours : -1);
    }
    List<Double> dataPoints = new ArrayList<>();
    for (int dataPointIndex = 0; dataPointIndex < statusDataPoints.size();
        dataPointIndex++) {
      if (dataPointIndex >= uptimeDataPoints.size()) {
        dataPoints.add(0.0);
      } else if (uptimeDataPoints.get(dataPointIndex) >= 0
          && statusDataPoints.get(dataPointIndex) > 0) {
        dataPoints.add(((double) uptimeDataPoints.get(dataPointIndex))
            / ((double) statusDataPoints.get(dataPointIndex)));
      } else {
        dataPoints.add(-1.0);
      }
    }
    int firstNonNullIndex = -1;
    int lastNonNullIndex = -1;
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
      /* Not a single non-negative value in the data points. */
      return null;
    }
    long firstDataPointMillis = graphStartMillis + firstNonNullIndex
        * dataPointInterval + dataPointInterval / 2L;
    if (graphIntervalIndex > 0 && firstDataPointMillis >=
        ((lastSeenMillis + DateTimeHelper.ONE_HOUR) / dataPointInterval)
        * dataPointInterval - graphIntervals[graphIntervalIndex - 1]) {
      /* Skip uptime history object, because it doesn't contain
       * anything new that wasn't already contained in the last
       * uptime history object(s). */
      return null;
    }
    long lastDataPointMillis = firstDataPointMillis
        + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
    int count = lastNonNullIndex - firstNonNullIndex + 1;
    GraphHistory graphHistory = new GraphHistory();
    graphHistory.setFirst(firstDataPointMillis);
    graphHistory.setLast(lastDataPointMillis);
    graphHistory.setInterval((int) (dataPointInterval
        / DateTimeHelper.ONE_SECOND));
    graphHistory.setFactor(1.0 / 999.0);
    graphHistory.setCount(count);
    int previousNonNullIndex = -2;
    boolean foundTwoAdjacentDataPoints = false;
    List<Integer> values = new ArrayList<>();
    for (int dataPointIndex = firstNonNullIndex; dataPointIndex
        <= lastNonNullIndex; dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (dataPointIndex - previousNonNullIndex == 1) {
          foundTwoAdjacentDataPoints = true;
        }
        previousNonNullIndex = dataPointIndex;
      }
      values.add(dataPoint < -0.5 ? null : ((int) (dataPoint * 999.0)));
    }
    graphHistory.setValues(values);
    if (foundTwoAdjacentDataPoints) {
      return graphHistory;
    } else {
      /* There are no two adjacent values in the data points that are
       * required to draw a line graph. */
      return null;
    }
  }

  @Override
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        this.writtenDocuments) + " uptime document files written\n");
    return sb.toString();
  }
}

