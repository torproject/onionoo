/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.writer;

import org.torproject.metrics.onionoo.docs.DateTimeHelper;
import org.torproject.metrics.onionoo.docs.DocumentStore;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.docs.GraphHistory;
import org.torproject.metrics.onionoo.docs.UpdateStatus;
import org.torproject.metrics.onionoo.docs.UptimeDocument;
import org.torproject.metrics.onionoo.docs.UptimeHistory;
import org.torproject.metrics.onionoo.docs.UptimeStatus;
import org.torproject.metrics.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Period;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class UptimeDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      UptimeDocumentWriter.class);

  private DocumentStore documentStore;

  public UptimeDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  @Override
  public void writeDocuments(long mostRecentStatusMillis) {
    UptimeStatus uptimeStatus = this.documentStore.retrieve(
        UptimeStatus.class, true);
    if (uptimeStatus == null) {
      /* No global uptime information available. */
      return;
    }
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updatedUptimeStatuses = this.documentStore.list(
        UptimeStatus.class, updatedMillis);
    for (String fingerprint : updatedUptimeStatuses) {
      this.updateDocument(fingerprint, mostRecentStatusMillis, uptimeStatus);
    }
    log.info("Wrote uptime document files");
  }

  private int writtenDocuments = 0;

  private void updateDocument(String fingerprint, long mostRecentStatusMillis,
      UptimeStatus knownStatuses) {
    UptimeStatus uptimeStatus = this.documentStore.retrieve(
        UptimeStatus.class, true, fingerprint);
    if (null != uptimeStatus) {
      boolean relay = uptimeStatus.getBridgeHistory().isEmpty();
      SortedSet<UptimeHistory> history = relay
          ? uptimeStatus.getRelayHistory()
          : uptimeStatus.getBridgeHistory();
      SortedSet<UptimeHistory> knownStatusesHistory = relay
          ? knownStatuses.getRelayHistory()
          : knownStatuses.getBridgeHistory();
      UptimeDocument uptimeDocument = this.compileUptimeDocument(relay,
          fingerprint, history, knownStatusesHistory, mostRecentStatusMillis);
      this.documentStore.store(uptimeDocument, fingerprint);
      this.writtenDocuments++;
    }
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
      DateTimeHelper.TWELVE_HOURS,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  private UptimeDocument compileUptimeDocument(boolean relay,
      String fingerprint, SortedSet<UptimeHistory> history,
      SortedSet<UptimeHistory> knownStatuses, long mostRecentStatusMillis) {
    UptimeDocument uptimeDocument = new UptimeDocument();
    uptimeDocument.setFingerprint(fingerprint);
    uptimeDocument.setUptime(this.compileUptimeHistory(relay, history,
        knownStatuses, mostRecentStatusMillis, null));
    SortedMap<String, Map<String, GraphHistory>> flags = new TreeMap<>();
    SortedSet<String> allFlags = new TreeSet<>();
    for (UptimeHistory hist : history) {
      if (hist.getFlags() != null) {
        allFlags.addAll(hist.getFlags());
      }
    }
    for (String flag : allFlags) {
      Map<String, GraphHistory> graphsForFlags = this.compileUptimeHistory(
          relay, history, knownStatuses, mostRecentStatusMillis, flag);
      if (!graphsForFlags.isEmpty()) {
        flags.put(flag, graphsForFlags);
      }
    }
    if (!flags.isEmpty()) {
      uptimeDocument.setFlags(flags);
    }
    return uptimeDocument;
  }

  private Map<String, GraphHistory> compileUptimeHistory(boolean relay,
      SortedSet<UptimeHistory> history, SortedSet<UptimeHistory> knownStatuses,
      long mostRecentStatusMillis, String flag) {

    /* Extracting history entries for compiling GraphHistory objects is a bit
     * harder than for the other document types. The reason is that we have to
     * combine (A) uptime history of all relays/bridges and (B) uptime history
     * of the relay/bridge that we're writing the document for. We're going to
     * refer to A and B below, to simplify descriptions a bit. */

    /* If there are either no A entries or no B entries, we can't compile
     * graphs. */
    if (history.isEmpty() || knownStatuses.isEmpty()) {
      return null;
    }

    /* Initialize the graph history compiler, and tell it that history entries
     * are divisible. This is different from the other history writers. */
    GraphHistoryCompiler ghc = new GraphHistoryCompiler(
        mostRecentStatusMillis + DateTimeHelper.ONE_HOUR);
    for (int i = 0; i < this.graphIntervals.length; i++) {
      ghc.addGraphType(this.graphNames[i], this.graphIntervals[i],
          this.dataPointIntervals[i]);
    }
    ghc.setDivisible(true);

    /* The general idea for extracting history entries and passing them to the
     * graph history compiler is to iterate over A entries one by one and keep
     * an Iterator for B entries to move forward as "time" proceeds. */
    Iterator<UptimeHistory> historyIterator = history.iterator();
    UptimeHistory hist;
    do {
      hist = historyIterator.hasNext() ? historyIterator.next() : null;
    } while (null != hist && (hist.isRelay() != relay
        || (null != flag && (null == hist.getFlags()
        || !hist.getFlags().contains(flag)))));

    /* If there is not at least one B entry, we can't compile graphs. */
    if (null == hist) {
      return null;
    }

    for (UptimeHistory statuses : knownStatuses) {

      /* If this A entry contains uptime information that we're not interested
       * in, skip it. */
      if (statuses.isRelay() != relay
          || (null != flag && (null == statuses.getFlags()
          || !statuses.getFlags().contains(flag)))) {
        continue;
      }

      /* The "current" time is the time that we're currently considering as part
       * of the A entry. It starts out as the interval start, but as we may
       * consider multiple B entries, it may proceed. The loop ends when
       * "current" time has reached the end of the considered A entry. */
      long currentTimeMillis = statuses.getStartMillis();
      do {
        if (null == hist) {

          /* There is no B entry left, which means that the relay/bridge was
           * offline from "current" time to the end of the A entry. */
          ghc.addHistoryEntry(currentTimeMillis, statuses.getEndMillis(),0.0);
          currentTimeMillis = statuses.getEndMillis();
        } else if (statuses.getEndMillis() <= hist.getStartMillis()) {

          /* This A entry ends before the B entry starts. If there was an
           * earlier B entry, count this time as offline time. */
          if (history.first().getStartMillis() <= currentTimeMillis) {
            ghc.addHistoryEntry(currentTimeMillis, statuses.getEndMillis(),
                0.0);
          }
          currentTimeMillis = statuses.getEndMillis();
        } else {

          /* A and B entries overlap. First, if there's time between "current"
           * time and the time when B starts, possibly count that as offline
           * time, but only if the relay was around earlier. */
          if (currentTimeMillis < hist.getStartMillis()) {
            if (history.first().getStartMillis() <= currentTimeMillis) {
              ghc.addHistoryEntry(currentTimeMillis, hist.getStartMillis(),
                  0.0);
            }
            currentTimeMillis = hist.getStartMillis();
          }

          /* Now handle the actually overlapping part. First determine when the
           * overlap ends, then add a history entry with the number of uptime
           * milliseconds as value. */
          long overlapEndMillis = Math.min(statuses.getEndMillis(),
              hist.getEndMillis());
          ghc.addHistoryEntry(currentTimeMillis, overlapEndMillis,
              overlapEndMillis - currentTimeMillis);
          currentTimeMillis = overlapEndMillis;

          /* If A ends after B, move on to the next B entry. */
          if (statuses.getEndMillis() >= hist.getEndMillis()) {
            do {
              hist = historyIterator.hasNext() ? historyIterator.next() : null;
            } while (null != hist && (hist.isRelay() != relay
                || (null != flag && (null == hist.getFlags()
                || !hist.getFlags().contains(flag)))));
          }
        }
      } while (currentTimeMillis < statuses.getEndMillis());
    }

    /* Now that the graph history compiler knows all relevant history, ask it to
     * compile graphs for us, and return them. */
    return ghc.compileGraphHistories();
  }

  @Override
  public String getStatsString() {
    return String.format("    %s uptime document files written\n",
        FormattingUtils.formatDecimalNumber(this.writtenDocuments));
  }
}

