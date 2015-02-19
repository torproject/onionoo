/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.NetworkStatusEntry;

public class DummyBridgeStatus implements BridgeNetworkStatus {

  public byte[] getRawDescriptorBytes() {
    return null;
  }

  public List<String> getAnnotations() {
    return null;
  }

  public List<String> getUnrecognizedLines() {
    return null;
  }

  private long publishedMillis;
  public void setPublishedMillis(long publishedMillis) {
    this.publishedMillis = publishedMillis;
  }
  public long getPublishedMillis() {
    return this.publishedMillis;
  }

  private SortedMap<String, NetworkStatusEntry> statusEntries =
      new TreeMap<String, NetworkStatusEntry>();
  public void addStatusEntry(NetworkStatusEntry statusEntry) {
    this.statusEntries.put(statusEntry.getFingerprint(), statusEntry);
  }
  public SortedMap<String, NetworkStatusEntry> getStatusEntries() {
    return this.statusEntries;
  }
}

