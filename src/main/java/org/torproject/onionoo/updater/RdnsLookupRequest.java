/* Copyright 2013--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import java.net.InetAddress;
import java.net.UnknownHostException;

class RdnsLookupRequest extends Thread {

  private final ReverseDomainNameResolver reverseDomainNameResolver;

  private RdnsLookupWorker parent;

  private String address;

  private String hostName;

  private long lookupStartedMillis = -1L;

  private long lookupCompletedMillis = -1L;

  public RdnsLookupRequest(
      ReverseDomainNameResolver reverseDomainNameResolver,
      RdnsLookupWorker parent, String address) {
    this.reverseDomainNameResolver = reverseDomainNameResolver;
    this.parent = parent;
    this.address = address;
  }

  @Override
  public void run() {
    this.lookupStartedMillis =
        this.reverseDomainNameResolver.time.currentTimeMillis();
    try {
      String result = InetAddress.getByName(this.address).getHostName();
      synchronized (this) {
        this.hostName = result;
      }
    } catch (UnknownHostException e) {
      /* We'll try again the next time. */
    }
    this.lookupCompletedMillis =
        this.reverseDomainNameResolver.time.currentTimeMillis();
    this.parent.interrupt();
  }

  public synchronized String getHostName() {
    return hostName;
  }

  public synchronized long getLookupMillis() {
    return this.lookupCompletedMillis - this.lookupStartedMillis;
  }
}

