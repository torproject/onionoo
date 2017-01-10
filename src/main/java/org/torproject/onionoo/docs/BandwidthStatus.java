/* Copyright 2013--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import org.torproject.descriptor.BandwidthHistory;
import org.torproject.onionoo.util.TimeFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

public class BandwidthStatus extends Document {

  private static Logger log = LoggerFactory.getLogger(
      BandwidthStatus.class);

  private transient boolean isDirty = false;

  public boolean isDirty() {
    return this.isDirty;
  }

  public void clearDirty() {
    this.isDirty = false;
  }

  private SortedMap<Long, long[]> writeHistory = new TreeMap<>();

  public void setWriteHistory(SortedMap<Long, long[]> writeHistory) {
    this.writeHistory = writeHistory;
  }

  public SortedMap<Long, long[]> getWriteHistory() {
    return this.writeHistory;
  }

  private SortedMap<Long, long[]> readHistory = new TreeMap<>();

  public void setReadHistory(SortedMap<Long, long[]> readHistory) {
    this.readHistory = readHistory;
  }

  public SortedMap<Long, long[]> getReadHistory() {
    return this.readHistory;
  }

  @Override
  public void setFromDocumentString(String documentString) {
    try (Scanner s = new Scanner(documentString)) {
      while (s.hasNextLine()) {
        String line = s.nextLine();
        String[] parts = line.split(" ");
        if (parts.length != 6) {
          log.error("Illegal line '" + line + "' in bandwidth "
              + "history.  Skipping this line.");
          continue;
        }
        SortedMap<Long, long[]> history = parts[0].equals("r")
            ? readHistory : writeHistory;
        long startMillis = DateTimeHelper.parse(parts[1] + " " + parts[2]);
        long endMillis = DateTimeHelper.parse(parts[3] + " " + parts[4]);
        if (startMillis < 0L || endMillis < 0L) {
          log.error("Could not parse timestamp while reading "
              + "bandwidth history.  Skipping.");
          break;
        }
        long bandwidth = Long.parseLong(parts[5]);
        long previousEndMillis = history.headMap(startMillis).isEmpty()
            ? startMillis
            : history.get(history.headMap(startMillis).lastKey())[1];
        long nextStartMillis = history.tailMap(startMillis).isEmpty()
            ? endMillis : history.tailMap(startMillis).firstKey();
        if (previousEndMillis <= startMillis
            && nextStartMillis >= endMillis) {
          history.put(startMillis, new long[] { startMillis, endMillis,
              bandwidth });
        }
      }
    }
  }

  public void addToWriteHistory(BandwidthHistory bandwidthHistory) {
    this.addToHistory(this.writeHistory, bandwidthHistory);
  }

  public void addToReadHistory(BandwidthHistory bandwidthHistory) {
    this.addToHistory(this.readHistory, bandwidthHistory);
  }

  private void addToHistory(SortedMap<Long, long[]> history,
      BandwidthHistory bandwidthHistory) {
    long intervalMillis = bandwidthHistory.getIntervalLength()
        * DateTimeHelper.ONE_SECOND;
    for (Map.Entry<Long, Long> e :
        bandwidthHistory.getBandwidthValues().entrySet()) {
      long endMillis = e.getKey();
      long startMillis = endMillis - intervalMillis;
      long bandwidthValue = e.getValue();
      /* TODO Should we first check whether an interval is already
       * contained in history? */
      history.put(startMillis, new long[] { startMillis, endMillis,
          bandwidthValue });
      this.isDirty = true;
    }
  }

  public void compressHistory() {
    this.compressHistory(this.writeHistory);
    this.compressHistory(this.readHistory);
  }

  private void compressHistory(SortedMap<Long, long[]> history) {
    SortedMap<Long, long[]> uncompressedHistory = new TreeMap<>(history);
    history.clear();
    long lastStartMillis = 0L;
    long lastEndMillis = 0L;
    long lastBandwidth = 0L;
    String lastMonthString = "1970-01";
    long now = TimeFactory.getTime().currentTimeMillis();
    for (long[] v : uncompressedHistory.values()) {
      long startMillis = v[0];
      long endMillis = v[1];
      long bandwidth = v[2];
      long intervalLengthMillis;
      if (now - endMillis <= DateTimeHelper.THREE_DAYS) {
        intervalLengthMillis = DateTimeHelper.FIFTEEN_MINUTES;
      } else if (now - endMillis <= DateTimeHelper.ONE_WEEK) {
        intervalLengthMillis = DateTimeHelper.ONE_HOUR;
      } else if (now - endMillis
          <= DateTimeHelper.ROUGHLY_ONE_MONTH) {
        intervalLengthMillis = DateTimeHelper.FOUR_HOURS;
      } else if (now - endMillis
          <= DateTimeHelper.ROUGHLY_THREE_MONTHS) {
        intervalLengthMillis = DateTimeHelper.TWELVE_HOURS;
      } else if (now - endMillis
          <= DateTimeHelper.ROUGHLY_ONE_YEAR) {
        intervalLengthMillis = DateTimeHelper.TWO_DAYS;
      } else {
        intervalLengthMillis = DateTimeHelper.TEN_DAYS;
      }
      String monthString = DateTimeHelper.format(startMillis,
          DateTimeHelper.ISO_YEARMONTH_FORMAT);
      if (lastEndMillis == startMillis
          && ((lastEndMillis - 1L) / intervalLengthMillis)
          == ((endMillis - 1L) / intervalLengthMillis)
          && lastMonthString.equals(monthString)) {
        lastEndMillis = endMillis;
        lastBandwidth += bandwidth;
      } else {
        if (lastStartMillis > 0L) {
          history.put(lastStartMillis, new long[] { lastStartMillis,
              lastEndMillis, lastBandwidth });
        }
        lastStartMillis = startMillis;
        lastEndMillis = endMillis;
        lastBandwidth = bandwidth;
      }
      lastMonthString = monthString;
    }
    if (lastStartMillis > 0L) {
      history.put(lastStartMillis, new long[] { lastStartMillis,
          lastEndMillis, lastBandwidth });
    }
  }

  @Override
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

