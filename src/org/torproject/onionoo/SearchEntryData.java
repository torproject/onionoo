/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store search data of a single relay that was running in the past seven
 * days. */
public class SearchEntryData implements Comparable<SearchEntryData> {
  private String fingerprint;
  private String nickname;
  private String address;
  private long validAfterMillis;
  private int orPort;
  private int dirPort;
  private SortedSet<String> relayFlags;
  public SearchEntryData(String nickname, String fingerprint,
      String address, long validAfterMillis, int orPort, int dirPort,
      SortedSet<String> relayFlags) {
    this.nickname = nickname;
    this.fingerprint = fingerprint;
    this.address = address;
    this.validAfterMillis = validAfterMillis;
    this.orPort = orPort;
    this.dirPort = dirPort;
    this.relayFlags = relayFlags;
  }
  public String getFingerprint() {
    return this.fingerprint;
  }
  public String getNickname() {
    return this.nickname;
  }
  public String getAddress() {
    return this.address;
  }
  public long getValidAfterMillis() {
    return this.validAfterMillis;
  }
  public int getOrPort() {
    return this.orPort;
  }
  public int getDirPort() {
    return this.dirPort;
  }
  public SortedSet<String> getRelayFlags() {
    return this.relayFlags;
  }
  public int compareTo(SearchEntryData o) {
    return this.fingerprint.compareTo(o.fingerprint);
  }
  public boolean equals(Object o) {
    return (o instanceof SearchEntryData &&
        this.fingerprint.equals(((SearchEntryData) o).fingerprint));
  }
}

