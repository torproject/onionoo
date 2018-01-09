/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

public interface StatusUpdater {

  public abstract void updateStatuses();

  public abstract String getStatsString();
}

