package org.torproject.onionoo;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.onionoo.updater.DescriptorListener;
import org.torproject.onionoo.updater.DescriptorSource;
import org.torproject.onionoo.updater.DescriptorType;
import org.torproject.onionoo.updater.FingerprintListener;

public class DummyDescriptorSource extends DescriptorSource {

  private Map<DescriptorType, Set<Descriptor>> descriptors =
      new HashMap<DescriptorType, Set<Descriptor>>();

  public void provideDescriptors(DescriptorType descriptorType,
      Collection<Descriptor> descriptors) {
    for (Descriptor descriptor : descriptors) {
      this.addDescriptor(descriptorType, descriptor);
    }
  }

  public void addDescriptor(DescriptorType descriptorType,
      Descriptor descriptor) {
    this.getDescriptorsByType(descriptorType).add(descriptor);
  }

  private Set<Descriptor> getDescriptorsByType(
      DescriptorType descriptorType) {
    if (!this.descriptors.containsKey(descriptorType)) {
      this.descriptors.put(descriptorType, new HashSet<Descriptor>());
    }
    return this.descriptors.get(descriptorType);
  }

  private Map<DescriptorType, SortedSet<String>> fingerprints =
      new HashMap<DescriptorType, SortedSet<String>>();

  public void addFingerprints(DescriptorType descriptorType,
      Collection<String> fingerprints) {
    this.getFingerprintsByType(descriptorType).addAll(fingerprints);
  }

  public void addFingerprint(DescriptorType descriptorType,
      String fingerprint) {
    this.getFingerprintsByType(descriptorType).add(fingerprint);
  }

  private SortedSet<String> getFingerprintsByType(
      DescriptorType descriptorType) {
    if (!this.fingerprints.containsKey(descriptorType)) {
      this.fingerprints.put(descriptorType, new TreeSet<String>());
    }
    return this.fingerprints.get(descriptorType);
  }

  private Map<DescriptorType, Set<DescriptorListener>>
      descriptorListeners = new HashMap<DescriptorType,
      Set<DescriptorListener>>();

  public void registerDescriptorListener(DescriptorListener listener,
      DescriptorType descriptorType) {
    if (!this.descriptorListeners.containsKey(descriptorType)) {
      this.descriptorListeners.put(descriptorType,
          new HashSet<DescriptorListener>());
    }
    this.descriptorListeners.get(descriptorType).add(listener);
  }

  private Map<DescriptorType, Set<FingerprintListener>>
      fingerprintListeners = new HashMap<DescriptorType,
      Set<FingerprintListener>>();

  public void registerFingerprintListener(FingerprintListener listener,
      DescriptorType descriptorType) {
    if (!this.fingerprintListeners.containsKey(descriptorType)) {
      this.fingerprintListeners.put(descriptorType,
          new HashSet<FingerprintListener>());
    }
    this.fingerprintListeners.get(descriptorType).add(listener);
  }

  public void readDescriptors() {
    Set<DescriptorType> descriptorTypes = new HashSet<DescriptorType>();
    descriptorTypes.addAll(this.descriptorListeners.keySet());
    descriptorTypes.addAll(this.fingerprintListeners.keySet());
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
      case BRIDGE_POOL_ASSIGNMENTS:
      default:
        relay = false;
        break;
      }
      if (this.descriptors.containsKey(descriptorType) &&
          this.descriptorListeners.containsKey(descriptorType)) {
        Set<DescriptorListener> listeners =
            this.descriptorListeners.get(descriptorType);
        for (Descriptor descriptor :
            this.getDescriptorsByType(descriptorType)) {
          for (DescriptorListener listener : listeners) {
            listener.processDescriptor(descriptor, relay);
          }
        }
      }
      if (this.fingerprints.containsKey(descriptorType) &&
          this.fingerprintListeners.containsKey(descriptorType)) {
        Set<FingerprintListener> listeners =
            this.fingerprintListeners.get(descriptorType);
        for (FingerprintListener listener : listeners) {
          listener.processFingerprints(this.getFingerprintsByType(
              descriptorType), relay);
        }
      }
    }
  }

  public void writeHistoryFiles() {
    /* Nothing to do here. */
  }
}

