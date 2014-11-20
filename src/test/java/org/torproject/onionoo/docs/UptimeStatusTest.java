/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.TreeSet;

import org.junit.Test;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.UptimeHistory;
import org.torproject.onionoo.docs.UptimeStatus;

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
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-12-20 00:00:00") })));
    uptimeStatus.compressHistory();
    assertTrue("Changed uptime status should say it's dirty.",
        uptimeStatus.isDirty());
    assertEquals("History must contain single entry.", 1,
        uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertEquals("History not for relay.", true,
        newUptimeHistory.isRelay());
    assertEquals("History start millis not same as provided.",
        DateTimeHelper.parse("2013-12-20 00:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1.", 1,
        newUptimeHistory.getUptimeHours());
  }

  @Test()
  public void testTwoConsecutiveHours() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-12-20 00:00:00"),
        DateTimeHelper.parse("2013-12-20 01:00:00") })));
    uptimeStatus.compressHistory();
    assertEquals("History must contain single entry.", 1,
        uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertEquals("History not for relay.", true,
        newUptimeHistory.isRelay());
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
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-09-09 02:00:00"),
        DateTimeHelper.parse("2013-12-20 00:00:00") })));
    assertEquals("Uncompressed history must contain five entries.", 5,
        uptimeStatus.getRelayHistory().size());
    uptimeStatus.compressHistory();
    assertEquals("Compressed history must contain one entry.", 1,
        uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertEquals("History not for relay.", true,
        newUptimeHistory.isRelay());
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
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 17:00:00") })));
    uptimeStatus.compressHistory();
    assertFalse("Unchanged history should not make uptime status dirty.",
        uptimeStatus.isDirty());
  }

  @Test()
  public void testAddExistingHourToIntervalEnd() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-09-09 01:00:00") })));
    uptimeStatus.compressHistory();
    assertFalse("Unchanged history should not make uptime status dirty.",
        uptimeStatus.isDirty());
  }

  @Test()
  public void testTwoHoursOverlappingWithIntervalStart() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.setFromDocumentString(RELAY_UPTIME_SAMPLE);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 16:00:00"),
        DateTimeHelper.parse("2013-07-22 17:00:00")})));
    uptimeStatus.compressHistory();
    assertEquals("Compressed history must still contain three entries.",
        3, uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertEquals("History not for relay.", true,
        newUptimeHistory.isRelay());
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
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-09-09 01:00:00"),
        DateTimeHelper.parse("2013-09-09 02:00:00")})));
    uptimeStatus.compressHistory();
    assertEquals("Compressed history must now contain two entries.",
        2, uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertEquals("History not for relay.", true,
        newUptimeHistory.isRelay());
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
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 16:00:00"),
        DateTimeHelper.parse("2014-03-21 20:00:00")})));
    uptimeStatus.compressHistory();
    assertEquals("Compressed relay history must still contain one entry.",
        1, uptimeStatus.getRelayHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getRelayHistory().first();
    assertEquals("History not for relay.", true,
        newUptimeHistory.isRelay());
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
    uptimeStatus.addToHistory(false, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 16:00:00"),
        DateTimeHelper.parse("2014-03-21 20:00:00")})));
    uptimeStatus.compressHistory();
    assertEquals("Compressed bridge history must still contain one "
        + "entry.", 1, uptimeStatus.getBridgeHistory().size());
    UptimeHistory newUptimeHistory =
        uptimeStatus.getBridgeHistory().last();
    assertEquals("History not for bridge.", false,
        newUptimeHistory.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 16:00:00"),
        newUptimeHistory.getStartMillis());
    assertEquals("History uptime hours not 1+5811+1=5813.", 5813,
        newUptimeHistory.getUptimeHours());
  }
}

