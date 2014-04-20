/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Map;

class BandwidthDocument extends Document {

  private String fingerprint;
  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  private Map<String, GraphHistory> write_history;
  public void setWriteHistory(Map<String, GraphHistory> writeHistory) {
    this.write_history = writeHistory;
  }

  private Map<String, GraphHistory> read_history;
  public void setReadHistory(Map<String, GraphHistory> readHistory) {
    this.read_history = readHistory;
  }
}

