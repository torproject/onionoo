/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.writer;

public interface DocumentWriter {

  void writeDocuments(long mostRecentStatusMillis);

  String getStatsString();
}

