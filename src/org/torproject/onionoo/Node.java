/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store search data of a single relay that was running in the past seven
 * days. */
public class Node implements Comparable<Node> {
  private String fingerprint;
  private String nickname;
  private String address;
  /* TODO Should we rename this attribute, now that we're using the class
   * for both relays and bridges? */
  private long validAfterMillis;
  private int orPort;
  private int dirPort;
  private SortedSet<String> relayFlags;
  private boolean running;
  public Node(String nickname, String fingerprint, String address,
      long validAfterMillis, int orPort, int dirPort,
      SortedSet<String> relayFlags) {
    this.nickname = nickname;
    this.fingerprint = fingerprint;
    this.address = address;
    this.validAfterMillis = validAfterMillis;
    this.orPort = orPort;
    this.dirPort = dirPort;
    this.relayFlags = relayFlags;
  }
  public Node(String fingerprint, long validAfterMillis, int orPort,
      int dirPort, SortedSet<String> relayFlags) {
    this(null, fingerprint, null, validAfterMillis, orPort, dirPort,
        relayFlags);
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
  public void setRunning(boolean running) {
    this.running = running;
  }
  public boolean getRunning() {
    return this.running;
  }
  public int compareTo(Node o) {
    return this.fingerprint.compareTo(o.fingerprint);
  }
  public boolean equals(Object o) {
    return (o instanceof Node &&
        this.fingerprint.equals(((Node) o).fingerprint));
  }
}

