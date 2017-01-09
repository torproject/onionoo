/* Copyright 2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.torproject.onionoo.docs.BandwidthDocument;
import org.torproject.onionoo.docs.BandwidthStatus;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.DummyDocumentStore;
import org.torproject.onionoo.docs.GraphHistory;
import org.torproject.onionoo.updater.DescriptorSourceFactory;
import org.torproject.onionoo.updater.DummyDescriptorSource;
import org.torproject.onionoo.util.DummyTime;
import org.torproject.onionoo.util.TimeFactory;

import org.junit.Before;
import org.junit.Test;

public class BandwidthDocumentWriterTest {

  private static final long TEST_TIME = DateTimeHelper.parse(
      "2017-01-09 12:00:00");

  private DummyTime dummyTime;

  @Before
  public void createDummyTime() {
    this.dummyTime = new DummyTime(TEST_TIME);
    TimeFactory.setTime(this.dummyTime);
  }

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
  public void testIgnore2019() {
    BandwidthStatus status = new BandwidthStatus();
    String documentString =
        "r 2017-01-08 08:29:33 2017-01-08 12:29:33 144272636928\n"
        + "r 2017-01-08 12:29:33 2017-01-08 16:29:33 144407647232\n"
        + "r 2017-01-08 16:29:33 2017-01-08 20:29:33 154355623936\n"
        + "r 2017-01-08 20:29:33 2017-01-09 00:29:33 149633244160\n"
        + "r 2019-08-06 05:31:45 2019-08-06 09:31:45 0\n"
        + "r 2019-08-06 09:31:45 2019-08-06 13:31:45 0\n"
        + "r 2019-08-06 13:31:45 2019-08-06 17:31:45 0\n"
        + "r 2019-08-06 17:31:45 2019-08-06 21:31:45 0\n"
        + "r 2019-08-06 21:31:45 2019-08-07 01:31:45 0\n"
        + "r 2019-08-07 01:31:45 2019-08-07 05:31:45 0\n";
    status.setFromDocumentString(documentString);
    String ibibUNC0Fingerprint = "7C0AA4E3B73E407E9F5FEB1912F8BE26D8AA124D";
    this.documentStore.addDocument(status, ibibUNC0Fingerprint);
    BandwidthDocumentWriter writer = new BandwidthDocumentWriter();
    DescriptorSourceFactory.getDescriptorSource().readDescriptors();
    writer.writeDocuments();
    assertEquals(1, this.documentStore.getPerformedStoreOperations());
    BandwidthDocument document = this.documentStore.getDocument(
        BandwidthDocument.class, ibibUNC0Fingerprint);
    assertEquals(1, document.getReadHistory().size());
    assertTrue(document.getReadHistory().containsKey("1_month"));
    GraphHistory history = document.getReadHistory().get("1_month");
    assertEquals(DateTimeHelper.parse("2017-01-08 14:00:00"),
        history.getFirst());
    assertEquals(DateTimeHelper.parse("2017-01-09 02:00:00"),
        history.getLast());
    assertEquals(DateTimeHelper.FOUR_HOURS / DateTimeHelper.ONE_SECOND,
        (int) history.getInterval());
    assertEquals(4, (int) history.getCount());
    assertEquals(4, history.getValues().size());
  }
}

