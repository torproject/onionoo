/* Copyright 2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.GraphHistory;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helper class to compile graph histories. */
public class GraphHistoryCompiler {

  private long graphsEndMillis;

  /**
   * Instantiates a new graph history compiler with the provided end time for
   * all compiled graphs.
   *
   * @param graphsEndMillis End time for all compiled graphs.
   */
  GraphHistoryCompiler(long graphsEndMillis) {
    this.graphsEndMillis = graphsEndMillis;
  }

  private boolean divisible = false;

  /**
   * Set whether history elements are divisible in the sense that they may be
   * longer than one data point; this is the case for uptime intervals where
   * uptime is equally distributed over potentially many data point intervals,
   * but it's not the case for bandwidth/weights/clients intervals where
   * observations are given for fixed-size reporting intervals. */
  void setDivisible(boolean divisible) {
    this.divisible = divisible;
  }

  private long threshold = 5;

  /**
   * Set the threshold (given as reciprocal value) of available history entries
   * for any given data points below which the data point will be counted as
   * null (missing); default is 5 for 1/5 = 20%.
   */
  void setThreshold(long threshold) {
    this.threshold = threshold;
  }

  private List<String> graphNames = new ArrayList<>();

  private List<Period> graphIntervals = new ArrayList<>();

  private List<Long> dataPointIntervals = new ArrayList<>();

  /**
   * Add a graph type with the given graph name, graph interval, and data point
   * interval.
   *
   * @param graphName Graph name, like "1_week".
   * @param graphInterval Graph interval, like Period.ofWeeks(1).
   * @param dataPointInterval Data point interval, like 1 hour in milliseconds.
   */
  void addGraphType(String graphName, Period graphInterval,
      Long dataPointInterval) {
    this.graphNames.add(graphName);
    this.graphIntervals.add(graphInterval);
    this.dataPointIntervals.add(dataPointInterval);
  }

  private Map<long[], Double> history = new LinkedHashMap<>();

  /**
   * Add a history entry with given start and end time and value.
   *
   * @param startMillis Start time in milliseconds.
   * @param endMillis End time in milliseconds.
   * @param value History entry value.
   */
  void addHistoryEntry(long startMillis, long endMillis, double value) {
    this.history.put(new long[] { startMillis, endMillis }, value);
  }

