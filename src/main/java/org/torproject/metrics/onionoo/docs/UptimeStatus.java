/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NavigableSet;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

public class UptimeStatus extends Document {

  private static final Logger logger = LoggerFactory.getLogger(
      UptimeStatus.class);

  private transient boolean isDirty = false;

  public boolean isDirty() {
    return this.isDirty;
  }

  public void clearDirty() {
    this.isDirty = false;
  }

  private SortedSet<UptimeHistory> relayHistory = new TreeSet<>();

  public SortedSet<UptimeHistory> getRelayHistory() {
    return this.relayHistory;
  }

  private SortedSet<UptimeHistory> bridgeHistory = new TreeSet<>();

  public SortedSet<UptimeHistory> getBridgeHistory() {
    return this.bridgeHistory;
  }

  @Override
  public void setFromDocumentString(String documentString) {
    try (Scanner s = new Scanner(documentString)) {
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
          logger.error("Could not parse uptime history line '{}'. Skipping.",
              line);
        }
      }
    }
  }

  /** Adds all given uptime history objects that don't overlap with
   * existing uptime history objects. */
  public void addToHistory(boolean relay, long startMillis,
      SortedSet<String> flags) {
    SortedSet<UptimeHistory> history = relay ? this.relayHistory
        : this.bridgeHistory;
    UptimeHistory interval = new UptimeHistory(relay, startMillis, 1,
        flags);
    NavigableSet<UptimeHistory> existingIntervals =
        new TreeSet<>(history.headSet(new UptimeHistory(
        relay, startMillis + DateTimeHelper.ONE_HOUR, 0, flags)));
    for (UptimeHistory prev : existingIntervals.descendingSet()) {
      if (prev.isRelay() != interval.isRelay()
          || prev.getStartMillis() + DateTimeHelper.ONE_HOUR
          * prev.getUptimeHours() <= interval.getStartMillis()) {
        break;
      }
      if (prev.getFlags() == interval.getFlags()
          || (prev.getFlags() != null && interval.getFlags() != null
          && prev.getFlags().equals(interval.getFlags()))) {
        /* The exact same interval is already contained in history. */
        return;
      }
      /* There is an interval that includes the new interval, but it
       * contains different flags.  Remove the old interval, put in any
       * parts before or after the new interval, and add the new interval
       * further down below. */
      history.remove(prev);
      int hoursBefore = (int) ((interval.getStartMillis()
          - prev.getStartMillis()) / DateTimeHelper.ONE_HOUR);
      if (hoursBefore > 0) {
        history.add(new UptimeHistory(relay,
            prev.getStartMillis(), hoursBefore, prev.getFlags()));
      }
      int hoursAfter = (int) (prev.getStartMillis()
          / DateTimeHelper.ONE_HOUR + prev.getUptimeHours()
          - interval.getStartMillis() / DateTimeHelper.ONE_HOUR - 1);
      if (hoursAfter > 0) {
        history.add(new UptimeHistory(relay,
            interval.getStartMillis() + DateTimeHelper.ONE_HOUR,
            hoursAfter, prev.getFlags()));
      }
      break;
    }
    history.add(interval);
    this.isDirty = true;
  }

  /** Compresses the history of uptime objects by merging adjacent
   * intervals. */
  public void compressHistory() {
    this.compressHistory(this.relayHistory);
    this.compressHistory(this.bridgeHistory);
  }

  private void compressHistory(SortedSet<UptimeHistory> history) {
    SortedSet<UptimeHistory> uncompressedHistory = new TreeSet<>(history);
    history.clear();
    UptimeHistory lastInterval = null;
    for (UptimeHistory interval : uncompressedHistory) {
      if (lastInterval != null
          && lastInterval.getStartMillis() + DateTimeHelper.ONE_HOUR
          * lastInterval.getUptimeHours() == interval.getStartMillis()
          && lastInterval.isRelay() == interval.isRelay()
          && (lastInterval.getFlags() == interval.getFlags()
          || (lastInterval.getFlags() != null
          && interval.getFlags() != null
          && lastInterval.getFlags().equals(interval.getFlags())))) {
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

  @Override
  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (UptimeHistory interval : this.relayHistory) {
      sb.append(interval.toString()).append("\n");
    }
    for (UptimeHistory interval : this.bridgeHistory) {
      sb.append(interval.toString()).append("\n");
    }
    return sb.toString();
  }
}

