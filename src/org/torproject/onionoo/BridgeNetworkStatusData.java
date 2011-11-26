/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store the data of a network status. */
public class BridgeNetworkStatusData {
  private SortedSet<String> bridges = new TreeSet<String>();
  public void addStatusEntry(String hashedFingerprint) {
    bridges.add(hashedFingerprint);
  }
  public SortedSet<String> getStatusEntries() {
    return new TreeSet<String>(this.bridges);
  }
  private long publishedMillis;
  public long getPublishedMillis() {
    return this.publishedMillis;
  }
  public void setPublishedMillis(long publishedMillis) {
    this.publishedMillis = publishedMillis;
  }
}

