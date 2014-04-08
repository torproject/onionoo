package org.torproject.onionoo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

class WeightsStatus extends Document {

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
      if (parts.length != 9) {
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
          Double.parseDouble(parts[8]) };
      this.history.put(interval, weights);
    }
    s.close();
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

