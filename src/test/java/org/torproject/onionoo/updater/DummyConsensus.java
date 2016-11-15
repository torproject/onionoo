/* Copyright 2014--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.descriptor.DirSourceEntry;
import org.torproject.descriptor.DirectorySignature;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class DummyConsensus implements RelayNetworkStatusConsensus {

  public byte[] getRawDescriptorBytes() {
    return null;
  }

  public List<String> getAnnotations() {
    return null;
  }

  public List<String> getUnrecognizedLines() {
    return null;
  }

  public int getNetworkStatusVersion() {
    return 0;
  }

  public String getConsensusFlavor() {
    return null;
  }

  public int getConsensusMethod() {
    return 0;
  }

  private long validAfterMillis;

  public void setValidAfterMillis(long validAfterMillis) {
    this.validAfterMillis = validAfterMillis;
  }

  public long getValidAfterMillis() {
    return this.validAfterMillis;
  }

  public long getFreshUntilMillis() {
    return 0;
  }

  public long getValidUntilMillis() {
    return 0;
  }

  public long getVoteSeconds() {
    return 0;
  }

  public long getDistSeconds() {
    return 0;
  }

  public List<String> getRecommendedServerVersions() {
    return null;
  }

  public List<String> getRecommendedClientVersions() {
    return null;
  }

  private SortedSet<String> knownFlags = new TreeSet<String>();

  public void addKnownFlag(String flag) {
    this.knownFlags.add(flag);
  }

  public SortedSet<String> getKnownFlags() {
    return this.knownFlags;
  }

  public SortedMap<String, Integer> getConsensusParams() {
    return null;
  }

  public SortedMap<String, DirSourceEntry> getDirSourceEntries() {
    return null;
  }

  private SortedMap<String, NetworkStatusEntry> statusEntries =
      new TreeMap<>();

  public void addStatusEntry(NetworkStatusEntry statusEntry) {
    this.statusEntries.put(statusEntry.getFingerprint(), statusEntry);
  }

  public SortedMap<String, NetworkStatusEntry> getStatusEntries() {
    return this.statusEntries;
  }

  public boolean containsStatusEntry(String fingerprint) {
    return false;
  }

  public NetworkStatusEntry getStatusEntry(String fingerprint) {
    return null;
  }

  public SortedMap<String, DirectorySignature> getDirectorySignatures() {
    return null;
  }

  public SortedMap<String, Integer> getBandwidthWeights() {
    return null;
  }

  public String getConsensusDigest() {
    return null;
  }

  public List<String> getPackageLines() {
    return null;
  }

  public List<DirectorySignature> getSignatures() {
    return null;
  }
}

