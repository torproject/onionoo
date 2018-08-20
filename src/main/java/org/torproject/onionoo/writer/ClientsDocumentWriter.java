/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.ClientsDocument;
import org.torproject.onionoo.docs.ClientsHistory;
import org.torproject.onionoo.docs.ClientsStatus;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.UpdateStatus;
import org.torproject.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Period;
import java.util.SortedSet;

/*
 * Clients status file produced as intermediate output:
 *
 * 2014-02-15 16:42:11 2014-02-16 00:00:00
 *   259.042 in=86.347,se=86.347  v4=259.042
 * 2014-02-16 00:00:00 2014-02-16 16:42:11
 *   592.958 in=197.653,se=197.653  v4=592.958
 *
 * Clients document file produced as output:
 *
 * "1_month":{
 *   "first":"2014-02-03 12:00:00",
 *   "last":"2014-02-28 12:00:00",
 *   "interval":86400,
 *   "factor":0.139049349,
 *   "count":26,
 *   "values":[371,354,349,374,432,null,485,458,493,536,null,null,524,576,
 *             607,622,null,635,null,566,774,999,945,690,656,681],
 *   "countries":{"cn":0.0192,"in":0.1768,"ir":0.2487,"ru":0.0104,
 *                "se":0.1698,"sy":0.0325,"us":0.0406},
 *   "transports":{"obfs2":0.4581},
 *   "versions":{"v4":1.0000}}
 */
public class ClientsDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      ClientsDocumentWriter.class);

  private DocumentStore documentStore;

  public ClientsDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  private int writtenDocuments = 0;

  @Override
  public void writeDocuments(long mostRecentStatusMillis) {
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updateDocuments = this.documentStore.list(
        ClientsStatus.class, updatedMillis);
    for (String hashedFingerprint : updateDocuments) {
      ClientsStatus clientsStatus = this.documentStore.retrieve(
          ClientsStatus.class, true, hashedFingerprint);
      if (clientsStatus == null) {
        continue;
      }
      SortedSet<ClientsHistory> history = clientsStatus.getHistory();
      ClientsDocument clientsDocument = this.compileClientsDocument(
          hashedFingerprint, mostRecentStatusMillis, history);
      this.documentStore.store(clientsDocument, hashedFingerprint);
      this.writtenDocuments++;
    }
    log.info("Wrote clients document files");
  }

  private String[] graphNames = new String[] {
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private Period[] graphIntervals = new Period[] {
      Period.ofWeeks(1),
      Period.ofMonths(1),
      Period.ofMonths(3),
      Period.ofYears(1),
      Period.ofYears(5) };

  private long[] dataPointIntervals = new long[] {
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private ClientsDocument compileClientsDocument(String hashedFingerprint,
      long mostRecentStatusMillis, SortedSet<ClientsHistory> history) {
    ClientsDocument clientsDocument = new ClientsDocument();
    clientsDocument.setFingerprint(hashedFingerprint);
    GraphHistoryCompiler ghc = new GraphHistoryCompiler(mostRecentStatusMillis
        + DateTimeHelper.ONE_HOUR);
    ghc.setThreshold(2L);
    for (int i = 0; i < this.graphIntervals.length; i++) {
      ghc.addGraphType(this.graphNames[i], this.graphIntervals[i],
          this.dataPointIntervals[i]);
    }
    for (ClientsHistory hist : history) {
      ghc.addHistoryEntry(hist.getStartMillis(), hist.getEndMillis(),
          hist.getTotalResponses() * ((double) DateTimeHelper.ONE_DAY) / 10.0);
    }
    clientsDocument.setAverageClients(ghc.compileGraphHistories());
    return clientsDocument;
  }

  @Override
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        this.writtenDocuments)).append(" clients document files updated\n");
    return sb.toString();
  }
}

