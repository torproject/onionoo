/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

public class UptimeStatus extends Document {

  private transient boolean isDirty = false;
  public boolean isDirty() {
    return this.isDirty;
  }
  public void clearDirty() {
    this.isDirty = false;
  }

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

  public void compressHistory() {
    this.compressHistory(this.relayHistory);
    this.compressHistory(this.bridgeHistory);
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

