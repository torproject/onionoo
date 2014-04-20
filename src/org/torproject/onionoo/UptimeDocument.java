/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Map;

class UptimeDocument extends Document {

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

