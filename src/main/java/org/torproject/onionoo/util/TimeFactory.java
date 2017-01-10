/* Copyright 2014--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.util;

public class TimeFactory {

  private static Time timeInstance;

  /** Sets a custom singleton time instance that will be returned by
   * {@link #getTime} rather than creating an instance upon first
   * invocation.
   *
   * @deprecated Try to use other time setting methods for testing.
   */
  public static void setTime(Time time) {
    timeInstance = time;
  }

  /** Returns the singleton node indexer instance that gets created upon
   * first invocation of this method.
   *
   * @deprecated Try to use other time setting methods for testing.
   */
  public static Time getTime() {
    if (timeInstance == null) {
      timeInstance = new Time();
    }
    return timeInstance;
  }
}

