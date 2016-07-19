/* Copyright 2013--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import java.util.Map;

@SuppressWarnings("checkstyle:membername")
public class BandwidthDocument extends Document {

  @SuppressWarnings("unused")
  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> write_history;

  public void setWriteHistory(Map<String, GraphHistory> writeHistory) {
    this.write_history = writeHistory;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> read_history;

  public void setReadHistory(Map<String, GraphHistory> readHistory) {
    this.read_history = readHistory;
  }
}

