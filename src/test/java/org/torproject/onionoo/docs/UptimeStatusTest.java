/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

public class UptimeStatusTest {

  @Test()
  public void testEmptyStatusNotDirty() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    assertFalse("Newly created uptime status should not be dirty.",
        uptimeStatus.isDirty());
  }

  @Test()
  public void testSingleHourWriteToDisk() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-12-20 00:00:00"), null);
    uptimeStatus.compressHistory();
    assertTrue("Changed uptime status should say it's dirty.",
        uptimeStatus.isDirty());
    assertEquals("History must contain single entry.", 1,
        uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertTrue("History not for relay.", newUptimeHistory.isRelay());
    assertEquals("History start millis not same as provided.",
        DateTimeHelper.parse("2013-12-20 00:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1.", 1,
        newUptimeHistory.getUptimeHours());
  }

  @Test()
  public void testTwoConsecutiveHours() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-12-20 00:00:00"), null);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-12-20 01:00:00"), null);
    uptimeStatus.compressHistory();
    assertEquals("History must contain single entry.", 1,
        uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertTrue("History not for relay.", newUptimeHistory.isRelay());
    assertEquals("History start millis not same as provided.",
        DateTimeHelper.parse("2013-12-20 00:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 2.", 2,
        newUptimeHistory.getUptimeHours());
  }

  private static final String RELAY_UPTIME_SAMPLE =
      "r 2013-07-22-17 1161\n" /* ends 2013-09-09 02:00:00 */
      + "r 2013-09-09-03 2445\n" /* ends 2013-12-20 00:00:00 */
      + "r 2013-12-20-01 2203\n"; /* ends 2014-03-21 20:00:00 */

  @Test()
  public void testGabelmooFillInGaps() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-09-09 02:00:00"), null);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-12-20 00:00:00"), null);
    assertEquals("Uncompressed history must contain five entries.", 5,
        uptimeStatus.getRelayHistory().size());
    uptimeStatus.compressHistory();
    assertEquals("Compressed history must contain one entry.", 1,
        uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertTrue("History not for relay.", newUptimeHistory.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 17:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1161+1+2445+1+2203=5811.",
        5811, newUptimeHistory.getUptimeHours());
  }

  @Test()
  public void testAddExistingHourToIntervalStart() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-07-22 17:00:00"), null);
    uptimeStatus.compressHistory();
    assertFalse("Unchanged history should not make uptime status dirty.",
        uptimeStatus.isDirty());
  }

  @Test()
  public void testAddExistingHourToIntervalEnd() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-09-09 01:00:00"), null);
    uptimeStatus.compressHistory();
    assertFalse("Unchanged history should not make uptime status dirty.",
        uptimeStatus.isDirty());
  }

  @Test()
  public void testTwoHoursOverlappingWithIntervalStart() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-07-22 16:00:00"), null);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-07-22 17:00:00"), null);
    uptimeStatus.compressHistory();
    assertEquals("Compressed history must still contain three entries.",
        3, uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertTrue("History not for relay.", newUptimeHistory.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 16:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1+1161=1162.", 1162,
        newUptimeHistory.getUptimeHours());
  }

  @Test()
  public void testTwoHoursOverlappingWithIntervalEnd() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-09-09 01:00:00"), null);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-09-09 02:00:00"), null);
    uptimeStatus.compressHistory();
    assertEquals("Compressed history must now contain two entries.",
        2, uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertTrue("History not for relay.", newUptimeHistory.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 17:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1161+1+2445=3607.", 3607,
        newUptimeHistory.getUptimeHours());
  }

  private static final String RELAYS_AND_BRIDGES_UPTIME_SAMPLE =
      "r 2013-07-22-17 5811\n" /* ends 2014-03-21 20:00:00 */
      + "b 2013-07-22-17 5811\n"; /* ends 2014-03-21 20:00:00 */

  @Test()
  public void testAddRelayUptimeHours() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_AND_BRIDGES_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-07-22 16:00:00"), null);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2014-03-21 20:00:00"), null);
    uptimeStatus.compressHistory();
    assertEquals("Compressed relay history must still contain one entry.",
        1, uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertTrue("History not for relay.", newUptimeHistory.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 16:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1+5811+1=5813.", 5813,
        newUptimeHistory.getUptimeHours());
  }

  @Test()
  public void testAddBridgeUptimeHours() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_AND_BRIDGES_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(false,
        DateTimeHelper.parse("2013-07-22 16:00:00"), null);
    uptimeStatus.addToHistory(false,
        DateTimeHelper.parse("2014-03-21 20:00:00"), null);
    uptimeStatus.compressHistory();
    assertEquals("Compressed bridge history must still contain one "
        + "entry.", 1, uptimeStatus.getBridgeHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getBridgeHistory().last();
    assertFalse("History not for bridge.", newUptimeHistory.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 16:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1+5811+1=5813.", 5813,
        newUptimeHistory.getUptimeHours());
  }

  private static final SortedSet<String> RUNNING_FLAG =
      new TreeSet<>(Arrays.asList("Running"));

  @Test()
  public void testAddFlagsToNoFlagsEnd() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_AND_BRIDGES_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2014-03-21 20:00:00"), RUNNING_FLAG);
    uptimeStatus.compressHistory();
    assertEquals("Mixed relay history must not be compressed.", 2,
        uptimeStatus.getRelayHistory().size());
  }

  @Test()
  public void testAddFlagsToNoFlagsBegin() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_AND_BRIDGES_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-07-22 16:00:00"), RUNNING_FLAG);
    uptimeStatus.compressHistory();
    assertEquals("Mixed relay history must not be compressed.", 2,
        uptimeStatus.getRelayHistory().size());
  }

  @Test()
  public void testAddFlagsToNoFlagsMiddle() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_AND_BRIDGES_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2013-09-20 12:00:00"), RUNNING_FLAG);
    uptimeStatus.compressHistory();
    assertEquals("Mixed relay history must not be compressed.", 3,
        uptimeStatus.getRelayHistory().size());
  }

  private static final String RELAYS_FLAGS_UPTIME_SAMPLE =
      "R 2013-07-22-17 5811 Running\n"; /* ends 2014-03-21 20:00:00 */

  @Test()
  public void testAddFlagsToFlagsEnd() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_FLAGS_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2014-03-21 20:00:00"), RUNNING_FLAG);
    uptimeStatus.compressHistory();
    assertEquals("Relay history with flags must be compressed.", 1,
        uptimeStatus.getRelayHistory().size());
  }

  private static final SortedSet<String> RUNNING_VALID_FLAGS =
      new TreeSet<>(Arrays.asList("Running", "Valid"));

  @Test()
  public void testDontCompressDifferentFlags() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAYS_FLAGS_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true,
        DateTimeHelper.parse("2014-03-21 20:00:00"), RUNNING_VALID_FLAGS);
    uptimeStatus.compressHistory();
    assertEquals("Relay history with different flags must not be "
        + "compressed.", 2, uptimeStatus.getRelayHistory().size());
  }
}

