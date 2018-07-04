/* Copyright 2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class DomainNameSystem {

  private DirContext dnsContext;
  private Logger log;

  /** Creates a new instance. */
  public DomainNameSystem() {
    log = LoggerFactory.getLogger(DomainNameSystem.class);
    Hashtable<String, String> envProps = new Hashtable<>();
    envProps.put(Context.INITIAL_CONTEXT_FACTORY,
        "com.sun.jndi.dns.DnsContextFactory");
    try {
      dnsContext = new InitialDirContext(envProps);
    } catch (NamingException e) {
      log.error(
          "Unable to create directory context. "
          + "Host name lookup will be disabled!");
    }
  }

  /**
   * Returns all the values of DNS resource records found for a given host name
   * and type.
   */
  public String[] getRecords(String hostName, String type)
          throws NamingException {
    if (dnsContext == null) {
      /* Initial setup failed, so all lookups will fail. */
      throw new NamingException();
    }
    Set<String> results = new TreeSet<String>();
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

}
