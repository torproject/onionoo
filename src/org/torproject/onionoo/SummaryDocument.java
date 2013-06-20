/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.List;

class SummaryDocument extends Document {

  class RelaySummary {
    String n;
    String f;
    String[] a;
    Boolean r;
  }

  class BridgeSummary {
    String n;
    String h;
    Boolean r;
  }

  String relays_published;
  List<RelaySummary> relays;
  String bridges_published;
  List<BridgeSummary> bridges;
}

