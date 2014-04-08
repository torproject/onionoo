/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

class UptimeHistory
    implements Comparable<UptimeHistory> {

  private boolean relay;
  public boolean isRelay() {
    return this.relay;
  }

  private long startMillis;
  public long getStartMillis() {
    return this.startMillis;
  }

  private int uptimeHours;
  public int getUptimeHours() {
    return this.uptimeHours;
  }

  UptimeHistory(boolean relay, long startMillis,
      int uptimeHours) {
    this.relay = relay;
    this.startMillis = startMillis;
    this.uptimeHours = uptimeHours;
  }

  public static UptimeHistory fromString(String uptimeHistoryString) {
    String[] parts = uptimeHistoryString.split(" ", 3);
    if (parts.length != 3) {
      return null;
    }
    boolean relay = false;
    if (parts[0].equals("r")) {
      relay = true;
    } else if (!parts[0].equals("b")) {
      return null;
    }
    long startMillis = DateTimeHelper.parse(parts[1],
          DateTimeHelper.YEARHOUR_NOSPACE_FORMAT);
    if (startMillis < 0L) {
      return null;
    }
    int uptimeHours = -1;
    try {
      uptimeHours = Integer.parseInt(parts[2]);
    } catch (NumberFormatException e) {
      return null;
    }
    return new UptimeHistory(relay, startMillis, uptimeHours);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.relay ? "r" : "b");
    sb.append(" " + DateTimeHelper.format(this.startMillis,
        DateTimeHelper.YEARHOUR_NOSPACE_FORMAT));
    sb.append(" " + String.format("%d", this.uptimeHours));
    return sb.toString();
  }

  public void addUptime(UptimeHistory other) {
    this.uptimeHours += other.uptimeHours;
    if (this.startMillis > other.startMillis) {
      this.startMillis = other.startMillis;
    }
  }

  public int compareTo(UptimeHistory other) {
    if (this.relay && !other.relay) {
      return -1;
    } else if (!this.relay && other.relay) {
      return 1;
    }
    return this.startMillis < other.startMillis ? -1 :
        this.startMillis > other.startMillis ? 1 : 0;
  }

  public boolean equals(Object other) {
    return other instanceof UptimeHistory &&
        this.relay == ((UptimeHistory) other).relay &&
        this.startMillis == ((UptimeHistory) other).startMillis;
  }

  public int hashCode() {
    return (int) this.startMillis + (this.relay ? 1 : 0);
  }
}

class UptimeStatus extends Document {

  private SortedSet<UptimeHistory> history = new TreeSet<UptimeHistory>();
  public void setHistory(SortedSet<UptimeHistory> history) {
    this.history = history;
  }
  public SortedSet<UptimeHistory> getHistory() {
    return this.history;
  }

  public void fromDocumentString(String documentString) {
    Scanner s = new Scanner(documentString);
    while (s.hasNextLine()) {
      String line = s.nextLine();
      UptimeHistory parsedLine = UptimeHistory.fromString(line);
      if (parsedLine != null) {
        this.history.add(parsedLine);
      } else {
        System.err.println("Could not parse uptime history line '"
            + line + "'.  Skipping.");
      }
    }
    s.close();
  }

  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (UptimeHistory interval : this.history) {
      sb.append(interval.toString() + "\n");
    }
    return sb.toString();
  }
}

