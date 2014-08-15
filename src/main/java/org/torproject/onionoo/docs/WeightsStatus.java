package org.torproject.onionoo.docs;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

import org.torproject.onionoo.util.TimeFactory;

public class WeightsStatus extends Document {

  private transient boolean isDirty = false;
  public boolean isDirty() {
    return this.isDirty;
  }
  public void clearDirty() {
    this.isDirty = false;
  }

  private SortedMap<long[], double[]> history =
      new TreeMap<long[], double[]>(new Comparator<long[]>() {
    public int compare(long[] a, long[] b) {
      return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0;
    }
  });
  public void setHistory(SortedMap<long[], double[]> history) {
    this.history = history;
  }
  public SortedMap<long[], double[]> getHistory() {
    return this.history;
  }

  private Map<String, Integer> advertisedBandwidths =
      new HashMap<String, Integer>();
  public Map<String, Integer> getAdvertisedBandwidths() {
    return this.advertisedBandwidths;
  }

  public void fromDocumentString(String documentString) {
    Scanner s = new Scanner(documentString);
    while (s.hasNextLine()) {
      String line = s.nextLine();
      String[] parts = line.split(" ");
      if (parts.length == 2) {
        String descriptorDigest = parts[0];
        int advertisedBandwidth = Integer.parseInt(parts[1]);
        this.advertisedBandwidths.put(descriptorDigest,
            advertisedBandwidth);
        continue;
      }
      if (parts.length != 9 && parts.length != 11) {
        System.err.println("Illegal line '" + line + "' in weights "
              + "status file.  Skipping this line.");
        continue;
      }
      if (parts[4].equals("NaN")) {
        /* Remove corrupt lines written on 2013-07-07 and the days
         * after. */
        continue;
      }
      long validAfterMillis = DateTimeHelper.parse(parts[0] + " "
          + parts[1]);
      long freshUntilMillis = DateTimeHelper.parse(parts[2] + " "
          + parts[3]);
      if (validAfterMillis < 0L || freshUntilMillis < 0L) {
        System.err.println("Could not parse timestamp while reading "
            + "weights status file.  Skipping.");
        break;
      }
      long[] interval = new long[] { validAfterMillis, freshUntilMillis };
      double[] weights = new double[] {
          Double.parseDouble(parts[4]),
          Double.parseDouble(parts[5]),
          Double.parseDouble(parts[6]),
          Double.parseDouble(parts[7]),
          Double.parseDouble(parts[8]), -1.0, -1.0 };
      if (parts.length == 11) {
        weights[5] = Double.parseDouble(parts[9]);
        weights[6] = Double.parseDouble(parts[10]);
      }
      this.history.put(interval, weights);
    }
    s.close();
  }

  public void addToHistory(long validAfterMillis, long freshUntilMillis,
      double[] weights) {
    long[] interval = new long[] { validAfterMillis, freshUntilMillis };
    if ((this.history.headMap(interval).isEmpty() ||
        this.history.headMap(interval).lastKey()[1] <=
        validAfterMillis) &&
        (this.history.tailMap(interval).isEmpty() ||
        this.history.tailMap(interval).firstKey()[0] >=
        freshUntilMillis)) {
      this.history.put(interval, weights);
      this.isDirty = true;
    }
  }

  public void compressHistory() {
    SortedMap<long[], double[]> uncompressedHistory =
        new TreeMap<long[], double[]>(this.history);
    history.clear();
    long lastStartMillis = 0L, lastEndMillis = 0L;
    double[] lastWeights = null;
    String lastMonthString = "1970-01";
    int lastMissingValues = -1;
    long now = TimeFactory.getTime().currentTimeMillis();
    for (Map.Entry<long[], double[]> e : uncompressedHistory.entrySet()) {
      long startMillis = e.getKey()[0], endMillis = e.getKey()[1];
      double[] weights = e.getValue();
      long intervalLengthMillis;
      if (now - endMillis <= DateTimeHelper.ONE_WEEK) {
        intervalLengthMillis = DateTimeHelper.ONE_HOUR;
      } else if (now - endMillis <=
          DateTimeHelper.ROUGHLY_ONE_MONTH) {
        intervalLengthMillis = DateTimeHelper.FOUR_HOURS;
      } else if (now - endMillis <=
          DateTimeHelper.ROUGHLY_THREE_MONTHS) {
        intervalLengthMillis = DateTimeHelper.TWELVE_HOURS;
      } else if (now - endMillis <=
          DateTimeHelper.ROUGHLY_ONE_YEAR) {
        intervalLengthMillis = DateTimeHelper.TWO_DAYS;
      } else {
        intervalLengthMillis = DateTimeHelper.TEN_DAYS;
      }
      String monthString = DateTimeHelper.format(startMillis,
          DateTimeHelper.ISO_YEARMONTH_FORMAT);
      int missingValues = 0;
      for (int i = 0; i < weights.length; i++) {
        if (weights[i] < -0.5) {
          missingValues += 1 << i;
        }
      }
      if (lastEndMillis == startMillis &&
          ((lastEndMillis - 1L) / intervalLengthMillis) ==
          ((endMillis - 1L) / intervalLengthMillis) &&
          lastMonthString.equals(monthString) &&
          lastMissingValues == missingValues) {
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

  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Integer> e :
        this.advertisedBandwidths.entrySet()) {
      sb.append(e.getKey() + " " + String.valueOf(e.getValue()) + "\n");
    }
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long[] fresh = e.getKey();
      double[] weights = e.getValue();
      sb.append(DateTimeHelper.format(fresh[0]) + " "
          + DateTimeHelper.format(fresh[1]));
      for (double weight : weights) {
        sb.append(String.format(" %.12f", weight));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}

