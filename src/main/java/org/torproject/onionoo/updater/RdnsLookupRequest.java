/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.onionoo.util.DomainNameSystem;

import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;

class RdnsLookupRequest extends Thread {

  private ReverseDomainNameResolver reverseDomainNameResolver;

  private DomainNameSystem domainNameSystem;

  private RdnsLookupWorker parent;

  private String address;

  private List<String> verifiedHostNames;

  private List<String> unverifiedHostNames;

  private long lookupStartedMillis = -1L;

  private long lookupCompletedMillis = -1L;

  public RdnsLookupRequest(
      ReverseDomainNameResolver reverseDomainNameResolver,
      RdnsLookupWorker parent, String address) {
    this.reverseDomainNameResolver = reverseDomainNameResolver;
    this.domainNameSystem =
            this.reverseDomainNameResolver.getDomainNameSystemInstance();
    this.parent = parent;
    this.address = address;
  }

  @Override
  public void run() {
    this.lookupStartedMillis = System.currentTimeMillis();
    try {
      final List<String> verifiedResults = new ArrayList<>();
      final List<String> unverifiedResults = new ArrayList<>();
      final String[] bytes = this.address.split("\\.");
      if (bytes.length == 4) {
        final String reverseDnsDomain =
                bytes[3]
                        + "." + bytes[2] + "." + bytes[1] + "." + bytes[0]
                        + ".in-addr.arpa";
        String[] reverseDnsRecords =
            this.domainNameSystem.getRecords(reverseDnsDomain, "PTR");
        for (String reverseDnsRecord : reverseDnsRecords) {
          boolean verified = false;
          String[] forwardDnsRecords =
              this.domainNameSystem.getRecords(reverseDnsRecord, "A");
          for (String forwardDnsRecord : forwardDnsRecords) {
            if (forwardDnsRecord.equals(this.address)) {
              verified = true;
              break;
            }
          }
          if (verified) {
            verifiedResults.add(reverseDnsRecord.substring(0,
                    reverseDnsRecord.length() - 1));
          } else {
            unverifiedResults.add(reverseDnsRecord.substring(0,
                    reverseDnsRecord.length() - 1));
          }
        }
      }
      synchronized (this) {
        this.verifiedHostNames = verifiedResults;
        this.unverifiedHostNames = unverifiedResults;
      }
    } catch (NamingException e) {
      /* The Onionoo field is omitted for both lookup failure and absence of
       * a host name. We'll try again the next time. */
    }
    this.lookupCompletedMillis = System.currentTimeMillis();
    this.parent.interrupt();
  }

  public synchronized String getHostName() {
    List<String> verifiedHostNames = this.verifiedHostNames;
    if (null != verifiedHostNames && !verifiedHostNames.isEmpty() ) {
      return verifiedHostNames.get(0);
    } else {
      return null;
    }
  }

  public synchronized List<String> getVerifiedHostNames() {
    return this.verifiedHostNames;
  }

  public synchronized List<String> getUnverifiedHostNames() {
    return this.unverifiedHostNames;
  }

  public synchronized long getLookupMillis() {
    return this.lookupCompletedMillis - this.lookupStartedMillis;
  }
}
