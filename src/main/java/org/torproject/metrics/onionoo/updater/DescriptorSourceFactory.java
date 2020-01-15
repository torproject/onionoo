/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

public class DescriptorSourceFactory {

  private static DescriptorSource descriptorSourceInstance;

  /** Sets a custom singleton descriptor source instance that will be
   * returned by {@link #getDescriptorSource()} rather than creating an
   * instance upon first invocation. */
  public static void setDescriptorSource(
      DescriptorSource descriptorSource) {
    descriptorSourceInstance = descriptorSource;
  }

  /** Returns the singleton descriptor source instance that gets created
   * upon first invocation of this method. */
  public static DescriptorSource getDescriptorSource() {
    if (descriptorSourceInstance == null) {
      descriptorSourceInstance = new DescriptorSource();
    }
    return descriptorSourceInstance;
  }
}

