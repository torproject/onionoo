/* Copyright 2011--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.BandwidthDocument;
import org.torproject.onionoo.docs.BandwidthStatus;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.GraphHistory;
import org.torproject.onionoo.docs.UpdateStatus;
import org.torproject.onionoo.util.TimeFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

public class BandwidthDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      BandwidthDocumentWriter.class);

  private DocumentStore documentStore;

  private long now;

  public BandwidthDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.now = TimeFactory.getTime().currentTimeMillis();
  }

  @Override
  public void writeDocuments() {
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updateBandwidthDocuments = this.documentStore.list(
        BandwidthStatus.class, updatedMillis);
    for (String fingerprint : updateBandwidthDocuments) {
      BandwidthStatus bandwidthStatus = this.documentStore.retrieve(
          BandwidthStatus.class, true, fingerprint);
      if (bandwidthStatus == null) {
        continue;
      }
      BandwidthDocument bandwidthDocument = this.compileBandwidthDocument(
          fingerprint, bandwidthStatus);
      this.documentStore.store(bandwidthDocument, fingerprint);
    }
    log.info("Wrote bandwidth document files");
  }


  private BandwidthDocument compileBandwidthDocument(String fingerprint,
      BandwidthStatus bandwidthStatus) {
    BandwidthDocument bandwidthDocument = new BandwidthDocument();
    bandwidthDocument.setFingerprint(fingerprint);
    bandwidthDocument.setWriteHistory(this.compileGraphType(
        bandwidthStatus.getWriteHistory()));
    bandwidthDocument.setReadHistory(this.compileGraphType(
        bandwidthStatus.getReadHistory()));
    return bandwidthDocument;
  }

  private String[] graphNames = new String[] {
      "3_days",
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private long[] graphIntervals = new long[] {
      DateTimeHelper.THREE_DAYS,
      DateTimeHelper.ONE_WEEK,
      DateTimeHelper.ROUGHLY_ONE_MONTH,
      DateTimeHelper.ROUGHLY_THREE_MONTHS,
      DateTimeHelper.ROUGHLY_ONE_YEAR,
      DateTimeHelper.ROUGHLY_FIVE_YEARS };

  private long[] dataPointIntervals = new long[] {
      DateTimeHelper.FIFTEEN_MINUTES,
      DateTimeHelper.ONE_HOUR,
      DateTimeHelper.FOUR_HOURS,
      DateTimeHelper.TWELVE_HOURS,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private Map<String, GraphHistory> compileGraphType(
      SortedMap<Long, long[]> history) {
    Map<String, GraphHistory> graphs =
        new LinkedHashMap<String, GraphHistory>();
    for (int i = 0; i < this.graphIntervals.length; i++) {
      String graphName = this.graphNames[i];
      long graphInterval = this.graphIntervals[i];
      long dataPointInterval = this.dataPointIntervals[i];
      List<Long> dataPoints = new ArrayList<Long>();
      long intervalStartMillis = ((this.now - graphInterval)
          / dataPointInterval) * dataPointInterval;
      long totalMillis = 0L;
      long totalBandwidth = 0L;
      for (long[] v : history.values()) {
        long endMillis = v[1];
        if (endMillis < intervalStartMillis) {
          continue;
        }
        long startMillis = v[0];
        if (startMillis > this.now) {
          break;
        }
        if (endMillis - startMillis > dataPointInterval) {
          /* This history interval is too long for this graph's data point
           * interval.  Maybe the next graph will contain it, but not this
           * one. */
          continue;
        }
        while ((intervalStartMillis / dataPointInterval)
            != (endMillis / dataPointInterval)) {
          dataPoints.add(totalMillis * 5L < dataPointInterval
              ? -1L : (totalBandwidth * DateTimeHelper.ONE_SECOND)
              / totalMillis);
          totalBandwidth = 0L;
          totalMillis = 0L;
          intervalStartMillis += dataPointInterval;
        }
        long bandwidth = v[2];
        totalBandwidth += bandwidth;
        totalMillis += (endMillis - startMillis);
      }
      dataPoints.add(totalMillis * 5L < dataPointInterval
          ? -1L : (totalBandwidth * DateTimeHelper.ONE_SECOND)
          / totalMillis);
      long maxValue = 1L;
      int firstNonNullIndex = -1;
      int lastNonNullIndex = -1;
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
      if (i > 0 && !graphs.isEmpty()
          && firstDataPointMillis >= this.now - graphIntervals[i - 1]) {
        /* Skip bandwidth history object, because it doesn't contain
         * anything new that wasn't already contained in the last
         * bandwidth history object(s).  Unless we did not include any of
         * the previous bandwidth history objects for other reasons, in
         * which case we should include this one. */
        continue;
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
      for (int j = firstNonNullIndex; j <= lastNonNullIndex; j++) {
        long dataPoint = dataPoints.get(j);
        if (dataPoint >= 0L) {
          if (j - previousNonNullIndex == 1) {
            foundTwoAdjacentDataPoints = true;
          }
          previousNonNullIndex = j;
        }
        values.add(dataPoint < 0L ? null
            : (int) ((dataPoint * 999L) / maxValue));
      }
      graphHistory.setValues(values);
      if (foundTwoAdjacentDataPoints) {
        graphs.put(graphName, graphHistory);
      }
    }
    return graphs;
  }

  @Override
  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}

