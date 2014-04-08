package org.torproject.onionoo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

class WeightsStatus extends Document {

  SortedMap<long[], double[]> history = new TreeMap<long[], double[]>(
      new Comparator<long[]>() {
    public int compare(long[] a, long[] b) {
      return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0;
    }
  });

  Map<String, Integer> advertisedBandwidths =
      new HashMap<String, Integer>();

  public void fromDocumentString(String documentString) {
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
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
        long validAfterMillis = dateTimeFormat.parse(parts[0]
            + " " + parts[1]).getTime();
        long freshUntilMillis = dateTimeFormat.parse(parts[2]
            + " " + parts[3]).getTime();
        long[] interval = new long[] { validAfterMillis,
            freshUntilMillis };
        double[] weights = new double[] {
            Double.parseDouble(parts[4]),
            Double.parseDouble(parts[5]),
            Double.parseDouble(parts[6]),
            Double.parseDouble(parts[7]),
            Double.parseDouble(parts[8]) };
        this.history.put(interval, weights);
      }
      s.close();
    } catch (ParseException e) {
      System.err.println("Could not parse timestamp while reading "
          + "weights status file.  Skipping.");
      e.printStackTrace();
    }
  }

  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Integer> e :
        this.advertisedBandwidths.entrySet()) {
      sb.append(e.getKey() + " " + String.valueOf(e.getValue()) + "\n");
    }
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long[] fresh = e.getKey();
      double[] weights = e.getValue();
      sb.append(dateTimeFormat.format(fresh[0]) + " "
          + dateTimeFormat.format(fresh[1]));
      for (double weight : weights) {
        sb.append(String.format(" %.12f", weight));
      }
      sb.append("\n");
    }
    return sb.toString();
  }
}

