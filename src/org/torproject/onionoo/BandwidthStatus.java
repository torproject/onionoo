/* Copyright 2013--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;

class BandwidthStatus extends Document {

  SortedMap<Long, long[]> writeHistory = new TreeMap<Long, long[]>();

  SortedMap<Long, long[]> readHistory = new TreeMap<Long, long[]>();

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
        if (parts.length != 6) {
          System.err.println("Illegal line '" + line + "' in bandwidth "
              + "history.  Skipping this line.");
          continue;
        }
        SortedMap<Long, long[]> history = parts[0].equals("r")
            ? readHistory : writeHistory;
        long startMillis = dateTimeFormat.parse(parts[1] + " "
            + parts[2]).getTime();
        long endMillis = dateTimeFormat.parse(parts[3] + " "
            + parts[4]).getTime();
        long bandwidth = Long.parseLong(parts[5]);
        long previousEndMillis = history.headMap(startMillis).isEmpty()
            ? startMillis
            : history.get(history.headMap(startMillis).lastKey())[1];
        long nextStartMillis = history.tailMap(startMillis).isEmpty()
            ? endMillis : history.tailMap(startMillis).firstKey();
        if (previousEndMillis <= startMillis &&
            nextStartMillis >= endMillis) {
          history.put(startMillis, new long[] { startMillis, endMillis,
              bandwidth });
        }
      }
      s.close();
    } catch (ParseException e) {
      System.err.println("Could not parse timestamp while reading "
          + "bandwidth history.  Skipping.");
      e.printStackTrace();
    }

  }

  public String toDocumentString() {
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    StringBuilder sb = new StringBuilder();
    for (long[] v : writeHistory.values()) {
      sb.append("w " + dateTimeFormat.format(v[0]) + " "
          + dateTimeFormat.format(v[1]) + " "
          + String.valueOf(v[2]) + "\n");
    }
    for (long[] v : readHistory.values()) {
      sb.append("r " + dateTimeFormat.format(v[0]) + " "
          + dateTimeFormat.format(v[1]) + " "
          + String.valueOf(v[2]) + "\n");
    }
    return sb.toString();
  }
}

