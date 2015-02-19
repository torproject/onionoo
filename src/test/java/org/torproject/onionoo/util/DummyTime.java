/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.util;

import org.torproject.onionoo.util.Time;

public class DummyTime extends Time {
  private long currentTimeMillis;
  public DummyTime(long currentTimeMillis) {
    this.currentTimeMillis = currentTimeMillis;
  }
  public long currentTimeMillis() {
    return this.currentTimeMillis;
  }
}

