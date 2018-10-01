/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.torproject.metrics.onionoo.util.FormattingUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class ReverseDomainNameResolver {

  static final long RDNS_LOOKUP_MAX_REQUEST_MILLIS = 10L * 1000L;

  static final long RDNS_LOOKUP_MAX_DURATION_MILLIS = 5L * 60L * 1000L;

  private static final long RDNS_LOOKUP_MAX_AGE_MILLIS =
      12L * 60L * 60L * 1000L;

  private static final int RDNS_LOOKUP_WORKERS_NUM = 30;

  private Map<String, Long> addressLastLookupTimes;

  Set<String> rdnsLookupJobs;

  Map<String, SortedSet<String>> rdnsVerifiedLookupResults;

  Map<String, SortedSet<String>> rdnsUnverifiedLookupResults;

  List<Long> rdnsLookupMillis;

  long startedRdnsLookups;

  private List<RdnsLookupWorker> rdnsLookupWorkers;

  public void setAddresses(Map<String, Long> addressLastLookupTimes) {
    this.addressLastLookupTimes = addressLastLookupTimes;
  }

  /** Starts reverse domain name lookups in one or more background
   * threads and returns immediately. */
  public void startReverseDomainNameLookups() {
    this.startedRdnsLookups = System.currentTimeMillis();
    this.rdnsLookupJobs = new HashSet<>();
    for (Map.Entry<String, Long> e :
        this.addressLastLookupTimes.entrySet()) {
      if (e.getValue() < this.startedRdnsLookups
          - RDNS_LOOKUP_MAX_AGE_MILLIS) {
        this.rdnsLookupJobs.add(e.getKey());
      }
    }
    this.rdnsVerifiedLookupResults = new HashMap<>();
    this.rdnsUnverifiedLookupResults = new HashMap<>();
    this.rdnsLookupMillis = new ArrayList<>();
    this.rdnsLookupWorkers = new ArrayList<>();
    for (int i = 0; i < RDNS_LOOKUP_WORKERS_NUM; i++) {
      RdnsLookupWorker rdnsLookupWorker = new RdnsLookupWorker(this);
      this.rdnsLookupWorkers.add(rdnsLookupWorker);
      rdnsLookupWorker.setDaemon(true);
      rdnsLookupWorker.start();
    }
  }

  /** Joins all background threads performing reverse domain name lookups
   * and returns as soon as they have all finished. */
  public void finishReverseDomainNameLookups() {
    for (RdnsLookupWorker rdnsLookupWorker : this.rdnsLookupWorkers) {
      try {
        rdnsLookupWorker.join();
      } catch (InterruptedException e) {
        /* This is not something that we can take care of.  Just leave the
         * worker thread alone. */
      }
    }
  }

  /** Returns reverse domain name verified lookup results. */
  public Map<String, SortedSet<String>> getVerifiedLookupResults() {
    synchronized (this.rdnsVerifiedLookupResults) {
      return new HashMap<>(this.rdnsVerifiedLookupResults);
    }
  }

  /** Returns reverse domain name unverified lookup results. */
  public Map<String, SortedSet<String>> getUnverifiedLookupResults() {
    synchronized (this.rdnsUnverifiedLookupResults) {
      return new HashMap<>(this.rdnsUnverifiedLookupResults);
    }
  }

  /** Returns the time in milliseconds since the epoch when reverse domain
   * lookups have been started. */
  public long getLookupStartMillis() {
    return this.startedRdnsLookups;
  }

  /** Returns a string with the number of performed reverse domain name
   * lookups and some simple statistics on lookup time. */
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        rdnsLookupMillis.size())).append(" lookups performed\n");
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        rdnsVerifiedLookupResults.size())).append(" verified results found\n");
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        rdnsUnverifiedLookupResults.size()))
        .append(" unverified results found\n");
    if (!rdnsLookupMillis.isEmpty()) {
      Collections.sort(rdnsLookupMillis);
      sb.append("    ").append(FormattingUtils.formatMillis(
          rdnsLookupMillis.get(0))).append(" minimum lookup time\n");
      sb.append("    ").append(FormattingUtils.formatMillis(
          rdnsLookupMillis.get(rdnsLookupMillis.size() / 2)))
          .append(" median lookup time\n");
      sb.append("    ").append(FormattingUtils.formatMillis(
          rdnsLookupMillis.get(rdnsLookupMillis.size() - 1)))
          .append(" maximum lookup time\n");
    }
    return sb.toString();
  }
}

