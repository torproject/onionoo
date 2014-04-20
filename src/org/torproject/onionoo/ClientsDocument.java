/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Map;

class ClientsDocument extends Document {

  private String fingerprint;
  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  private Map<String, ClientsGraphHistory> average_clients;
  public void setAverageClients(
      Map<String, ClientsGraphHistory> averageClients) {
    this.average_clients = averageClients;
  }
}

