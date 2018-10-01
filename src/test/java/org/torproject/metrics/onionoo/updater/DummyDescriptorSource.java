/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.torproject.descriptor.Descriptor;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DummyDescriptorSource extends DescriptorSource {

  private Map<DescriptorType, Set<Descriptor>> descriptors = new HashMap<>();

  /** Fills the given collection with descriptors of the requested type. */
  public void provideDescriptors(DescriptorType descriptorType,
      Collection<Descriptor> descriptors) {
    for (Descriptor descriptor : descriptors) {
      this.addDescriptor(descriptorType, descriptor);
    }
  }

  /** Add a descriptor of the given type. */
  public void addDescriptor(DescriptorType descriptorType,
      Descriptor descriptor) {
    this.getDescriptorsByType(descriptorType).add(descriptor);
  }

  private Set<Descriptor> getDescriptorsByType(
      DescriptorType descriptorType) {
    this.descriptors.putIfAbsent(descriptorType, new HashSet<>());
    return this.descriptors.get(descriptorType);
  }

  private Map<DescriptorType, Set<DescriptorListener>>
      descriptorListeners = new HashMap<>();

  /** Register a listener to receive descriptors of the demanded type. */
  public void registerDescriptorListener(DescriptorListener listener,
      DescriptorType descriptorType) {
    this.descriptorListeners.putIfAbsent(descriptorType, new HashSet<>());
    this.descriptorListeners.get(descriptorType).add(listener);
  }

  @Override
  public void readDescriptors() {
    Set<DescriptorType> descriptorTypes = new HashSet<>(
        this.descriptorListeners.keySet());
    for (DescriptorType descriptorType : descriptorTypes) {
      boolean relay;
      switch (descriptorType) {
        case RELAY_CONSENSUSES:
        case RELAY_SERVER_DESCRIPTORS:
        case RELAY_EXTRA_INFOS:
        case EXIT_LISTS:
          relay = true;
          break;
        case BRIDGE_STATUSES:
        case BRIDGE_SERVER_DESCRIPTORS:
        case BRIDGE_EXTRA_INFOS:
        default:
          relay = false;
          break;
      }
      if (this.descriptors.containsKey(descriptorType)
          && this.descriptorListeners.containsKey(descriptorType)) {
        Set<DescriptorListener> listeners =
            this.descriptorListeners.get(descriptorType);
        for (Descriptor descriptor :
            this.getDescriptorsByType(descriptorType)) {
          for (DescriptorListener listener : listeners) {
            listener.processDescriptor(descriptor, relay);
          }
        }
      }
    }
  }

  public void writeHistoryFiles() {
    /* Nothing to do here. */
  }
}

