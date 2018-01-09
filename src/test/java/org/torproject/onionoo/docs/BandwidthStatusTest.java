/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.torproject.descriptor.BandwidthHistory;

import org.junit.Test;

import java.util.SortedMap;
import java.util.TreeMap;

public class BandwidthStatusTest {

  private static final long TEST_TIME = DateTimeHelper.parse(
      "2014-08-01 02:22:22");

  @Test()
  public void testEmptyStatusNotDirty() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    assertFalse("Newly created bandwidth status should not be dirty.",
        bandwidthStatus.isDirty());
  }

  private static class DummyBandwidthHistory implements BandwidthHistory {
    DummyBandwidthHistory(String line) {
      this.line = line;
      String[] parts = line.split(" ");
      this.historyEndMillis = DateTimeHelper.parse(parts[1] + " "
          + parts[2]);
      this.intervalLength = Long.parseLong(parts[3].substring(1));
      long intervalEndMillis = this.historyEndMillis;
      long intervalLengthMillis = this.intervalLength * 1000L;
      String[] valueStrings = parts[5].split(",");
      for (int i = valueStrings.length - 1; i >= 0; i--) {
        this.bandwidthValues.put(intervalEndMillis,
            Long.parseLong(valueStrings[i]));
        intervalEndMillis -= intervalLengthMillis;
      }
    }

    private String line;

    public String getLine() {
      return this.line;
    }

    private long historyEndMillis;

    public long getHistoryEndMillis() {
      return this.historyEndMillis;
    }

    private long intervalLength;

    public long getIntervalLength() {
      return this.intervalLength;
    }

    private SortedMap<Long, Long> bandwidthValues = new TreeMap<>();

    public SortedMap<Long, Long> getBandwidthValues() {
      return this.bandwidthValues;
    }
  }

  @Test()
  public void testNewStatusWithSingleInterval() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    bandwidthStatus.addToWriteHistory(new DummyBandwidthHistory(
        "write-history 2014-08-01 00:22:22 (900 s) 30720"));
    assertTrue("Updated bandwidth status should be marked as dirty.",
        bandwidthStatus.isDirty());
    assertEquals("Formatted document string not as expected.",
        "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n",
        bandwidthStatus.toDocumentString());
    bandwidthStatus.clearDirty();
    assertFalse("Dirty flag should be cleared.",
        bandwidthStatus.isDirty());
  }

  @Test()
  public void testNewStatusWithTwoIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    bandwidthStatus.addToWriteHistory(new DummyBandwidthHistory(
        "write-history 2014-08-01 00:22:22 (900 s) 4096,30720"));
    assertEquals("Formatted document string not as expected.",
        "w 2014-07-31 23:52:22 2014-08-01 00:07:22 4096\n"
        + "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n",
        bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testExistingStatusWithNewIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    String existingLines =
        "w 2014-07-31 23:52:22 2014-08-01 00:07:22 4096\n"
        + "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n";
    bandwidthStatus.setFromDocumentString(existingLines);
    bandwidthStatus.addToWriteHistory(new DummyBandwidthHistory(
        "write-history 2014-08-01 00:37:22 (900 s) 0"));
    assertEquals("New interval should be appended.",
        existingLines + "w 2014-08-01 00:22:22 2014-08-01 00:37:22 0\n",
        bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testCompressRecentIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    String existingLines =
        "w 2014-08-01 00:07:22 2014-08-01 00:22:22 30720\n"
        + "w 2014-08-01 00:22:22 2014-08-01 00:37:22 4096\n";
    bandwidthStatus.setFromDocumentString(existingLines);
    bandwidthStatus.compressHistory(TEST_TIME);
    assertEquals("Two recent intervals should not be compressed.",
        existingLines, bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testCompressOldIntervals() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    bandwidthStatus.setFromDocumentString(
        "w 2013-08-01 00:07:22 2013-08-01 00:22:22 30720\n"
        + "w 2013-08-01 00:22:22 2013-08-01 00:37:22 4096\n");
    bandwidthStatus.compressHistory(TEST_TIME);
    assertEquals("Two old intervals should be compressed into one.",
        "w 2013-08-01 00:07:22 2013-08-01 00:37:22 34816\n",
        bandwidthStatus.toDocumentString());
  }

  @Test()
  public void testCompressOldIntervalsOverMonthEnd() {
    BandwidthStatus bandwidthStatus = new BandwidthStatus();
    String statusLines =
        "w 2013-07-31 23:52:22 2013-08-01 00:07:22 4096\n"
        + "w 2013-08-01 00:07:22 2013-08-01 00:22:22 30720\n";
    bandwidthStatus.setFromDocumentString(statusLines);
    bandwidthStatus.compressHistory(TEST_TIME);
    assertEquals("Two old intervals should not be merged over month end.",
        statusLines, bandwidthStatus.toDocumentString());
  }
}

