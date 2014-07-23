/* Copyright 2013--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

public class BandwidthStatus extends Document {

  private SortedMap<Long, long[]> writeHistory =
      new TreeMap<Long, long[]>();
  public void setWriteHistory(SortedMap<Long, long[]> writeHistory) {
    this.writeHistory = writeHistory;
  }
  public SortedMap<Long, long[]> getWriteHistory() {
    return this.writeHistory;
  }

  private SortedMap<Long, long[]> readHistory =
      new TreeMap<Long, long[]>();
  public void setReadHistory(SortedMap<Long, long[]> readHistory) {
    this.readHistory = readHistory;
  }
  public SortedMap<Long, long[]> getReadHistory() {
    return this.readHistory;
  }

  public void fromDocumentString(String documentString) {
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
      long startMillis = DateTimeHelper.parse(parts[1] + " " + parts[2]);
      long endMillis = DateTimeHelper.parse(parts[3] + " " + parts[4]);
      if (startMillis < 0L || endMillis < 0L) {
        System.err.println("Could not parse timestamp while reading "
            + "bandwidth history.  Skipping.");
        break;
      }
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
  }

  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (long[] v : writeHistory.values()) {
      sb.append("w " + DateTimeHelper.format(v[0]) + " "
          + DateTimeHelper.format(v[1]) + " " + String.valueOf(v[2])
          + "\n");
    }
    for (long[] v : readHistory.values()) {
      sb.append("r " + DateTimeHelper.format(v[0]) + " "
          + DateTimeHelper.format(v[1]) + " " + String.valueOf(v[2])
          + "\n");
    }
    return sb.toString();
  }
}

