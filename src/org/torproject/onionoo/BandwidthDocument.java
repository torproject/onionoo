/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.List;
import java.util.Map;

class BandwidthDocument extends Document {

  class BandwidthHistory {
    String first;
    String last;
    Integer interval;
    Double factor;
    Integer count;
    List<Integer> values;
  }

  class NodeBandwidth {
    String fingerprint;
    Map<String, BandwidthHistory> write_history;
    Map<String, BandwidthHistory> read_history;
  }

  String relays_published;
  List<NodeBandwidth> relays;
  String bridges_published;
  List<NodeBandwidth> bridges;
}

