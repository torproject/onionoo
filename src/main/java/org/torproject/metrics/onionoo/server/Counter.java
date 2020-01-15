/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

class Counter {

  int value = 0;

  void increment() {
    this.value++;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }

  void clear() {
    this.value = 0;
  }
}
