/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.Map;

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
}

