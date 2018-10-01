/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import java.util.Hashtable;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

class RdnsLookupRequest extends Thread {

  private RdnsLookupWorker parent;

  private String address;

  private SortedSet<String> verifiedHostNames;

  private SortedSet<String> unverifiedHostNames;

  private long lookupStartedMillis = -1L;

  private long lookupCompletedMillis = -1L;

  public RdnsLookupRequest(
      RdnsLookupWorker parent, String address) {
    this.parent = parent;
    this.address = address;
  }

  @Override
  public void run() {
    this.lookupStartedMillis = System.currentTimeMillis();
    try {
      final SortedSet<String> verifiedResults = new TreeSet<>();
      final SortedSet<String> unverifiedResults = new TreeSet<>();
      final String[] bytes = this.address.split("\\.");
      if (bytes.length == 4) {
        final String reverseDnsDomain =
                bytes[3]
                        + "." + bytes[2] + "." + bytes[1] + "." + bytes[0]
                        + ".in-addr.arpa";
        String[] reverseDnsRecords =
            this.getRecords(reverseDnsDomain, "PTR");
        for (String reverseDnsRecord : reverseDnsRecords) {
          boolean verified = false;
          String[] forwardDnsRecords =
              this.getRecords(reverseDnsRecord, "A");
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

  /**
   * Returns all the values of DNS resource records found for a given host name
   * and type.
   */
  public String[] getRecords(String hostName, String type)
          throws NamingException {
    Hashtable<String, String> envProps = new Hashtable<>();
    envProps.put(Context.INITIAL_CONTEXT_FACTORY,
        "com.sun.jndi.dns.DnsContextFactory");
    final DirContext dnsContext = new InitialDirContext(envProps);
    Set<String> results = new TreeSet<>();
    Attributes dnsEntries =
        dnsContext.getAttributes(hostName, new String[] { type });
    if (dnsEntries != null) {
      Attribute dnsAttribute = dnsEntries.get(type);
      if (dnsAttribute != null) {
        NamingEnumeration<?> dnsEntryIterator = dnsEntries.get(type).getAll();
        while (dnsEntryIterator.hasMoreElements()) {
          results.add(dnsEntryIterator.next().toString());
        }
      }
    }
    return results.toArray(new String[results.size()]);
  }

  public synchronized SortedSet<String> getVerifiedHostNames() {
    return this.verifiedHostNames;
  }

  public synchronized SortedSet<String> getUnverifiedHostNames() {
    return this.unverifiedHostNames;
  }

  public synchronized long getLookupMillis() {
    return this.lookupCompletedMillis - this.lookupStartedMillis;
  }
}
