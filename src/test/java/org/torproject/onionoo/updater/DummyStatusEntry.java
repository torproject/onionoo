/* Copyright 2014--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.descriptor.NetworkStatusEntry;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class DummyStatusEntry implements NetworkStatusEntry {

  public DummyStatusEntry(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public byte[] getStatusEntryBytes() {
    return null;
  }

  @Override
  public String getNickname() {
    return null;
  }

  private String fingerprint;

  public String getFingerprint() {
    return this.fingerprint;
  }

  public String getDescriptor() {
    return null;
  }

  public long getPublishedMillis() {
    return 0;
  }

  public String getAddress() {
    return null;
  }

  public int getOrPort() {
    return 0;
  }

  public int getDirPort() {
    return 0;
  }

  public Set<String> getMicrodescriptorDigests() {
    return null;
  }

  public List<String> getOrAddresses() {
    return null;
  }

  private SortedSet<String> flags = new TreeSet<>();

  public void addFlag(String flag) {
    this.flags.add(flag);
  }

  public SortedSet<String> getFlags() {
    return this.flags;
  }

  public String getVersion() {
    return null;
  }

  public long getBandwidth() {
    return 0;
  }

  public long getMeasured() {
    return 0;
  }

  public boolean getUnmeasured() {
    return false;
  }

  public String getDefaultPolicy() {
    return null;
  }

  public String getPortList() {
    return null;
  }

  @Override
  public String getMasterKeyEd25519() {
    return null;
  }
}

