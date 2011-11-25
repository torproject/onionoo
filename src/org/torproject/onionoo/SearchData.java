/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store search data containing those relays that have been running in the
 * past seven days. */
public class SearchData {
  public SortedSet<Long> getAllValidAfterMillis() {
    return new TreeSet<Long>(this.containedValidAfterMillis);
  }
  public long getValidAfterMillis() {
    return this.containedValidAfterMillis.isEmpty() ? -1L :
        this.containedValidAfterMillis.last();
  }
  public long getFreshUntilMillis() {
    return this.containedValidAfterMillis.isEmpty() ? -1L :
        this.containedValidAfterMillis.last() + 60L * 60L * 1000L;
  }
  private SortedSet<Long> containedValidAfterMillis = new TreeSet<Long>();
  private SortedMap<String, SearchEntryData> containedRelays =
      new TreeMap<String, SearchEntryData>();
  public void addValidAfterMillis(long validAfterMillis) {
    this.containedValidAfterMillis.add(validAfterMillis);
  }
  /* Add a search entry for a relay if this relay wasn't seen before or if
   * its current valid-after time is newer than the currently known
   * valid-after time. */
  public void addRelay(String nickname, String fingerprint,
      String address, long validAfterMillis) {
    if (!this.containedRelays.containsKey(fingerprint) ||
        this.containedRelays.get(fingerprint).getValidAfterMillis() <
        validAfterMillis) {
      SearchEntryData entry = new SearchEntryData(nickname, fingerprint,
          address, validAfterMillis);
      this.containedRelays.put(fingerprint, entry);
    }
  }
  public SortedMap<String, SearchEntryData> getRelays() {
    return new TreeMap<String, SearchEntryData>(this.containedRelays);
  }
  public void updateAll(Collection<NetworkStatusData> consensuses) {
    if (consensuses != null) {
      for (NetworkStatusData consensus : consensuses) {
        this.update(consensus);
      }
    }
  }
  public void update(NetworkStatusData consensus) {
    long validAfterMillis = consensus.getValidAfterMillis();
    this.addValidAfterMillis(validAfterMillis);
    for (NetworkStatusEntryData entry :
        consensus.getStatusEntries().values()) {
      String nickname = entry.getNickname();
      String fingerprint = entry.getFingerprint();
      long lastRestartMillis = entry.getLastPublishedDescriptorMillis();
      String address = entry.getAddress();
      this.addRelay(nickname, fingerprint, address, validAfterMillis);
    }
  }
}

