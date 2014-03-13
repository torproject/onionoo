/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

public interface StatusUpdater {

  public abstract void updateStatuses();

  public abstract String getStatsString();
}

