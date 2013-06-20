/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.onionoo.LookupService.LookupResult;

/* Store relays and bridges that have been running in the past seven
 * days. */
public class NodeDataWriter {

  private DescriptorSource descriptorSource;

  private LookupService lookupService;

  private DocumentStore documentStore;

  private SortedMap<String, NodeStatus> knownNodes =
      new TreeMap<String, NodeStatus>();

  private long relaysLastValidAfterMillis = -1L;

  private long bridgesLastPublishedMillis = -1L;

  private SortedMap<String, Integer> lastBandwidthWeights = null;

  public NodeDataWriter(DescriptorSource descriptorSource,
      LookupService lookupService, DocumentStore documentStore) {
    this.descriptorSource = descriptorSource;
    this.lookupService = lookupService;
    this.documentStore = documentStore;
  }

  public void readStatusSummary() {
    SortedSet<String> fingerprints = this.documentStore.list(
        NodeStatus.class, true);
    for (String fingerprint : fingerprints) {
      NodeStatus node = this.documentStore.retrieve(NodeStatus.class,
          true, fingerprint);
      if (node.isRelay()) {
        this.relaysLastValidAfterMillis = Math.max(
            this.relaysLastValidAfterMillis, node.getLastSeenMillis());
      } else {
        this.bridgesLastPublishedMillis = Math.max(
            this.bridgesLastPublishedMillis, node.getLastSeenMillis());
      }
      this.knownNodes.put(fingerprint, node);
    }
  }

  public void readRelayNetworkConsensuses() {
    if (this.descriptorSource == null) {
      System.err.println("Not configured to read relay network "
          + "consensuses.");
      return;
    }
    DescriptorQueue descriptorQueue =
        this.descriptorSource.getDescriptorQueue(
        DescriptorType.RELAY_CONSENSUSES,
        DescriptorHistory.RELAY_CONSENSUS_HISTORY);
    Descriptor descriptor;
    while ((descriptor = descriptorQueue.nextDescriptor()) != null) {
      if (descriptor instanceof RelayNetworkStatusConsensus) {
        updateRelayNetworkStatusConsensus(
            (RelayNetworkStatusConsensus) descriptor);
      }
    }
  }

  public void setRunningBits() {
    for (NodeStatus node : this.knownNodes.values()) {
      if (node.isRelay() &&
          node.getLastSeenMillis() == this.relaysLastValidAfterMillis) {
        node.setRunning(true);
      }
      if (!node.isRelay() &&
          node.getLastSeenMillis() == this.bridgesLastPublishedMillis) {
        node.setRunning(true);
      }
    }
  }

