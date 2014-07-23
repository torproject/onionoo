/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Test;
import org.torproject.onionoo.docs.UptimeHistory;
import org.torproject.onionoo.docs.UptimeStatus;
import org.torproject.onionoo.util.ApplicationFactory;
import org.torproject.onionoo.util.DateTimeHelper;

public class UptimeStatusTest {

  private DummyDocumentStore documentStore;

  @Before
  public void createDummyDocumentStore() {
    this.documentStore = new DummyDocumentStore();
    ApplicationFactory.setDocumentStore(this.documentStore);
  }

  private static final String MORIA1_FINGERPRINT =
      "9695DFC35FFEB861329B9F1AB04C46397020CE31";

  @Test()
  public void testEmptyStatusNoWriteToDisk() {
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        MORIA1_FINGERPRINT);
    uptimeStatus.storeIfChanged();
    assertEquals("Should make one retrieve attempt.", 1,
        this.documentStore.getPerformedRetrieveOperations());
    assertEquals("Newly created uptime status with empty history should "
        + "not be written to disk.", 0,
        this.documentStore.getPerformedStoreOperations());
  }

  @Test()
  public void testSingleHourWriteToDisk() {
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        MORIA1_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-12-20 00:00:00") })));
    uptimeStatus.storeIfChanged();
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
    assertEquals("Newly created uptime status with non-empty history "
        + "must be written to disk.", 1,
        this.documentStore.getPerformedStoreOperations());
  }

  @Test()
  public void testTwoConsecutiveHours() {
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        MORIA1_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-12-20 00:00:00"),
        DateTimeHelper.parse("2013-12-20 01:00:00") })));
    uptimeStatus.storeIfChanged();
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

  private static final String GABELMOO_FINGERPRINT =
      "F2044413DAC2E02E3D6BCF4735A19BCA1DE97281";

  private static final String GABELMOO_UPTIME_SAMPLE =
      "r 2013-07-22-17 1161\n" /* ends 2013-09-09 02:00:00 */
      + "r 2013-09-09-03 2445\n" /* ends 2013-12-20 00:00:00 */
      + "r 2013-12-20-01 2203\n"; /* ends 2014-03-21 20:00:00 */

  private void addGabelmooUptimeSample() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.fromDocumentString(GABELMOO_UPTIME_SAMPLE);
    this.documentStore.addDocument(uptimeStatus, GABELMOO_FINGERPRINT);
  }

  @Test()
  public void testGabelmooFillInGaps() {
    this.addGabelmooUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        GABELMOO_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-09-09 02:00:00"),
        DateTimeHelper.parse("2013-12-20 00:00:00") })));
    assertEquals("Uncompressed history must contain five entries.", 5,
        uptimeStatus.getRelayHistory().size());
    uptimeStatus.storeIfChanged();
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
    this.addGabelmooUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        GABELMOO_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 17:00:00") })));
    uptimeStatus.storeIfChanged();
    assertEquals("Unchanged history should not be written to disk.", 0,
        this.documentStore.getPerformedStoreOperations());
  }

  @Test()
  public void testAddExistingHourToIntervalEnd() {
    this.addGabelmooUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        GABELMOO_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-09-09 01:00:00") })));
    uptimeStatus.storeIfChanged();
    assertEquals("Unchanged history should not be written to disk.", 0,
        this.documentStore.getPerformedStoreOperations());
  }

  @Test()
  public void testTwoHoursOverlappingWithIntervalStart() {
    this.addGabelmooUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        GABELMOO_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 16:00:00"),
        DateTimeHelper.parse("2013-07-22 17:00:00")})));
    uptimeStatus.storeIfChanged();
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
    this.addGabelmooUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        GABELMOO_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-09-09 01:00:00"),
        DateTimeHelper.parse("2013-09-09 02:00:00")})));
    uptimeStatus.storeIfChanged();
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

  private static final String ALL_RELAYS_AND_BRIDGES_FINGERPRINT = null;

  private static final String ALL_RELAYS_AND_BRIDGES_UPTIME_SAMPLE =
      "r 2013-07-22-17 5811\n" /* ends 2014-03-21 20:00:00 */
      + "b 2013-07-22-17 5811\n"; /* ends 2014-03-21 20:00:00 */

  private void addAllRelaysAndBridgesUptimeSample() {
    UptimeStatus uptimeStatus = new UptimeStatus();
    uptimeStatus.fromDocumentString(ALL_RELAYS_AND_BRIDGES_UPTIME_SAMPLE);
    this.documentStore.addDocument(uptimeStatus,
        ALL_RELAYS_AND_BRIDGES_FINGERPRINT);
  }

  @Test()
  public void testAddRelayUptimeHours() {
    this.addAllRelaysAndBridgesUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        ALL_RELAYS_AND_BRIDGES_FINGERPRINT);
    uptimeStatus.addToHistory(true, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 16:00:00"),
        DateTimeHelper.parse("2014-03-21 20:00:00")})));
    uptimeStatus.storeIfChanged();
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
    this.addAllRelaysAndBridgesUptimeSample();
    UptimeStatus uptimeStatus = UptimeStatus.loadOrCreate(
        ALL_RELAYS_AND_BRIDGES_FINGERPRINT);
    uptimeStatus.addToHistory(false, new TreeSet<Long>(Arrays.asList(
        new Long[] { DateTimeHelper.parse("2013-07-22 16:00:00"),
        DateTimeHelper.parse("2014-03-21 20:00:00")})));
    uptimeStatus.storeIfChanged();
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

