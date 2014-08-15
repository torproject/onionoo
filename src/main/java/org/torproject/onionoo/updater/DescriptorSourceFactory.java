/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

public class DescriptorSourceFactory {

  private static DescriptorSource descriptorSourceInstance;
  public static void setDescriptorSource(
      DescriptorSource descriptorSource) {
    descriptorSourceInstance = descriptorSource;
  }
  public static DescriptorSource getDescriptorSource() {
    if (descriptorSourceInstance == null) {
      descriptorSourceInstance = new DescriptorSource();
    }
    return descriptorSourceInstance;
  }
}

