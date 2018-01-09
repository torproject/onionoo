/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import java.util.Map;

@SuppressWarnings("checkstyle:membername")
public class ClientsDocument extends Document {

  @SuppressWarnings("unused")
  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> average_clients;

  public void setAverageClients(
      Map<String, GraphHistory> averageClients) {
    this.average_clients = averageClients;
  }
}

