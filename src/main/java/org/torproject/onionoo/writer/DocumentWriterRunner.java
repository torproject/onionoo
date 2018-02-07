/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.NodeStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentWriterRunner {

  private static final Logger log = LoggerFactory.getLogger(
      DocumentWriterRunner.class);

  private DocumentWriter[] documentWriters;

  /** Instantiates a new document writer runner with newly created
   * instances of all known document writer implementations. */
  public DocumentWriterRunner() {
    SummaryDocumentWriter sdw = new SummaryDocumentWriter();
    DetailsDocumentWriter ddw = new DetailsDocumentWriter();
    BandwidthDocumentWriter bdw = new BandwidthDocumentWriter();
    WeightsDocumentWriter wdw = new WeightsDocumentWriter();
    ClientsDocumentWriter cdw = new ClientsDocumentWriter();
    UptimeDocumentWriter udw = new UptimeDocumentWriter();
    this.documentWriters = new DocumentWriter[] { sdw, ddw, bdw, wdw, cdw,
        udw };
  }

  /** Lets each configured document writer write its documents. */
  public void writeDocuments() {
    long mostRecentStatusMillis = retrieveMostRecentStatusMillis();
    for (DocumentWriter dw : this.documentWriters) {
      log.debug("Writing " + dw.getClass().getSimpleName());
      dw.writeDocuments(mostRecentStatusMillis);
    }
  }

  private long retrieveMostRecentStatusMillis() {
    DocumentStore documentStore = DocumentStoreFactory.getDocumentStore();
    long mostRecentStatusMillis = -1L;
    for (String fingerprint : documentStore.list(NodeStatus.class)) {
      NodeStatus nodeStatus = documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      mostRecentStatusMillis = Math.max(mostRecentStatusMillis,
          nodeStatus.getLastSeenMillis());
    }
    return mostRecentStatusMillis;
  }

  /** Logs statistics of all configured document writers. */
  public void logStatistics() {
    for (DocumentWriter dw : this.documentWriters) {
      String statsString = dw.getStatsString();
      if (statsString != null) {
        log.info(dw.getClass().getSimpleName() + "\n"
            + statsString);
      }
    }
  }
}

