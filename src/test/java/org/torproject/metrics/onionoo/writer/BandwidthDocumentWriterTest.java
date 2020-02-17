/* Copyright 2017--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.torproject.metrics.onionoo.docs.BandwidthDocument;
import org.torproject.metrics.onionoo.docs.BandwidthStatus;
import org.torproject.metrics.onionoo.docs.DateTimeHelper;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.docs.DummyDocumentStore;
import org.torproject.metrics.onionoo.docs.GraphHistory;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

public class BandwidthDocumentWriterTest {

  private DummyDocumentStore documentStore;

  @Before
  public void createDummyDocumentStore() {
    this.documentStore = new DummyDocumentStore();
    DocumentStoreFactory.setDocumentStore(this.documentStore);
  }

  @Test
  public void testIgnoreFuture() {
    String ibibUnc0Fingerprint = "7C0AA4E3B73E407E9F5FEB1912F8BE26D8AA124D";
    String documentString =
          "r 2020-02-12 12:29:33 2020-02-13 12:29:33 144272636928\n"
        + "r 2020-02-13 12:29:33 2020-02-14 12:29:33 144407647232\n"
        + "r 2020-02-14 12:29:33 2020-02-15 12:29:33 154355623936\n"
        + "r 2020-02-15 12:29:33 2020-02-16 12:29:33 149633244160\n"
        + "r 2021-08-06 13:31:45 2021-08-07 13:31:45 0\n"
        + "r 2021-08-07 13:31:45 2021-08-08 13:31:45 0\n"
        + "r 2021-08-08 13:31:45 2021-08-09 13:31:45 0\n"
        + "r 2021-08-09 13:31:45 2021-08-10 13:31:45 0\n"
        + "r 2021-08-10 13:31:45 2021-08-11 13:31:45 0\n"
        + "r 2021-08-11 13:31:45 2021-08-12 13:31:45 0\n";
    BandwidthStatus status = new BandwidthStatus();
    status.setFromDocumentString(documentString);
    this.documentStore.addDocument(status, ibibUnc0Fingerprint);
    BandwidthDocumentWriter writer = new BandwidthDocumentWriter();
    writer.writeDocuments(Instant.parse("2020-05-15T12:00:00Z").toEpochMilli());
    assertEquals(1, this.documentStore.getPerformedListOperations());
    assertEquals(2, this.documentStore.getPerformedRetrieveOperations());
    assertEquals(1, this.documentStore.getPerformedStoreOperations());
    BandwidthDocument document = this.documentStore.getDocument(
        BandwidthDocument.class, ibibUnc0Fingerprint);
    assertEquals(1, document.getReadHistory().size());
    assertTrue(document.getReadHistory().containsKey("6_months"));
    GraphHistory history = document.getReadHistory().get("6_months");
    assertEquals(Instant.parse("2020-02-12T12:00:00Z").toEpochMilli(),
        history.getFirst());
    assertEquals(Instant.parse("2020-02-16T12:00:00Z").toEpochMilli(),
        history.getLast());
    assertEquals(DateTimeHelper.ONE_DAY / DateTimeHelper.ONE_SECOND,
        (int) history.getInterval());
    assertEquals(5, (int) history.getCount());
    assertEquals(5, history.getValues().size());
  }
}

