/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.ClientsDocument;
import org.torproject.onionoo.docs.ClientsHistory;
import org.torproject.onionoo.docs.ClientsStatus;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.GraphHistory;
import org.torproject.onionoo.docs.NodeStatus;
import org.torproject.onionoo.docs.UpdateStatus;
import org.torproject.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

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
public class ClientsDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      ClientsDocumentWriter.class);

  private DocumentStore documentStore;

  public ClientsDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  private int writtenDocuments = 0;

  @Override
  public void writeDocuments() {
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updateDocuments = this.documentStore.list(
        ClientsStatus.class, updatedMillis);
    for (String hashedFingerprint : updateDocuments) {
      NodeStatus nodeStatus = this.documentStore.retrieve(NodeStatus.class,
          true, hashedFingerprint);
      if (null == nodeStatus) {
        continue;
      }
      ClientsStatus clientsStatus = this.documentStore.retrieve(
          ClientsStatus.class, true, hashedFingerprint);
      if (clientsStatus == null) {
        continue;
      }
      SortedSet<ClientsHistory> history = clientsStatus.getHistory();
      ClientsDocument clientsDocument = this.compileClientsDocument(
          hashedFingerprint, nodeStatus, history);
      this.documentStore.store(clientsDocument, hashedFingerprint);
      this.writtenDocuments++;
    }
    log.info("Wrote clients document files");
  }

  private String[] graphNames = new String[] {
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private Period[] graphIntervals = new Period[] {
      Period.ofWeeks(1),
      Period.ofMonths(1),
      Period.ofMonths(3),
      Period.ofYears(1),
      Period.ofYears(5) };

  private long[] dataPointIntervals = new long[] {
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private ClientsDocument compileClientsDocument(String hashedFingerprint,
      NodeStatus nodeStatus, SortedSet<ClientsHistory> history) {
    ClientsDocument clientsDocument = new ClientsDocument();
    clientsDocument.setFingerprint(hashedFingerprint);
    Map<String, GraphHistory> averageClients = new LinkedHashMap<>();
    for (int graphIntervalIndex = 0; graphIntervalIndex
        < this.graphIntervals.length; graphIntervalIndex++) {
      String graphName = this.graphNames[graphIntervalIndex];
      GraphHistory graphHistory = this.compileClientsHistory(
          graphIntervalIndex, history, nodeStatus.getLastSeenMillis());
      if (graphHistory != null) {
        averageClients.put(graphName, graphHistory);
      }
    }
    clientsDocument.setAverageClients(averageClients);
    return clientsDocument;
  }

  private GraphHistory compileClientsHistory(
      int graphIntervalIndex, SortedSet<ClientsHistory> history,
      long lastSeenMillis) {
    Period graphInterval = this.graphIntervals[graphIntervalIndex];
    long dataPointInterval =
        this.dataPointIntervals[graphIntervalIndex];
    List<Double> dataPoints = new ArrayList<>();
    long graphEndMillis = ((lastSeenMillis + DateTimeHelper.ONE_HOUR)
        / dataPointInterval) * dataPointInterval;
    long graphStartMillis = LocalDateTime
        .ofEpochSecond(graphEndMillis / 1000L, 0, ZoneOffset.UTC)
        .minus(graphInterval)
        .toEpochSecond(ZoneOffset.UTC) * 1000L;
    long intervalStartMillis = graphStartMillis;
    long millis = 0L;
    double responses = 0.0;
    for (ClientsHistory hist : history) {
      if (hist.getEndMillis() <= intervalStartMillis) {
        continue;
      } else if (hist.getEndMillis() > graphEndMillis) {
        break;
      }
      while ((intervalStartMillis / dataPointInterval)
          != ((hist.getEndMillis() - 1L) / dataPointInterval)) {
        dataPoints.add(millis * 2L < dataPointInterval
            ? -1.0 : responses * ((double) DateTimeHelper.ONE_DAY)
            / (((double) millis) * 10.0));
        responses = 0.0;
        millis = 0L;
        intervalStartMillis += dataPointInterval;
      }
      responses += hist.getTotalResponses();
      millis += (hist.getEndMillis() - hist.getStartMillis());
    }
    dataPoints.add(millis * 2L < dataPointInterval
        ? -1.0 : responses * ((double) DateTimeHelper.ONE_DAY)
        / (((double) millis) * 10.0));
    double maxValue = 0.0;
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
        if (dataPoint > maxValue) {
          maxValue = dataPoint;
        }
      }
    }
    if (firstNonNullIndex < 0) {
      /* Not a single non-negative value in the data points. */
      return null;
    }
    long firstDataPointMillis = graphStartMillis + firstNonNullIndex
        * dataPointInterval + dataPointInterval / 2L;
    if (graphIntervalIndex > 0 && firstDataPointMillis >= LocalDateTime
        .ofEpochSecond(graphEndMillis / 1000L, 0, ZoneOffset.UTC)
        .minus(graphIntervals[graphIntervalIndex - 1])
        .toEpochSecond(ZoneOffset.UTC) * 1000L) {
      /* Skip clients history object, because it doesn't contain
       * anything new that wasn't already contained in the last
       * clients history object(s). */
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
      values.add(dataPoint < 0.0 ? null :
          (int) ((dataPoint * 999.0) / maxValue));
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
        this.writtenDocuments) + " clients document files updated\n");
    return sb.toString();
  }
}

