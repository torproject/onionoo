/* Copyright 2016--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

public enum DescriptorHistory {

  RELAY_CONSENSUS_HISTORY("relay-consensus-history"),
  RELAY_SERVER_HISTORY("relay-server-history"),
  RELAY_EXTRAINFO_HISTORY("relay-extrainfo-history"),
  EXIT_LIST_HISTORY("exit-list-history"),
  BRIDGE_STATUS_HISTORY("bridge-status-history"),
  BRIDGE_SERVER_HISTORY("bridge-server-history"),
  BRIDGE_EXTRAINFO_HISTORY("bridge-extrainfo-history"),
  ARCHIVED_HISTORY("archived-history");

  private String fileName;

  private DescriptorHistory(String fileName) {
    this.fileName = fileName;
  }

  public String getFileName() {
    return this.fileName;
  }

}

