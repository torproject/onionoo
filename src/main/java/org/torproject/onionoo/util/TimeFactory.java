/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.util;

public class TimeFactory {

  private static Time timeInstance;

  public static void setTime(Time time) {
    timeInstance = time;
  }

  public static Time getTime() {
    if (timeInstance == null) {
      timeInstance = new Time();
    }
    return timeInstance;
  }
}

