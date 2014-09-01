/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentWriterRunner {

  private final static Logger log = LoggerFactory.getLogger(
      DocumentWriterRunner.class);

  private DocumentWriter[] documentWriters;

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

  public void writeDocuments() {
    for (DocumentWriter dw : this.documentWriters) {
      log.debug("Writing " + dw.getClass().getSimpleName());
      dw.writeDocuments();
    }
  }

  public void logStatistics() {
    for (DocumentWriter dw : this.documentWriters) {
      String statsString = dw.getStatsString();
      if (statsString != null) {
        log.info(dw.getClass().getSimpleName(),
            statsString);
      }
    }
  }
}