  /**
   * Compile graph histories from the history entries provided earlier.
   *
   * @return Map with graph names as keys and GraphHistory instances as values.
   */
  Map<String, GraphHistory> compileGraphHistories() {
    Map<String, GraphHistory> graphs = new LinkedHashMap<>();
    for (int graphIntervalIndex = 0;
         graphIntervalIndex < this.graphIntervals.size();
         graphIntervalIndex++) {

      /* Look up graph name, graph interval, and data point interval from the
       * graph type details provided earlier. */
      final String graphName = this.graphNames.get(graphIntervalIndex);
      Period graphInterval = this.graphIntervals.get(graphIntervalIndex);
      long dataPointInterval = this.dataPointIntervals.get(graphIntervalIndex);

      /* Determine graph end time as the end time for all graphs, rounded down
       * to the last full data point interval. */
      long graphEndMillis = (this.graphsEndMillis / dataPointInterval)
          * dataPointInterval;

      /* Determine graph start time as graph end time minus graph interval,
       * rounded down to the last full data point interval. */
      long graphStartMillis = ((LocalDateTime
          .ofEpochSecond(graphEndMillis / 1000L, 0, ZoneOffset.UTC)
          .minus(graphInterval)
          .toEpochSecond(ZoneOffset.UTC) * 1000L) / dataPointInterval)
          * dataPointInterval;

      /* Keep input for graph values in two arrays, one for values * millis,
       * another one for millis. */
      int dataPoints = (int) ((graphEndMillis - graphStartMillis)
          / dataPointInterval);
      double[] totalValues = new double[dataPoints];
      long[] totalMillis = new long[dataPoints];

      /* Iterate over all history entries and see which ones we need for this
       * graph. */
      for (Map.Entry<long[], Double> h : this.history.entrySet()) {
        long startMillis = h.getKey()[0];
        long endMillis = h.getKey()[1];
        double value = h.getValue();

        /* If a history entry ends before this graph starts or starts before
         * this graph ends, skip it. */
        if (endMillis <= graphStartMillis || startMillis >= graphEndMillis) {
          continue;
        }

        /* If history entries are not divisible and this entry is longer than
         * the data point interval, skip it. Maybe the next graph will contain
         * it, but not this one. */
        if (!this.divisible && endMillis - startMillis > dataPointInterval) {
          continue;
        }

        /* Iterate over all data points that this history element falls into.
         * Even if history entries are not divisible, we may have to split it
         * over two data points, because reported statistics rarely align with
         * our data point intervals. And if history entries are divisible, we
         * may have to split them over many data points. */
        for (long intervalStartMillis = startMillis;
            intervalStartMillis < endMillis;
            intervalStartMillis = ((intervalStartMillis + dataPointInterval)
            / dataPointInterval) * dataPointInterval) {

          /* Determine the data point that this (partial) history entry falls
           * into. And if it's out of bounds, skip it. */
          int dataPointIndex = (int) ((intervalStartMillis - graphStartMillis)
              / dataPointInterval);
          if (dataPointIndex < 0 || dataPointIndex >= dataPoints) {
            continue;
          }

          /* Determine the interval end, which may be the end of the data point
           * or the end of the history entry, whichever comes first. Then add
           * values and millis to the data point. */
          long intervalEndMillis = Math.min(endMillis, ((intervalStartMillis
              + dataPointInterval) / dataPointInterval) * dataPointInterval);
          long millis = intervalEndMillis - intervalStartMillis;
          totalValues[dataPointIndex] += (value * (double) millis)
              / (double) (endMillis - startMillis);
          totalMillis[dataPointIndex] += millis;
        }
      }

      /* Go through the previously compiled data points and extract some pieces
       * that will be relevant for deciding whether to include this graph and
       * for adding meta data to the GraphHistory object. */
      double maxValue = 0.0;
      int firstNonNullIndex = -1;
      int lastNonNullIndex = -1;
      boolean foundTwoAdjacentDataPoints = false;
      for (int dataPointIndex = 0, previousNonNullIndex = -2;
           dataPointIndex < dataPoints; dataPointIndex++) {

        /* Only consider data points containing values for at least the given
         * threshold of time (20% by default). If so, record first and last
         * data point containing data, whether there exist two adjacent data
         * points containing data, and determine the maximum value. */
        if (totalMillis[dataPointIndex] * this.threshold >= dataPointInterval) {
          if (firstNonNullIndex < 0) {
            firstNonNullIndex = dataPointIndex;
          }
          lastNonNullIndex = dataPointIndex;
          if (dataPointIndex - previousNonNullIndex == 1) {
            foundTwoAdjacentDataPoints = true;
          }
          previousNonNullIndex = dataPointIndex;
          maxValue = Math.max(maxValue, totalValues[dataPointIndex]
              / totalMillis[dataPointIndex]);
        }
      }

      /* If there are not at least two adjacent data points containing data,
       * skip the graph. */
      if (!foundTwoAdjacentDataPoints) {
        continue;
      }

      /* Calculate the timestamp of the first data point containing data. */
      long firstDataPointMillis = graphStartMillis + firstNonNullIndex
          * dataPointInterval + dataPointInterval / 2L;

      /* If the graph doesn't contain anything new that wasn't already contained
       * in previously compiled graphs, skip this graph. */
      if (graphIntervalIndex > 0 && !graphs.isEmpty()
          && firstDataPointMillis >= LocalDateTime.ofEpochSecond(
          graphEndMillis / 1000L, 0, ZoneOffset.UTC)
          .minus(this.graphIntervals.get(graphIntervalIndex - 1))
          .toEpochSecond(ZoneOffset.UTC) * 1000L) {
        continue;
      }

      /* Put together the list of values that will go into the graph. */
      List<Integer> values = new ArrayList<>();
      for (int dataPointIndex = firstNonNullIndex;
           dataPointIndex <= lastNonNullIndex; dataPointIndex++) {
        if (totalMillis[dataPointIndex] * this.threshold >= dataPointInterval) {
          values.add((int) ((totalValues[dataPointIndex] * 999.0)
              / (maxValue * totalMillis[dataPointIndex])));
        } else {
          values.add(null);
        }
      }

      /* Put together a GraphHistory object and add it to the map under the
       * given graph name. */
      GraphHistory graphHistory = new GraphHistory();
      graphHistory.setFirst(firstDataPointMillis);
      graphHistory.setLast(firstDataPointMillis + (lastNonNullIndex
          - firstNonNullIndex) * dataPointInterval);
      graphHistory.setInterval((int) (dataPointInterval
          / DateTimeHelper.ONE_SECOND));
      graphHistory.setFactor(maxValue / 999.0);
      graphHistory.setCount(lastNonNullIndex - firstNonNullIndex + 1);
      graphHistory.setValues(values);
      graphs.put(graphName, graphHistory);
    }

    /* We're done. Return the map of compiled graphs. */
    return graphs;
  }
}

