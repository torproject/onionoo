/* Copyright 2013--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

public enum DescriptorType {
  RELAY_CONSENSUSES("relay-descriptors/consensuses"),
  RELAY_SERVER_DESCRIPTORS("relay-descriptors/server-descriptors"),
  RELAY_EXTRA_INFOS("relay-descriptors/extra-infos"),
  EXIT_LISTS("exit-lists"),
  BRIDGE_STATUSES("bridge-descriptors/statuses"),
  BRIDGE_SERVER_DESCRIPTORS("bridge-descriptors/server-descriptors"),
  BRIDGE_EXTRA_INFOS("bridge-descriptors/extra-infos"),
  BRIDGE_POOL_ASSIGNMENTS("bridge-pool-assignments");

  private final String dir;
  DescriptorType(String dir) {
    this.dir = dir;
  }

  public String getDir() {
    return this.dir;
  }

}

