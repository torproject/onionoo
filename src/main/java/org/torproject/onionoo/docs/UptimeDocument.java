/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import java.util.Map;
import java.util.SortedMap;

@SuppressWarnings("checkstyle:membername")
public class UptimeDocument extends Document {

  @SuppressWarnings("unused")
  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  private Map<String, GraphHistory> uptime;

  public void setUptime(Map<String, GraphHistory> uptime) {
    this.uptime = uptime;
  }

  public Map<String, GraphHistory> getUptime() {
    return this.uptime;
  }

  private SortedMap<String, Map<String, GraphHistory>> flags;

  public void setFlags(
      SortedMap<String, Map<String, GraphHistory>> flags) {
    this.flags = flags;
  }

  public SortedMap<String, Map<String, GraphHistory>> getFlags() {
    return this.flags;
  }
}

