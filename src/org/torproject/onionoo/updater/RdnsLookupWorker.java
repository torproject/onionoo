/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

class RdnsLookupWorker extends Thread {

  private final ReverseDomainNameResolver reverseDomainNameResolver;

  RdnsLookupWorker(ReverseDomainNameResolver reverseDomainNameResolver) {
    this.reverseDomainNameResolver = reverseDomainNameResolver;
  }

  public void run() {
    while (this.reverseDomainNameResolver.time.currentTimeMillis() -
        ReverseDomainNameResolver.RDNS_LOOKUP_MAX_DURATION_MILLIS
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
          this.reverseDomainNameResolver, this, rdnsLookupJob);
      request.setDaemon(true);
      request.start();
      try {
        Thread.sleep(
            ReverseDomainNameResolver.RDNS_LOOKUP_MAX_REQUEST_MILLIS);
      } catch (InterruptedException e) {
        /* Getting interrupted should be the default case. */
      }
      String hostName = request.getHostName();
      if (hostName != null) {
        synchronized (this.reverseDomainNameResolver.rdnsLookupResults) {
          this.reverseDomainNameResolver.rdnsLookupResults.put(
              rdnsLookupJob, hostName);
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

