/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store the data of a single network status entry. */
public class NetworkStatusEntryData
    implements Comparable<NetworkStatusEntryData> {
  private String fingerprint;
  public String getFingerprint() {
    return this.fingerprint;
  }
  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }
  private String nickname;
  public String getNickname() {
    return this.nickname;
  }
  public void setNickname(String nickname) {
    this.nickname = nickname;
  }
  private String address;
  public String getAddress() {
    return this.address;
  }
  public void setAddress(String address) {
    this.address = address;
  }
  private int orPort;
  public int getOrPort() {
    return this.orPort;
  }
  public void setOrPort(int orPort) {
    this.orPort = orPort;
  }
  private int dirPort;
  public int getDirPort() {
    return this.dirPort;
  }
  public void setDirPort(int dirPort) {
    this.dirPort = dirPort;
  }
  private SortedSet<String> relayFlags;
  public SortedSet<String> getRelayFlags() {
    return this.relayFlags;
  }
  public void setRelayFlags(SortedSet<String> relayFlags) {
    this.relayFlags = relayFlags;
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

