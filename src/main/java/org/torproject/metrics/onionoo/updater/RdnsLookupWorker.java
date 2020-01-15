/* Copyright 2013--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import java.util.SortedSet;

class RdnsLookupWorker extends Thread {

  private final ReverseDomainNameResolver reverseDomainNameResolver;

  RdnsLookupWorker(ReverseDomainNameResolver reverseDomainNameResolver) {
    this.reverseDomainNameResolver = reverseDomainNameResolver;
  }

  @Override
  public void run() {
    while (System.currentTimeMillis()
        - ReverseDomainNameResolver.RDNS_LOOKUP_MAX_DURATION_MILLIS
        <= this.reverseDomainNameResolver.startedRdnsLookups) {
      String rdnsLookupJob = null;
      synchronized (this.reverseDomainNameResolver.rdnsLookupJobs) {
        for (String job : this.reverseDomainNameResolver.rdnsLookupJobs) {
          rdnsLookupJob = job;
          this.reverseDomainNameResolver.rdnsLookupJobs.remove(job);
          break;
        }
      }
      if (rdnsLookupJob == null) {
        break;
      }
      RdnsLookupRequest request = new RdnsLookupRequest(
          this, rdnsLookupJob);
      request.setDaemon(true);
      request.start();
      try {
        Thread.sleep(
            ReverseDomainNameResolver.RDNS_LOOKUP_MAX_REQUEST_MILLIS);
      } catch (InterruptedException e) {
        /* Getting interrupted should be the default case. */
      }
      SortedSet<String> verifiedHostNames = request.getVerifiedHostNames();
      if (null != verifiedHostNames) {
        synchronized (this.reverseDomainNameResolver
            .rdnsVerifiedLookupResults) {
          this.reverseDomainNameResolver.rdnsVerifiedLookupResults.put(
              rdnsLookupJob, verifiedHostNames);
        }
      }
      SortedSet<String> unverifiedHostNames = request.getUnverifiedHostNames();
      if (null != unverifiedHostNames) {
        synchronized (this.reverseDomainNameResolver
            .rdnsUnverifiedLookupResults) {
          this.reverseDomainNameResolver.rdnsUnverifiedLookupResults.put(
              rdnsLookupJob, unverifiedHostNames);
        }
      }
      long lookupMillis = request.getLookupMillis();
      if (lookupMillis >= 0L) {
        synchronized (this.reverseDomainNameResolver.rdnsLookupMillis) {
          this.reverseDomainNameResolver.rdnsLookupMillis.add(
              lookupMillis);
        }
      }
    }
  }
}

