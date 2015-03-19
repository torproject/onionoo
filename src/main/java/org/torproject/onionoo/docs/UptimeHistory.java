package org.torproject.onionoo.docs;

import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UptimeHistory implements Comparable<UptimeHistory> {

  private final static Logger log = LoggerFactory.getLogger(
      UptimeHistory.class);

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

  private SortedSet<String> flags;
  public SortedSet<String> getFlags() {
    return this.flags;
  }

  UptimeHistory(boolean relay, long startMillis,
      int uptimeHours, SortedSet<String> flags) {
    this.relay = relay;
    this.startMillis = startMillis;
    this.uptimeHours = uptimeHours;
    this.flags = flags;
  }

  public static UptimeHistory fromString(String uptimeHistoryString) {
    String[] parts = uptimeHistoryString.split(" ", -1);
    if (parts.length < 3) {
      log.warn("Invalid number of space-separated strings in uptime "
          + "history: '" + uptimeHistoryString + "'.  Skipping");
      return null;
    }
    boolean relay = false;
    if (parts[0].equalsIgnoreCase("r")) {
      relay = true;
    } else if (!parts[0].equals("b")) {
      log.warn("Invalid node type in uptime history: '"
          + uptimeHistoryString + "'.  Supported types are 'r', 'R', and "
          + "'b'.  Skipping.");
      return null;
    }
    long startMillis = DateTimeHelper.parse(parts[1],
          DateTimeHelper.DATEHOUR_NOSPACE_FORMAT);
    if (DateTimeHelper.NO_TIME_AVAILABLE == startMillis) {
      log.warn("Invalid start timestamp in uptime history: '"
          + uptimeHistoryString + "'.  Skipping.");
      return null;
    }
    int uptimeHours = -1;
    try {
      uptimeHours = Integer.parseInt(parts[2]);
    } catch (NumberFormatException e) {
      log.warn("Invalid number format in uptime history: '"
          + uptimeHistoryString + "'.  Skipping.");
      return null;
    }
    SortedSet<String> flags = null;
    if (parts[0].equals("R")) {
      flags = new TreeSet<String>();
      for (int i = 3; i < parts.length; i++) {
        flags.add(parts[i]);
      }
    }
    return new UptimeHistory(relay, startMillis, uptimeHours, flags);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.relay ? (this.flags == null ? "r" : "R") : "b");
    sb.append(" " + DateTimeHelper.format(this.startMillis,
        DateTimeHelper.DATEHOUR_NOSPACE_FORMAT));
    sb.append(" " + String.format("%d", this.uptimeHours));
    if (this.flags != null) {
      for (String flag : this.flags) {
        sb.append(" " + flag);
      }
    }
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