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
          DateTimeHelper.DATEHOUR_NOSPACE_FORMAT);
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
        DateTimeHelper.DATEHOUR_NOSPACE_FORMAT));
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

public class UptimeStatus extends Document {

  private transient String fingerprint;

  private transient boolean isDirty = false;

  private SortedSet<UptimeHistory> relayHistory =
      new TreeSet<UptimeHistory>();
  public void setRelayHistory(SortedSet<UptimeHistory> relayHistory) {
    this.relayHistory = relayHistory;
  }
  public SortedSet<UptimeHistory> getRelayHistory() {
    return this.relayHistory;
  }

  private SortedSet<UptimeHistory> bridgeHistory =
      new TreeSet<UptimeHistory>();
  public void setBridgeHistory(SortedSet<UptimeHistory> bridgeHistory) {
    this.bridgeHistory = bridgeHistory;
  }
  public SortedSet<UptimeHistory> getBridgeHistory() {
    return this.bridgeHistory;
  }

  public static UptimeStatus loadOrCreate(String fingerprint) {
    UptimeStatus uptimeStatus = (fingerprint == null) ?
        ApplicationFactory.getDocumentStore().retrieve(
            UptimeStatus.class, true) :
        ApplicationFactory.getDocumentStore().retrieve(
            UptimeStatus.class, true, fingerprint);
    if (uptimeStatus == null) {
      uptimeStatus = new UptimeStatus();
    }
    uptimeStatus.fingerprint = fingerprint;
    return uptimeStatus;
  }

  public void fromDocumentString(String documentString) {
    Scanner s = new Scanner(documentString);
    while (s.hasNextLine()) {
      String line = s.nextLine();
      UptimeHistory parsedLine = UptimeHistory.fromString(line);
      if (parsedLine != null) {
        if (parsedLine.isRelay()) {
          this.relayHistory.add(parsedLine);
        } else {
          this.bridgeHistory.add(parsedLine);
        }
      } else {
        System.err.println("Could not parse uptime history line '"
            + line + "'.  Skipping.");
      }
    }
    s.close();
  }

  public void addToHistory(boolean relay, SortedSet<Long> newIntervals) {
    for (long startMillis : newIntervals) {
      SortedSet<UptimeHistory> history = relay ? this.relayHistory
          : this.bridgeHistory;
      UptimeHistory interval = new UptimeHistory(relay, startMillis, 1);
      if (!history.headSet(interval).isEmpty()) {
        UptimeHistory prev = history.headSet(interval).last();
        if (prev.isRelay() == interval.isRelay() &&
            prev.getStartMillis() + DateTimeHelper.ONE_HOUR
            * prev.getUptimeHours() > interval.getStartMillis()) {
          continue;
        }
      }
      if (!history.tailSet(interval).isEmpty()) {
        UptimeHistory next = history.tailSet(interval).first();
        if (next.isRelay() == interval.isRelay() &&
            next.getStartMillis() < interval.getStartMillis()
            + DateTimeHelper.ONE_HOUR) {
          continue;
        }
      }
      history.add(interval);
      this.isDirty = true;
    }
  }

  public void storeIfChanged() {
    if (this.isDirty) {
      this.compressHistory(this.relayHistory);
      this.compressHistory(this.bridgeHistory);
      if (fingerprint == null) {
        ApplicationFactory.getDocumentStore().store(this);
      } else {
        ApplicationFactory.getDocumentStore().store(this,
            this.fingerprint);
      }
      this.isDirty = false;
    }
  }

  private void compressHistory(SortedSet<UptimeHistory> history) {
    SortedSet<UptimeHistory> uncompressedHistory =
        new TreeSet<UptimeHistory>(history);
    history.clear();
    UptimeHistory lastInterval = null;
    for (UptimeHistory interval : uncompressedHistory) {
      if (lastInterval != null &&
          lastInterval.getStartMillis() + DateTimeHelper.ONE_HOUR
          * lastInterval.getUptimeHours() == interval.getStartMillis() &&
          lastInterval.isRelay() == interval.isRelay()) {
        lastInterval.addUptime(interval);
      } else {
        if (lastInterval != null) {
          history.add(lastInterval);
        }
        lastInterval = interval;
      }
    }
    if (lastInterval != null) {
      history.add(lastInterval);
    }
  }

  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (UptimeHistory interval : this.relayHistory) {
      sb.append(interval.toString() + "\n");
    }
    for (UptimeHistory interval : this.bridgeHistory) {
      sb.append(interval.toString() + "\n");
    }
    return sb.toString();
  }
}