  private void updateRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    long validAfterMillis = consensus.getValidAfterMillis();
    if (validAfterMillis > this.relaysLastValidAfterMillis) {
      this.relaysLastValidAfterMillis = validAfterMillis;
    }
    for (NetworkStatusEntry entry :
        consensus.getStatusEntries().values()) {
      String nickname = entry.getNickname();
      String fingerprint = entry.getFingerprint();
      String address = entry.getAddress();
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>(
          entry.getOrAddresses());
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> relayFlags = entry.getFlags();
      long consensusWeight = entry.getBandwidth();
      String defaultPolicy = entry.getDefaultPolicy();
      String portList = entry.getPortList();
      NodeStatus newNodeStatus = new NodeStatus(true, nickname,
          fingerprint, address, orAddressesAndPorts, null,
          validAfterMillis, orPort, dirPort, relayFlags, consensusWeight,
          null, null, -1L, defaultPolicy, portList, validAfterMillis,
          validAfterMillis, null);
      if (this.knownNodes.containsKey(fingerprint)) {
        this.knownNodes.get(fingerprint).update(newNodeStatus);
      } else {
        this.knownNodes.put(fingerprint, newNodeStatus);
      }
    }
    if (this.relaysLastValidAfterMillis == validAfterMillis) {
      this.lastBandwidthWeights = consensus.getBandwidthWeights();
    }
  }

  public void lookUpCitiesAndASes() {
    SortedSet<String> addressStrings = new TreeSet<String>();
    for (NodeStatus node : this.knownNodes.values()) {
      if (node.isRelay()) {
        addressStrings.add(node.getAddress());
      }
    }
    if (addressStrings.isEmpty()) {
      System.err.println("No relay IP addresses to resolve to cities or "
          + "ASN.");
      return;
    }
    SortedMap<String, LookupResult> lookupResults =
        this.lookupService.lookup(addressStrings);
    for (NodeStatus node : knownNodes.values()) {
      if (!node.isRelay()) {
        continue;
      }
      String addressString = node.getAddress();
      if (lookupResults.containsKey(addressString)) {
        LookupResult lookupResult = lookupResults.get(addressString);
        node.setCountryCode(lookupResult.countryCode);
        node.setCountryName(lookupResult.countryName);
        node.setRegionName(lookupResult.regionName);
        node.setCityName(lookupResult.cityName);
        node.setLatitude(lookupResult.latitude);
        node.setLongitude(lookupResult.longitude);
        node.setASNumber(lookupResult.aSNumber);
        node.setASName(lookupResult.aSName);
      }
    }
  }

  public void readBridgeNetworkStatuses() {
    if (this.descriptorSource == null) {
      System.err.println("Not configured to read bridge network "
          + "statuses.");
      return;
    }
    DescriptorQueue descriptorQueue =
        this.descriptorSource.getDescriptorQueue(
        DescriptorType.BRIDGE_STATUSES,
        DescriptorHistory.BRIDGE_STATUS_HISTORY);
    Descriptor descriptor;
    while ((descriptor = descriptorQueue.nextDescriptor()) != null) {
      if (descriptor instanceof BridgeNetworkStatus) {
        updateBridgeNetworkStatus((BridgeNetworkStatus) descriptor);
      }
    }
  }

  private void updateBridgeNetworkStatus(BridgeNetworkStatus status) {
    long publishedMillis = status.getPublishedMillis();
    if (publishedMillis > this.bridgesLastPublishedMillis) {
      this.bridgesLastPublishedMillis = publishedMillis;
    }
    for (NetworkStatusEntry entry : status.getStatusEntries().values()) {
      String nickname = entry.getNickname();
      String fingerprint = entry.getFingerprint();
      String address = entry.getAddress();
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>(
          entry.getOrAddresses());
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> relayFlags = entry.getFlags();
      NodeStatus newNodeStatus = new NodeStatus(false, nickname,
          fingerprint, address, orAddressesAndPorts, null,
          publishedMillis, orPort, dirPort, relayFlags, -1L, "??", null,
          -1L, null, null, publishedMillis, -1L, null);
      if (this.knownNodes.containsKey(fingerprint)) {
        this.knownNodes.get(fingerprint).update(newNodeStatus);
      } else {
        this.knownNodes.put(fingerprint, newNodeStatus);
      }
    }
  }

  public void writeStatusSummary() {
    this.writeSummary(true);
  }

  public void writeOutSummary() {
    this.writeSummary(false);
  }

  private void writeSummary(boolean includeArchive) {
    SortedMap<String, NodeStatus> nodes = includeArchive
        ? this.knownNodes : this.getCurrentNodes();
    for (Map.Entry<String, NodeStatus> e : nodes.entrySet()) {
      this.documentStore.store(e.getValue(), e.getKey());
    }
  }

  public SortedMap<String, NodeStatus> getCurrentNodes() {
    long cutoff = Math.max(this.relaysLastValidAfterMillis,
        this.bridgesLastPublishedMillis) - 7L * 24L * 60L * 60L * 1000L;
    SortedMap<String, NodeStatus> currentNodes =
        new TreeMap<String, NodeStatus>();
    for (Map.Entry<String, NodeStatus> e : this.knownNodes.entrySet()) {
      if (e.getValue().getLastSeenMillis() >= cutoff) {
        currentNodes.put(e.getKey(), e.getValue());
      }
    }
    return currentNodes;
  }

  public SortedMap<String, Integer> getLastBandwidthWeights() {
    return this.lastBandwidthWeights;
  }
}

