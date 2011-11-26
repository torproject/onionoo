/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store the data of a network status. */
public class NetworkStatusData {
  private SortedMap<String, NetworkStatusEntryData> relays =
      new TreeMap<String, NetworkStatusEntryData>();
  public void addStatusEntry(String nickname, String fingerprint,
      String address, int orPort, int dirPort,
      SortedSet<String> relayFlags) {
    NetworkStatusEntryData entry = new NetworkStatusEntryData();
    entry.setNickname(nickname);
    entry.setFingerprint(fingerprint);
    entry.setAddress(address);
    entry.setOrPort(orPort);
    entry.setDirPort(dirPort);
    entry.setRelayFlags(relayFlags);
    relays.put(fingerprint, entry);
  }
  public SortedMap<String, NetworkStatusEntryData> getStatusEntries() {
    return new TreeMap<String, NetworkStatusEntryData>(this.relays);
  }
  private long validAfterMillis;
  public long getValidAfterMillis() {
    return this.validAfterMillis;
  }
  public void setValidAfterMillis(long validAfterMillis) {
    this.validAfterMillis = validAfterMillis;
  }
  private long freshUntilMillis;
  public long getFreshUntilMillis() {
    return this.freshUntilMillis;
  }
  public void setFreshUntilMillis(long freshUntilMillis) {
    this.freshUntilMillis = freshUntilMillis;
  }
}

