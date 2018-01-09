/* Copyright 2017--2018 The Tor Project
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
import org.torproject.onionoo.docs.NodeStatus;

import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

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
    String future = new SimpleDateFormat("yyyy")
        .format(new Date(System.currentTimeMillis()
            + DateTimeHelper.ROUGHLY_ONE_YEAR));
    String dayBeforeYesterday = new SimpleDateFormat("yyyy-MM-dd")
        .format(new Date(System.currentTimeMillis()
            - 2 * DateTimeHelper.ONE_DAY));
    String yesterday = new SimpleDateFormat("yyyy-MM-dd")
        .format(new Date(System.currentTimeMillis()
            - DateTimeHelper.ONE_DAY));
    String documentString =
        "r " + dayBeforeYesterday + " 08:29:33 " + dayBeforeYesterday
        + " 12:29:33 144272636928\n"
        + "r " + dayBeforeYesterday + " 12:29:33 " + dayBeforeYesterday
        + " 16:29:33 144407647232\n"
        + "r " + dayBeforeYesterday + " 16:29:33 " + dayBeforeYesterday
        + " 20:29:33 154355623936\n"
        + "r " + dayBeforeYesterday + " 20:29:33 " + yesterday
        + " 00:29:33 149633244160\n"
        + "r " + future + "-08-06 05:31:45 " + future + "-08-06 09:31:45 0\n"
        + "r " + future + "-08-06 09:31:45 " + future + "-08-06 13:31:45 0\n"
        + "r " + future + "-08-06 13:31:45 " + future + "-08-06 17:31:45 0\n"
        + "r " + future + "-08-06 17:31:45 " + future + "-08-06 21:31:45 0\n"
        + "r " + future + "-08-06 21:31:45 " + future + "-08-07 01:31:45 0\n"
        + "r " + future + "-08-07 01:31:45 " + future + "-08-07 05:31:45 0\n";
    NodeStatus nodeStatus = new NodeStatus(ibibUnc0Fingerprint);
    nodeStatus.setLastSeenMillis(DateTimeHelper.parse(
        yesterday + " 12:00:00"));
    this.documentStore.addDocument(nodeStatus, ibibUnc0Fingerprint);
    BandwidthStatus status = new BandwidthStatus();
    status.setFromDocumentString(documentString);
    this.documentStore.addDocument(status, ibibUnc0Fingerprint);
    BandwidthDocumentWriter writer = new BandwidthDocumentWriter();
    writer.writeDocuments();
    assertEquals(1, this.documentStore.getPerformedListOperations());
    assertEquals(3, this.documentStore.getPerformedRetrieveOperations());
    assertEquals(1, this.documentStore.getPerformedStoreOperations());
    BandwidthDocument document = this.documentStore.getDocument(
        BandwidthDocument.class, ibibUnc0Fingerprint);
    assertEquals(1, document.getReadHistory().size());
    assertTrue(document.getReadHistory().containsKey("1_month"));
    GraphHistory history = document.getReadHistory().get("1_month");
    assertEquals(DateTimeHelper.parse(dayBeforeYesterday + " 14:00:00"),
        history.getFirst());
    assertEquals(DateTimeHelper.parse(yesterday + " 02:00:00"),
        history.getLast());
    assertEquals(DateTimeHelper.FOUR_HOURS / DateTimeHelper.ONE_SECOND,
        (int) history.getInterval());
    assertEquals(4, (int) history.getCount());
    assertEquals(4, history.getValues().size());
  }
}

