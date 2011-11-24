/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

/* Store the data of a single network status entry. */
public class NetworkStatusEntryData
    implements Comparable<NetworkStatusEntryData> {
  private String fingerprint;
  private String nickname;
  private long lastPublishedDescriptorMillis;
  private String address;
  public NetworkStatusEntryData(String nickname, String fingerprint,
      long lastPublishedDescriptorMillis, String address) {
    this.nickname = nickname;
    this.fingerprint = fingerprint;
    this.lastPublishedDescriptorMillis = lastPublishedDescriptorMillis;
    this.address = address;
  }
  public String getFingerprint() {
    return this.fingerprint;
  }
  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }
  public String getNickname() {
    return this.nickname;
  }
  public void setNickname(String nickname) {
    this.nickname = nickname;
  }
  public long getLastPublishedDescriptorMillis() {
    return this.lastPublishedDescriptorMillis;
  }
  public String getAddress() {
    return this.address;
  }
  public void setAddress(String address) {
    this.address = address;
  }
  public int compareTo(NetworkStatusEntryData o) {
    return this.fingerprint.compareTo(o.fingerprint);
  }
  public boolean equals(Object o) {
    return (o instanceof NetworkStatusEntryData &&
        this.fingerprint.equals(((NetworkStatusEntryData) o).
        fingerprint));
  }
}

