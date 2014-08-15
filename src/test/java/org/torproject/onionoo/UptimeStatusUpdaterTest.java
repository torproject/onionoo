/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.UptimeHistory;
import org.torproject.onionoo.docs.UptimeStatus;
import org.torproject.onionoo.updater.DescriptorSourceFactory;
import org.torproject.onionoo.updater.DescriptorType;
import org.torproject.onionoo.updater.UptimeStatusUpdater;

public class UptimeStatusUpdaterTest {

  private DummyDescriptorSource descriptorSource;

  @Before
  public void createDummyDescriptorSource() {
    this.descriptorSource = new DummyDescriptorSource();
    DescriptorSourceFactory.setDescriptorSource(this.descriptorSource);
  }

  private DummyDocumentStore documentStore;

  @Before
  public void createDummyDocumentStore() {
    this.documentStore = new DummyDocumentStore();
    DocumentStoreFactory.setDocumentStore(this.documentStore);
  }

  @Test
  public void testNoDescriptorsNoStatusFiles() {
    UptimeStatusUpdater updater = new UptimeStatusUpdater();
    DescriptorSourceFactory.getDescriptorSource().readDescriptors();
    updater.updateStatuses();
    assertEquals("Without providing any data, nothing should be written "
        + "to disk.", 0,
        this.documentStore.getPerformedStoreOperations());
  }

  private static final long VALID_AFTER_SAMPLE =
      DateTimeHelper.parse("2014-03-21 20:00:00");

  private static final String GABELMOO_FINGERPRINT =
      "F2044413DAC2E02E3D6BCF4735A19BCA1DE97281";

  private void addConsensusSample() {
    DummyStatusEntry statusEntry = new DummyStatusEntry(
        GABELMOO_FINGERPRINT);
    statusEntry.addFlag("Running");
    DummyConsensus consensus = new DummyConsensus();
    consensus.setValidAfterMillis(VALID_AFTER_SAMPLE);
    consensus.addStatusEntry(statusEntry);
    this.descriptorSource.addDescriptor(DescriptorType.RELAY_CONSENSUSES,
        consensus);
  }

  @Test
  public void testOneConsensusNoStatusFiles() {
    this.addConsensusSample();
    UptimeStatusUpdater updater = new UptimeStatusUpdater();
    DescriptorSourceFactory.getDescriptorSource().readDescriptors();
    updater.updateStatuses();
    assertEquals("Two status files should have been written to disk.",
        2, this.documentStore.getPerformedStoreOperations());
    for (String fingerprint : new String[] { GABELMOO_FINGERPRINT,
        null }) {
      UptimeStatus status = this.documentStore.getDocument(
          UptimeStatus.class, fingerprint);
      UptimeHistory history = status.getRelayHistory().first();
      assertEquals("History must contain one entry.", 1,
          status.getRelayHistory().size());
      assertEquals("History not for relay.", true, history.isRelay());
      assertEquals("History start millis not as expected.",
          VALID_AFTER_SAMPLE, history.getStartMillis());
      assertEquals("History uptime hours must be 1.", 1,
          history.getUptimeHours());
    }
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

  @Test
  public void testOneConsensusOneStatusFiles() {
    this.addAllRelaysAndBridgesUptimeSample();
    this.addConsensusSample();
    UptimeStatusUpdater updater = new UptimeStatusUpdater();
    DescriptorSourceFactory.getDescriptorSource().readDescriptors();
    updater.updateStatuses();
    assertEquals("Two status files should have been written to disk.",
        2, this.documentStore.getPerformedStoreOperations());
    UptimeStatus status = this.documentStore.getDocument(
        UptimeStatus.class, ALL_RELAYS_AND_BRIDGES_FINGERPRINT);
    assertEquals("Relay history must contain one entry", 1,
        status.getRelayHistory().size());
    UptimeHistory history = status.getRelayHistory().first();
    assertEquals("History not for relay.", true, history.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 17:00:00"),
        history.getStartMillis());
    assertEquals("History uptime hours must be 5812.", 5812,
        history.getUptimeHours());
  }

  private static final long PUBLISHED_SAMPLE =
      DateTimeHelper.parse("2014-03-21 20:37:03");

  private static final String NDNOP2_FINGERPRINT =
      "DE6397A047ABE5F78B4C87AF725047831B221AAB";

  private void addBridgeStatusSample() {
    DummyStatusEntry statusEntry = new DummyStatusEntry(
        NDNOP2_FINGERPRINT);
    statusEntry.addFlag("Running");
    DummyBridgeStatus bridgeStatus = new DummyBridgeStatus();
    bridgeStatus.setPublishedMillis(PUBLISHED_SAMPLE);
    bridgeStatus.addStatusEntry(statusEntry);
    this.descriptorSource.addDescriptor(DescriptorType.BRIDGE_STATUSES,
        bridgeStatus);
  }

  @Test
  public void testOneBridgeStatusNoStatusFiles() {
    this.addBridgeStatusSample();
    UptimeStatusUpdater updater = new UptimeStatusUpdater();
    DescriptorSourceFactory.getDescriptorSource().readDescriptors();
    updater.updateStatuses();
    assertEquals("Two status files should have been written to disk.",
        2, this.documentStore.getPerformedStoreOperations());
    for (String fingerprint : new String[] { NDNOP2_FINGERPRINT,
        null }) {
      UptimeStatus status = this.documentStore.getDocument(
          UptimeStatus.class, fingerprint);
      UptimeHistory history = status.getBridgeHistory().first();
      assertEquals("Bridge history must contain one entry.", 1,
          status.getBridgeHistory().size());
      assertEquals("History not for bridge.", false, history.isRelay());
      assertEquals("History start millis not as expected.",
          DateTimeHelper.parse("2014-03-21 20:00:00"),
          history.getStartMillis());
      assertEquals("History uptime hours must be 1.", 1,
          history.getUptimeHours());
    }
  }

  @Test
  public void testOneBridgeStatusOneStatusFiles() {
    this.addAllRelaysAndBridgesUptimeSample();
    this.addBridgeStatusSample();
    UptimeStatusUpdater updater = new UptimeStatusUpdater();
    DescriptorSourceFactory.getDescriptorSource().readDescriptors();
    updater.updateStatuses();
    assertEquals("Two status files should have been written to disk.",
        2, this.documentStore.getPerformedStoreOperations());
    UptimeStatus status = this.documentStore.getDocument(
        UptimeStatus.class, ALL_RELAYS_AND_BRIDGES_FINGERPRINT);
    assertEquals("Bridge history must contain one entry.", 1,
        status.getBridgeHistory().size());
    UptimeHistory history = status.getBridgeHistory().last();
    assertEquals("History not for bridge.", false, history.isRelay());
    assertEquals("History start millis not as expected.",
        DateTimeHelper.parse("2013-07-22 17:00:00"),
        history.getStartMillis());
    assertEquals("History uptime hours must be 5812.", 5812,
        history.getUptimeHours());
  }
}

