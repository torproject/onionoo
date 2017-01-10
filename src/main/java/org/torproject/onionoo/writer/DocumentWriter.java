/* Copyright 2014--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

public interface DocumentWriter {

  public abstract void writeDocuments();

  public abstract String getStatsString();
}

