/* Copyright 2012--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.writer;

import org.torproject.metrics.onionoo.docs.DateTimeHelper;
import org.torproject.metrics.onionoo.docs.DocumentStore;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.docs.GraphHistory;
import org.torproject.metrics.onionoo.docs.UpdateStatus;
import org.torproject.metrics.onionoo.docs.WeightsDocument;
import org.torproject.metrics.onionoo.docs.WeightsStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Period;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

public class WeightsDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      WeightsDocumentWriter.class);

  private DocumentStore documentStore;

  public WeightsDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  @Override
  public void writeDocuments(long mostRecentStatusMillis) {
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updateWeightsDocuments = this.documentStore.list(
        WeightsStatus.class, updatedMillis);
    for (String fingerprint : updateWeightsDocuments) {
      WeightsStatus weightsStatus = this.documentStore.retrieve(
          WeightsStatus.class, true, fingerprint);
      if (weightsStatus == null) {
        continue;
      }
      SortedMap<long[], double[]> history = weightsStatus.getHistory();
      WeightsDocument weightsDocument = this.compileWeightsDocument(
          fingerprint, history, mostRecentStatusMillis);
      this.documentStore.store(weightsDocument, fingerprint);
    }
    log.info("Wrote weights document files");
  }

  private String[] graphNames = new String[] {
      "1_week",
      "1_month",
      "6_months",
      "1_year",
      "5_years" };

  private Period[] graphIntervals = new Period[] {
      Period.ofWeeks(1),
      Period.ofMonths(1),
      Period.ofMonths(6),
      Period.ofYears(1),
      Period.ofYears(5) };

  private long[] dataPointIntervals = new long[] {
      DateTimeHelper.ONE_HOUR,
      DateTimeHelper.FOUR_HOURS,
      DateTimeHelper.ONE_DAY,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private WeightsDocument compileWeightsDocument(String fingerprint,
      SortedMap<long[], double[]> history, long mostRecentStatusMillis) {
    WeightsDocument weightsDocument = new WeightsDocument();
    weightsDocument.setFingerprint(fingerprint);
    weightsDocument.setConsensusWeightFraction(
        this.compileGraphType(history, mostRecentStatusMillis, 1));
    weightsDocument.setGuardProbability(
        this.compileGraphType(history, mostRecentStatusMillis, 2));
    weightsDocument.setMiddleProbability(
        this.compileGraphType(history, mostRecentStatusMillis, 3));
    weightsDocument.setExitProbability(
        this.compileGraphType(history, mostRecentStatusMillis, 4));
    weightsDocument.setConsensusWeight(
        this.compileGraphType(history, mostRecentStatusMillis, 6));
    return weightsDocument;
  }

  private Map<String, GraphHistory> compileGraphType(
      SortedMap<long[], double[]> history, long mostRecentStatusMillis,
      int graphTypeIndex) {
    GraphHistoryCompiler ghc = new GraphHistoryCompiler(
        mostRecentStatusMillis + DateTimeHelper.ONE_HOUR);
    for (int i = 0; i < this.graphIntervals.length; i++) {
      ghc.addGraphType(this.graphNames[i], this.graphIntervals[i],
          this.dataPointIntervals[i]);
    }
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long startMillis = e.getKey()[0];
      long endMillis = e.getKey()[1];
      double weight = e.getValue()[graphTypeIndex];
      if (weight >= 0.0) {
        ghc.addHistoryEntry(startMillis, endMillis,
            weight * ((double) (endMillis - startMillis)));
      }
    }
    return ghc.compileGraphHistories();
  }

  @Override
  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}

