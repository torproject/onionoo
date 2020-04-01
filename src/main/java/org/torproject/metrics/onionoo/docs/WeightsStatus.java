/* Copyright 2016--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

public class WeightsStatus extends Document {

  private static final Logger logger = LoggerFactory.getLogger(
      WeightsStatus.class);

  private transient boolean isDirty = false;

  public boolean isDirty() {
    return this.isDirty;
  }

  public void clearDirty() {
    this.isDirty = false;
  }

  private Comparator<long[]> histComparator = (first, second) -> {
    int relation = Long.compare(first[0], second[0]);
    if (0 != relation) {
      return relation;
    } else {
      return Long.compare(first[1], second[1]);
    }
  };

  private SortedMap<long[], double[]> history = new TreeMap<>(histComparator);

  public void setHistory(SortedMap<long[], double[]> history) {
    this.history = history;
  }

  public SortedMap<long[], double[]> getHistory() {
    return this.history;
  }

  @Override
  public void setFromDocumentString(String documentString) {
    try (Scanner s = new Scanner(documentString)) {
      while (s.hasNextLine()) {
        String line = s.nextLine();
        String[] parts = line.split(" ", 11);
        if (parts.length == 2) {
          /* Skip lines containing descriptor digest and advertised
           * bandwidth. */
          continue;
        }
        if (parts.length != 9 && parts.length != 11) {
          logger.error("Illegal line '{}' in weights status file. Skipping "
              + "this line.", line);
          continue;
        }
        if (parts[4].equals("NaN")) {
          /* Remove corrupt lines written on 2013-07-07 and the days
           * after. */
          continue;
        }
        long validAfterMillis = DateTimeHelper.parse(parts[0] + " " + parts[1]);
        long freshUntilMillis = DateTimeHelper.parse(parts[2] + " " + parts[3]);
        if (validAfterMillis < 0L || freshUntilMillis < 0L) {
          logger.error("Could not parse timestamp while reading "
              + "weights status file.  Skipping.");
          break;
        }
        if (validAfterMillis > freshUntilMillis) {
          logger.error("Illegal dates in '{}' of weights status file. "
              + "Skipping.", line);
          break;
        }
        long[] interval = new long[] { validAfterMillis, freshUntilMillis };
        double[] weights;
        try {
          weights = new double[] { -1.0,
              parseWeightDouble(parts[5]),
              parseWeightDouble(parts[6]),
              parseWeightDouble(parts[7]),
              parseWeightDouble(parts[8]), -1.0, -1.0 };
          if (parts.length == 11) {
            weights[6] = parseWeightDouble(parts[10]);
          }
        } catch (NumberFormatException e) {
          logger.error("Could not parse weights values in line '{}' while "
              + "reading weights status file. Skipping.", line);
          break;
        }
        this.history.put(interval, weights);
      }
    }
  }

  private double parseWeightDouble(String in) throws NumberFormatException {
    return in.isEmpty() || in.startsWith("-")
        ? Double.NaN : Double.parseDouble(in);
  }

  /** Adds all given weights history objects that don't overlap with
   * existing weights history objects. */
  public void addToHistory(long validAfterMillis, long freshUntilMillis,
      double[] weights) {
    long[] interval = new long[] { validAfterMillis, freshUntilMillis };
    if ((this.history.headMap(interval).isEmpty()
        || this.history.headMap(interval).lastKey()[1]
        <= validAfterMillis)
        && (this.history.tailMap(interval).isEmpty()
        || this.history.tailMap(interval).firstKey()[0]
        >= freshUntilMillis)) {
      this.history.put(interval, weights);
      this.isDirty = true;
    }
  }

  /** Compresses the history of weights objects by merging adjacent
   * intervals, depending on how far back in the past they lie. */
  public void compressHistory(long lastSeenMillis) {
    SortedMap<long[], double[]> uncompressedHistory =
        new TreeMap<>(histComparator);
    uncompressedHistory.putAll(this.history);
    history.clear();
    long lastStartMillis = 0L;
    long lastEndMillis = 0L;
    double[] lastWeights = null;
    String lastMonthString = "1970-01";
    int lastMissingValues = -1;
    for (Map.Entry<long[], double[]> e : uncompressedHistory.entrySet()) {
      long startMillis = e.getKey()[0];
      long endMillis = e.getKey()[1];
      double[] weights = e.getValue();
      long intervalLengthMillis;
      if (lastSeenMillis - endMillis <= DateTimeHelper.ONE_WEEK) {
        intervalLengthMillis = DateTimeHelper.ONE_HOUR;
      } else if (lastSeenMillis - endMillis
          <= DateTimeHelper.ROUGHLY_ONE_MONTH) {
        intervalLengthMillis = DateTimeHelper.FOUR_HOURS;
      } else if (lastSeenMillis - endMillis
          <= DateTimeHelper.ROUGHLY_SIX_MONTHS) {
        intervalLengthMillis = DateTimeHelper.ONE_DAY;
      } else if (lastSeenMillis - endMillis
          <= DateTimeHelper.ROUGHLY_ONE_YEAR) {
        intervalLengthMillis = DateTimeHelper.TWO_DAYS;
      } else {
        intervalLengthMillis = DateTimeHelper.TEN_DAYS;
      }
      String monthString = DateTimeHelper.format(startMillis,
          DateTimeHelper.ISO_YEARMONTH_FORMAT);
      int missingValues = 0;
      for (int i = 0; i < weights.length; i++) {
        if (Double.valueOf(weights[i]).isNaN()) {
          missingValues += 1 << i;
        }
      }
      if (lastEndMillis == startMillis
          && ((lastEndMillis - 1L) / intervalLengthMillis)
          == ((endMillis - 1L) / intervalLengthMillis)
          && lastMonthString.equals(monthString)
          && lastMissingValues == missingValues) {
        double lastIntervalInHours = (double) ((lastEndMillis
            - lastStartMillis) / DateTimeHelper.ONE_HOUR);
        double currentIntervalInHours = (double) ((endMillis
            - startMillis) / DateTimeHelper.ONE_HOUR);
        double newIntervalInHours = (double) ((endMillis
            - lastStartMillis) / DateTimeHelper.ONE_HOUR);
        for (int i = 0; i < lastWeights.length; i++) {
          lastWeights[i] *= lastIntervalInHours;
          lastWeights[i] += weights[i] * currentIntervalInHours;
          lastWeights[i] /= newIntervalInHours;
        }
        lastEndMillis = endMillis;
      } else {
        if (lastStartMillis > 0L) {
          this.history.put(new long[] { lastStartMillis, lastEndMillis },
              lastWeights);
        }
        lastStartMillis = startMillis;
        lastEndMillis = endMillis;
        lastWeights = weights;
      }
      lastMonthString = monthString;
      lastMissingValues = missingValues;
    }
    if (lastStartMillis > 0L) {
      this.history.put(new long[] { lastStartMillis, lastEndMillis },
          lastWeights);
    }
  }

  @Override
  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long[] fresh = e.getKey();
      double[] weights = e.getValue();
      sb.append(DateTimeHelper.format(fresh[0])).append(" ")
          .append(DateTimeHelper.format(fresh[1]));
      for (int i = 0; i < weights.length; i++) {
        sb.append(" ");
        if (i != 0 && i != 5 && !Double.valueOf(weights[i]).isNaN()) {
          sb.append(String.format("%.12f", weights[i]));
        }
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}

