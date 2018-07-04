/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.onionoo.util.DomainNameSystem;
import org.torproject.onionoo.util.FormattingUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReverseDomainNameResolver {

  static final long RDNS_LOOKUP_MAX_REQUEST_MILLIS = 10L * 1000L;

  static final long RDNS_LOOKUP_MAX_DURATION_MILLIS = 5L * 60L * 1000L;

  private static final long RDNS_LOOKUP_MAX_AGE_MILLIS =
      12L * 60L * 60L * 1000L;

  private static final int RDNS_LOOKUP_WORKERS_NUM = 5;

  private DomainNameSystem domainNameSystem;

  private Map<String, Long> addressLastLookupTimes;

  Set<String> rdnsLookupJobs;

  Map<String, String> rdnsLookupResults;

  Map<String, List<String>> rdnsVerifiedLookupResults;

  Map<String, List<String>> rdnsUnverifiedLookupResults;

  List<Long> rdnsLookupMillis;

  long startedRdnsLookups;

  private List<RdnsLookupWorker> rdnsLookupWorkers;

  public void setAddresses(Map<String, Long> addressLastLookupTimes) {
    this.addressLastLookupTimes = addressLastLookupTimes;
  }

  /** Starts reverse domain name lookups in one or more background
   * threads and returns immediately. */
  public void startReverseDomainNameLookups() {
    this.domainNameSystem = new DomainNameSystem();
    this.startedRdnsLookups = System.currentTimeMillis();
    this.rdnsLookupJobs = new HashSet<>();
    for (Map.Entry<String, Long> e :
        this.addressLastLookupTimes.entrySet()) {
      if (e.getValue() < this.startedRdnsLookups
          - RDNS_LOOKUP_MAX_AGE_MILLIS) {
        this.rdnsLookupJobs.add(e.getKey());
      }
    }
    this.rdnsLookupResults = new HashMap<>();
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

  /** Returns reverse domain name lookup results. */
  public Map<String, String> getLookupResults() {
    synchronized (this.rdnsLookupResults) {
      return new HashMap<>(this.rdnsLookupResults);
    }
  }

  /** Returns reverse domain name verified lookup results. */
  public Map<String, List<String>> getVerifiedLookupResults() {
    synchronized (this.rdnsVerifiedLookupResults) {
      return new HashMap<>(this.rdnsVerifiedLookupResults);
    }
  }

  /** Returns reverse domain name unverified lookup results. */
  public Map<String, List<String>> getUnverifiedLookupResults() {
    synchronized (this.rdnsUnverifiedLookupResults) {
      return new HashMap<>(this.rdnsUnverifiedLookupResults);
    }
  }

  /** Returns the time in milliseconds since the epoch when reverse domain
   * lookups have been started. */
  public long getLookupStartMillis() {
    return this.startedRdnsLookups;
  }

  public DomainNameSystem getDomainNameSystemInstance() {
    return this.domainNameSystem;
  }

  /** Returns a string with the number of performed reverse domain name
   * lookups and some simple statistics on lookup time. */
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        rdnsLookupMillis.size()) + " lookups performed\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        rdnsVerifiedLookupResults.size()) + " verified results found\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        rdnsUnverifiedLookupResults.size())
        + " unverified results found\n");
    if (!rdnsLookupMillis.isEmpty()) {
      Collections.sort(rdnsLookupMillis);
      sb.append("    " + FormattingUtils.formatMillis(
          rdnsLookupMillis.get(0)) + " minimum lookup time\n");
      sb.append("    " + FormattingUtils.formatMillis(
          rdnsLookupMillis.get(rdnsLookupMillis.size() / 2))
          + " median lookup time\n");
      sb.append("    " + FormattingUtils.formatMillis(
          rdnsLookupMillis.get(rdnsLookupMillis.size() - 1))
          + " maximum lookup time\n");
    }
    return sb.toString();
  }
}

