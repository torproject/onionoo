/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import java.util.Map;

public class ClientsDocument extends Document {

  @SuppressWarnings("unused")
  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> averageClients;

  public void setAverageClients(
      Map<String, GraphHistory> averageClients) {
    this.averageClients = averageClients;
  }
}

