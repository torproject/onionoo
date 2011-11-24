/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

/* Store search data of a single relay that was running in the past seven
 * days. */
public class SearchEntryData implements Comparable<SearchEntryData> {
  private String fingerprint;
  private String nickname;
  private long lastRestartMillis;
  private String address;
  private long validAfterMillis;
  public SearchEntryData(String nickname, String fingerprint,
      long lastRestartMillis, String address, long validAfterMillis) {
    this.nickname = nickname;
    this.fingerprint = fingerprint;
    this.lastRestartMillis = lastRestartMillis;
    this.address = address;
    this.validAfterMillis = validAfterMillis;
  }
  public String getFingerprint() {
    return this.fingerprint;
  }
  public String getNickname() {
    return this.nickname;
  }
  public long getLastRestartMillis() {
    return this.lastRestartMillis;
  }
  public String getAddress() {
    return this.address;
  }
  public long getValidAfterMillis() {
    return this.validAfterMillis;
  }
  public int compareTo(SearchEntryData o) {
    return this.fingerprint.compareTo(o.fingerprint);
  }
  public boolean equals(Object o) {
    return (o instanceof SearchEntryData &&
        this.fingerprint.equals(((SearchEntryData) o).fingerprint));
  }
}

