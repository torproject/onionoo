/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.writer;

import org.torproject.onionoo.util.Logger;

public class DocumentWriterRunner {

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
      dw.writeDocuments();
    }
  }

  public void logStatistics() {
    for (DocumentWriter dw : this.documentWriters) {
      String statsString = dw.getStatsString();
      if (statsString != null) {
        Logger.printStatistics(dw.getClass().getSimpleName(),
            statsString);
      }
    }
  }
}
