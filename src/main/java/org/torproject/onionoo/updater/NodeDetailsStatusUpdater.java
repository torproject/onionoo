/* Copyright 2011--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;
import org.torproject.descriptor.ExtraInfoDescriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DetailsStatus;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.NodeStatus;
import org.torproject.onionoo.util.FormattingUtils;
import org.torproject.onionoo.util.TimeFactory;

/*
 * Status updater for both node and details statuses.
 *
 * Node statuses hold selected information about relays and bridges that
 * must be retrieved efficiently in the update process.  They are kept in
 * memory and not written to disk until the update process has completed.
 * They are also the sole basis for writing summary documents that are
 * used in the server package to build the node index.
 *
 * Details statuses hold detailed information about relays and bridges and
 * are written at the end of the update process.  They are not kept in
 * memory.  They are the sole basis for writing details documents.
 *
 * The complete update process as implemented in this class is as follows:
 *
 *   1. Parse descriptors and either write their contents to details
 *      statuses (which requires retrieving them and storing them back to
 *      disk, which is why this is only done for detailed information that
 *      don't fit into memory) and/or local data structures in memory
 *      (which may include node statuses).
 *   2. Read all known node statuses from disk and merge their contents
 *      with the node statuses from parsing descriptors.  Node statuses
 *      are not loaded from disk before the parse step in order to save
 *      memory for parsed descriptors.
 *   3. Perform reverse DNS lookups, Look up relay IP addresses in a
 *      GeoIP database, and calculate path selection probabilities.
 *      Update node statuses accordingly.
 *   4. Retrieve details statuses corresponding to nodes that have been
 *      changed since the start of the update process, possibly update the
 *      node statuses with contents from newly parsed descriptors, update
 *      details statuses with results from lookup operations and new path
 *      selection probabilities, and store details statuses and node
 *      statuses back to disk.
 */
public class NodeDetailsStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private Logger log = LoggerFactory.getLogger(
      NodeDetailsStatusUpdater.class);

  private DescriptorSource descriptorSource;

  private ReverseDomainNameResolver reverseDomainNameResolver;

  private LookupService lookupService;

  private DocumentStore documentStore;

  private long now;

  private SortedMap<String, NodeStatus> knownNodes =
      new TreeMap<String, NodeStatus>();

  private long relaysLastValidAfterMillis = -1L;

  private long bridgesLastPublishedMillis = -1L;

  private SortedMap<String, Integer> lastBandwidthWeights = null;

  private int relayConsensusesProcessed = 0, bridgeStatusesProcessed = 0;

  public NodeDetailsStatusUpdater(
      ReverseDomainNameResolver reverseDomainNameResolver,
      LookupService lookupService) {
    this.descriptorSource = DescriptorSourceFactory.getDescriptorSource();
    this.reverseDomainNameResolver = reverseDomainNameResolver;
    this.lookupService = lookupService;
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.now = TimeFactory.getTime().currentTimeMillis();
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_SERVER_DESCRIPTORS);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_STATUSES);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_SERVER_DESCRIPTORS);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.BRIDGE_EXTRA_INFOS);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.EXIT_LISTS);
  }

  /* Step 1: parse descriptors. */

  private SortedSet<String> updatedNodes = new TreeSet<String>();

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof ServerDescriptor && relay) {
      this.processRelayServerDescriptor((ServerDescriptor) descriptor);
    } else if (descriptor instanceof ExitList) {
      this.processExitList((ExitList) descriptor);
    } else if (descriptor instanceof RelayNetworkStatusConsensus) {
      this.processRelayNetworkStatusConsensus(
          (RelayNetworkStatusConsensus) descriptor);
    } else if (descriptor instanceof ServerDescriptor && !relay) {
      this.processBridgeServerDescriptor((ServerDescriptor) descriptor);
    } else if (descriptor instanceof ExtraInfoDescriptor && !relay) {
      this.processBridgeExtraInfoDescriptor(
          (ExtraInfoDescriptor) descriptor);
    } else if (descriptor instanceof BridgeNetworkStatus) {
      this.processBridgeNetworkStatus((BridgeNetworkStatus) descriptor);
    }
  }

  private void processRelayServerDescriptor(
      ServerDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, true, fingerprint);
    if (detailsStatus == null) {
      detailsStatus = new DetailsStatus();
    } else if (detailsStatus.getDescPublished() != null &&
        detailsStatus.getDescPublished() >=
        descriptor.getPublishedMillis()) {
      /* Already parsed more recent server descriptor from this relay. */
      return;
    }
    long lastRestartedMillis = descriptor.getPublishedMillis()
        - descriptor.getUptime() * DateTimeHelper.ONE_SECOND;
    int bandwidthRate = descriptor.getBandwidthRate();
    int bandwidthBurst = descriptor.getBandwidthBurst();
    int observedBandwidth = descriptor.getBandwidthObserved();
    int advertisedBandwidth = Math.min(bandwidthRate,
        Math.min(bandwidthBurst, observedBandwidth));
    detailsStatus.setDescPublished(descriptor.getPublishedMillis());
    detailsStatus.setLastRestarted(lastRestartedMillis);
    detailsStatus.setBandwidthRate(bandwidthRate);
    detailsStatus.setBandwidthBurst(bandwidthBurst);
    detailsStatus.setObservedBandwidth(observedBandwidth);
    detailsStatus.setAdvertisedBandwidth(advertisedBandwidth);
    detailsStatus.setExitPolicy(descriptor.getExitPolicyLines());
    detailsStatus.setContact(descriptor.getContact());
    detailsStatus.setPlatform(descriptor.getPlatform());
    detailsStatus.setFamily(descriptor.getFamilyEntries());
    if (descriptor.getIpv6DefaultPolicy() != null &&
        (descriptor.getIpv6DefaultPolicy().equals("accept") ||
        descriptor.getIpv6DefaultPolicy().equals("reject")) &&
        descriptor.getIpv6PortList() != null) {
      Map<String, List<String>> exitPolicyV6Summary =
          new HashMap<String, List<String>>();
      List<String> portsOrPortRanges = Arrays.asList(
          descriptor.getIpv6PortList().split(","));
      exitPolicyV6Summary.put(descriptor.getIpv6DefaultPolicy(),
          portsOrPortRanges);
      detailsStatus.setExitPolicyV6Summary(exitPolicyV6Summary);
    }
    detailsStatus.setHibernating(descriptor.isHibernating() ? true :
        null);
    this.documentStore.store(detailsStatus, fingerprint);
  }

  private Map<String, Map<String, Long>> exitListEntries =
      new HashMap<String, Map<String, Long>>();

  private void processExitList(ExitList exitList) {
    for (ExitListEntry exitListEntry : exitList.getExitListEntries()) {
      String fingerprint = exitListEntry.getFingerprint();
      long scanMillis = exitListEntry.getScanMillis();
      if (scanMillis < this.now - DateTimeHelper.ONE_DAY) {
        continue;
      }
      if (!this.exitListEntries.containsKey(fingerprint)) {
        this.exitListEntries.put(fingerprint,
            new HashMap<String, Long>());
      }
      String exitAddress = exitListEntry.getExitAddress();
      if (!this.exitListEntries.get(fingerprint).containsKey(exitAddress)
          || this.exitListEntries.get(fingerprint).get(exitAddress)
          < scanMillis) {
        this.exitListEntries.get(fingerprint).put(exitAddress,
            scanMillis);
      }
    }
  }

  private void processRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    long validAfterMillis = consensus.getValidAfterMillis();
    if (validAfterMillis > this.relaysLastValidAfterMillis) {
      this.relaysLastValidAfterMillis = validAfterMillis;
    }
    Set<String> recommendedVersions = null;
    if (consensus.getRecommendedServerVersions() != null) {
      recommendedVersions = new HashSet<String>();
      for (String recommendedVersion :
          consensus.getRecommendedServerVersions()) {
        recommendedVersions.add("Tor " + recommendedVersion);
      }
    }
    for (Map.Entry<String, NetworkStatusEntry> e :
        consensus.getStatusEntries().entrySet()) {
      String fingerprint = e.getKey();
      NetworkStatusEntry entry = e.getValue();
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      if (nodeStatus == null) {
        nodeStatus = new NodeStatus(fingerprint);
        this.knownNodes.put(fingerprint, nodeStatus);
      }
      String address = entry.getAddress();
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>(
          entry.getOrAddresses());
      nodeStatus.addLastAddresses(validAfterMillis, address, orPort,
          dirPort, orAddressesAndPorts);
      if (nodeStatus.getFirstSeenMillis() == 0L ||
          validAfterMillis < nodeStatus.getFirstSeenMillis()) {
        nodeStatus.setFirstSeenMillis(validAfterMillis);
      }
      if (validAfterMillis > nodeStatus.getLastSeenMillis()) {
        nodeStatus.setLastSeenMillis(validAfterMillis);
        nodeStatus.setRelay(true);
        nodeStatus.setNickname(entry.getNickname());
        nodeStatus.setAddress(address);
        nodeStatus.setOrAddressesAndPorts(orAddressesAndPorts);
        nodeStatus.setOrPort(orPort);
        nodeStatus.setDirPort(dirPort);
        nodeStatus.setRelayFlags(entry.getFlags());
        nodeStatus.setConsensusWeight(entry.getBandwidth());
        nodeStatus.setDefaultPolicy(entry.getDefaultPolicy());
        nodeStatus.setPortList(entry.getPortList());
        nodeStatus.setRecommendedVersion((recommendedVersions == null ||
            entry.getVersion() == null) ? null :
            recommendedVersions.contains(entry.getVersion()));
      }
    }
    this.relayConsensusesProcessed++;
    if (this.relaysLastValidAfterMillis == validAfterMillis) {
      this.lastBandwidthWeights = consensus.getBandwidthWeights();
    }
  }

  private void processBridgeServerDescriptor(
      ServerDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, true, fingerprint);
    if (detailsStatus == null) {
      detailsStatus = new DetailsStatus();
    } else if (descriptor.getPublishedMillis() <=
        detailsStatus.getDescPublished()) {
      /* Already parsed more recent server descriptor from this bridge. */
      return;
    }
    long lastRestartedMillis = descriptor.getPublishedMillis()
        - descriptor.getUptime() * DateTimeHelper.ONE_SECOND;
    int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
        Math.min(descriptor.getBandwidthBurst(),
        descriptor.getBandwidthObserved()));
    detailsStatus.setDescPublished(descriptor.getPublishedMillis());
    detailsStatus.setLastRestarted(lastRestartedMillis);
    detailsStatus.setAdvertisedBandwidth(advertisedBandwidth);
    detailsStatus.setPlatform(descriptor.getPlatform());
    this.documentStore.store(detailsStatus, fingerprint);
    this.updatedNodes.add(fingerprint);
  }

  private void processBridgeExtraInfoDescriptor(
      ExtraInfoDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, true, fingerprint);
    if (detailsStatus == null) {
      detailsStatus = new DetailsStatus();
    } else if (null == detailsStatus.getExtraInfoDescPublished() ||
        descriptor.getPublishedMillis() >
        detailsStatus.getExtraInfoDescPublished()) {
      detailsStatus.setExtraInfoDescPublished(
          descriptor.getPublishedMillis());
      detailsStatus.setTransports(descriptor.getTransports());
      this.documentStore.store(detailsStatus, fingerprint);
      this.updatedNodes.add(fingerprint);
    }
  }

  private void processBridgeNetworkStatus(BridgeNetworkStatus status) {
    long publishedMillis = status.getPublishedMillis();
    if (publishedMillis > this.bridgesLastPublishedMillis) {
      this.bridgesLastPublishedMillis = publishedMillis;
    }
    for (Map.Entry<String, NetworkStatusEntry> e :
        status.getStatusEntries().entrySet()) {
      String fingerprint = e.getKey();
      NetworkStatusEntry entry = e.getValue();
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      if (nodeStatus == null) {
        nodeStatus = new NodeStatus(fingerprint);
        this.knownNodes.put(fingerprint, nodeStatus);
      }
      if (nodeStatus.getFirstSeenMillis() == 0L ||
          publishedMillis < nodeStatus.getFirstSeenMillis()) {
        nodeStatus.setFirstSeenMillis(publishedMillis);
      }
      if (publishedMillis > nodeStatus.getLastSeenMillis()) {
        nodeStatus.setRelay(false);
        nodeStatus.setNickname(entry.getNickname());
        nodeStatus.setAddress(entry.getAddress());
        nodeStatus.setOrAddressesAndPorts(new TreeSet<String>(
          entry.getOrAddresses()));
        nodeStatus.setOrPort(entry.getOrPort());
        nodeStatus.setDirPort(entry.getDirPort());
        nodeStatus.setRelayFlags(entry.getFlags());
        nodeStatus.setLastSeenMillis(publishedMillis);
      }
    }
    this.bridgeStatusesProcessed++;
  }

  public void updateStatuses() {
    this.readNodeStatuses();
    log.info("Read node statuses");
    this.startReverseDomainNameLookups();
    log.info("Started reverse domain name lookups");
    this.lookUpCitiesAndASes();
    log.info("Looked up cities and ASes");
    this.calculatePathSelectionProbabilities();
    log.info("Calculated path selection probabilities");
    this.finishReverseDomainNameLookups();
    log.info("Finished reverse domain name lookups");
    this.updateNodeDetailsStatuses();
    log.info("Updated node and details statuses");
  }

  /* Step 2: read node statuses from disk. */

  private SortedSet<String> currentRelays = new TreeSet<String>(),
      runningRelays = new TreeSet<String>();

  private void readNodeStatuses() {
    SortedSet<String> previouslyKnownNodes = this.documentStore.list(
        NodeStatus.class);
    long previousRelaysLastValidAfterMillis = -1L,
        previousBridgesLastValidAfterMillis = -1L;
    for (String fingerprint : previouslyKnownNodes) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      if (nodeStatus.isRelay() && nodeStatus.getLastSeenMillis() >
          previousRelaysLastValidAfterMillis) {
        previousRelaysLastValidAfterMillis =
            nodeStatus.getLastSeenMillis();
      } else if (!nodeStatus.isRelay() && nodeStatus.getLastSeenMillis() >
          previousBridgesLastValidAfterMillis) {
        previousBridgesLastValidAfterMillis =
            nodeStatus.getLastSeenMillis();
      }
    }
    if (previousRelaysLastValidAfterMillis >
        this.relaysLastValidAfterMillis) {
      this.relaysLastValidAfterMillis =
          previousRelaysLastValidAfterMillis;
    }
    if (previousBridgesLastValidAfterMillis >
        this.bridgesLastPublishedMillis) {
      this.bridgesLastPublishedMillis =
          previousBridgesLastValidAfterMillis;
    }
    long cutoff = Math.max(this.relaysLastValidAfterMillis,
        this.bridgesLastPublishedMillis) - DateTimeHelper.ONE_WEEK;
    for (Map.Entry<String, NodeStatus> e : this.knownNodes.entrySet()) {
      String fingerprint = e.getKey();
      NodeStatus nodeStatus = e.getValue();
      this.updatedNodes.add(fingerprint);
      if (nodeStatus.isRelay() &&
          nodeStatus.getLastSeenMillis() >= cutoff) {
        this.currentRelays.add(fingerprint);
        if (nodeStatus.getLastSeenMillis() ==
            this.relaysLastValidAfterMillis) {
          this.runningRelays.add(fingerprint);
        }
      }
    }
    for (String fingerprint : previouslyKnownNodes) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      NodeStatus updatedNodeStatus = null;
      if (this.knownNodes.containsKey(fingerprint)) {
        updatedNodeStatus = this.knownNodes.get(fingerprint);
        String address = nodeStatus.getAddress();
        int orPort = nodeStatus.getOrPort();
        int dirPort = nodeStatus.getDirPort();
        SortedSet<String> orAddressesAndPorts =
            nodeStatus.getOrAddressesAndPorts();
        updatedNodeStatus.addLastAddresses(
            nodeStatus.getLastChangedOrAddressOrPort(), address, orPort,
            dirPort, orAddressesAndPorts);
        if (nodeStatus.getLastSeenMillis() >
            updatedNodeStatus.getLastSeenMillis()) {
          updatedNodeStatus.setNickname(nodeStatus.getNickname());
          updatedNodeStatus.setAddress(address);
          updatedNodeStatus.setOrAddressesAndPorts(orAddressesAndPorts);
          updatedNodeStatus.setLastSeenMillis(
              nodeStatus.getLastSeenMillis());
          updatedNodeStatus.setOrPort(orPort);
          updatedNodeStatus.setDirPort(dirPort);
          updatedNodeStatus.setRelayFlags(nodeStatus.getRelayFlags());
          updatedNodeStatus.setConsensusWeight(
              nodeStatus.getConsensusWeight());
          updatedNodeStatus.setCountryCode(nodeStatus.getCountryCode());
          updatedNodeStatus.setDefaultPolicy(
              nodeStatus.getDefaultPolicy());
          updatedNodeStatus.setPortList(nodeStatus.getPortList());
          updatedNodeStatus.setASNumber(nodeStatus.getASNumber());
          updatedNodeStatus.setRecommendedVersion(
              nodeStatus.getRecommendedVersion());
        }
        if (nodeStatus.getFirstSeenMillis() <
            updatedNodeStatus.getFirstSeenMillis()) {
          updatedNodeStatus.setFirstSeenMillis(
              nodeStatus.getFirstSeenMillis());
        }
        updatedNodeStatus.setLastRdnsLookup(
            nodeStatus.getLastRdnsLookup());
      } else {
        updatedNodeStatus = nodeStatus;
        this.knownNodes.put(fingerprint, nodeStatus);
        if (nodeStatus.getLastSeenMillis() == (nodeStatus.isRelay() ?
            previousRelaysLastValidAfterMillis :
            previousBridgesLastValidAfterMillis)) {
          /* This relay or bridge was previously running, but we didn't
           * parse any descriptors with its fingerprint.  Make sure to
           * update its details status file later on, so it has the
           * correct running bit. */
          this.updatedNodes.add(fingerprint);
        }
      }
      if (updatedNodeStatus.isRelay() &&
          updatedNodeStatus.getLastSeenMillis() >= cutoff) {
        this.currentRelays.add(fingerprint);
        if (updatedNodeStatus.getLastSeenMillis() ==
            this.relaysLastValidAfterMillis) {
          this.runningRelays.add(fingerprint);
        }
      }
    }
  }

  /* Step 3: perform lookups and calculate path selection
   * probabilities. */

  private void startReverseDomainNameLookups() {
    Map<String, Long> addressLastLookupTimes =
        new HashMap<String, Long>();
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      addressLastLookupTimes.put(nodeStatus.getAddress(),
          nodeStatus.getLastRdnsLookup());
    }
    this.reverseDomainNameResolver.setAddresses(addressLastLookupTimes);
    this.reverseDomainNameResolver.startReverseDomainNameLookups();
  }

  private SortedMap<String, LookupResult> geoIpLookupResults =
      new TreeMap<String, LookupResult>();

  private void lookUpCitiesAndASes() {
    SortedSet<String> addressStrings = new TreeSet<String>();
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      addressStrings.add(nodeStatus.getAddress());
    }
    if (addressStrings.isEmpty()) {
      log.error("No relay IP addresses to resolve to cities or "
          + "ASN.");
      return;
    }
    SortedMap<String, LookupResult> lookupResults =
        this.lookupService.lookup(addressStrings);
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      LookupResult lookupResult = lookupResults.get(
          nodeStatus.getAddress());
      if (lookupResult != null) {
        this.geoIpLookupResults.put(fingerprint, lookupResult);
        this.updatedNodes.add(fingerprint);
      }
    }
  }

  private SortedMap<String, Float>
      consensusWeightFractions = new TreeMap<String, Float>(),
      guardProbabilities = new TreeMap<String, Float>(),
      middleProbabilities = new TreeMap<String, Float>(),
      exitProbabilities = new TreeMap<String, Float>();

  private void calculatePathSelectionProbabilities() {
    boolean consensusContainsBandwidthWeights = false;
    double wgg = 0.0, wgd = 0.0, wmg = 0.0, wmm = 0.0, wme = 0.0,
        wmd = 0.0, wee = 0.0, wed = 0.0;
    if (this.lastBandwidthWeights != null) {
      SortedSet<String> weightKeys = new TreeSet<String>(Arrays.asList(
          "Wgg,Wgd,Wmg,Wmm,Wme,Wmd,Wee,Wed".split(",")));
      weightKeys.removeAll(this.lastBandwidthWeights.keySet());
      if (weightKeys.isEmpty()) {
        consensusContainsBandwidthWeights = true;
        wgg = ((double) this.lastBandwidthWeights.get("Wgg")) / 10000.0;
        wgd = ((double) this.lastBandwidthWeights.get("Wgd")) / 10000.0;
        wmg = ((double) this.lastBandwidthWeights.get("Wmg")) / 10000.0;
        wmm = ((double) this.lastBandwidthWeights.get("Wmm")) / 10000.0;
        wme = ((double) this.lastBandwidthWeights.get("Wme")) / 10000.0;
        wmd = ((double) this.lastBandwidthWeights.get("Wmd")) / 10000.0;
        wee = ((double) this.lastBandwidthWeights.get("Wee")) / 10000.0;
        wed = ((double) this.lastBandwidthWeights.get("Wed")) / 10000.0;
      }
    } else {
      log.error("Could not determine most recent Wxx parameter "
          + "values, probably because we didn't parse a consensus in "
          + "this execution.  All relays' guard/middle/exit weights are "
          + "going to be 0.0.");
    }
    SortedMap<String, Double>
        consensusWeights = new TreeMap<String, Double>(),
        guardWeights = new TreeMap<String, Double>(),
        middleWeights = new TreeMap<String, Double>(),
        exitWeights = new TreeMap<String, Double>();
    double totalConsensusWeight = 0.0;
    double totalGuardWeight = 0.0;
    double totalMiddleWeight = 0.0;
    double totalExitWeight = 0.0;
    for (String fingerprint : this.runningRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      boolean isExit = nodeStatus.getRelayFlags().contains("Exit") &&
          !nodeStatus.getRelayFlags().contains("BadExit");
      boolean isGuard = nodeStatus.getRelayFlags().contains("Guard");
      double consensusWeight = (double) nodeStatus.getConsensusWeight();
      consensusWeights.put(fingerprint, consensusWeight);
      totalConsensusWeight += consensusWeight;
      if (consensusContainsBandwidthWeights) {
        double guardWeight = consensusWeight,
            middleWeight = consensusWeight,
            exitWeight = consensusWeight;
        if (isGuard && isExit) {
          guardWeight *= wgd;
          middleWeight *= wmd;
          exitWeight *= wed;
        } else if (isGuard) {
          guardWeight *= wgg;
          middleWeight *= wmg;
          exitWeight = 0.0;
        } else if (isExit) {
          guardWeight = 0.0;
          middleWeight *= wme;
          exitWeight *= wee;
        } else {
          guardWeight = 0.0;
          middleWeight *= wmm;
          exitWeight = 0.0;
        }
        guardWeights.put(fingerprint, guardWeight);
        middleWeights.put(fingerprint, middleWeight);
        exitWeights.put(fingerprint, exitWeight);
        totalGuardWeight += guardWeight;
        totalMiddleWeight += middleWeight;
        totalExitWeight += exitWeight;
      }
    }
    for (String fingerprint : this.runningRelays) {
      if (consensusWeights.containsKey(fingerprint)) {
        this.consensusWeightFractions.put(fingerprint, (float)
            (consensusWeights.get(fingerprint) / totalConsensusWeight));
        this.updatedNodes.add(fingerprint);
      }
      if (guardWeights.containsKey(fingerprint)) {
        this.guardProbabilities.put(fingerprint, (float)
            (guardWeights.get(fingerprint) / totalGuardWeight));
        this.updatedNodes.add(fingerprint);
      }
      if (middleWeights.containsKey(fingerprint)) {
        this.middleProbabilities.put(fingerprint, (float)
            (middleWeights.get(fingerprint) / totalMiddleWeight));
        this.updatedNodes.add(fingerprint);
      }
      if (exitWeights.containsKey(fingerprint)) {
        this.exitProbabilities.put(fingerprint, (float)
            (exitWeights.get(fingerprint) / totalExitWeight));
        this.updatedNodes.add(fingerprint);
      }
    }
  }

  private long startedRdnsLookups = -1L;

  private SortedMap<String, String> rdnsLookupResults =
      new TreeMap<String, String>();

  private void finishReverseDomainNameLookups() {
    this.reverseDomainNameResolver.finishReverseDomainNameLookups();
    this.startedRdnsLookups =
        this.reverseDomainNameResolver.getLookupStartMillis();
    Map<String, String> lookupResults =
        this.reverseDomainNameResolver.getLookupResults();
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      String hostName = lookupResults.get(nodeStatus.getAddress());
      if (hostName != null) {
        this.rdnsLookupResults.put(fingerprint, hostName);
        this.updatedNodes.add(fingerprint);
      }
    }
  }

  /* Step 4: update details statuses and then node statuses. */

  private void updateNodeDetailsStatuses() {
    for (String fingerprint : this.updatedNodes) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      if (nodeStatus == null) {
        nodeStatus = new NodeStatus(fingerprint);
      }
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, true, fingerprint);
      if (detailsStatus == null) {
        detailsStatus = new DetailsStatus();
      }

      nodeStatus.setContact(detailsStatus.getContact());

      Map<String, Long> exitAddresses = new HashMap<String, Long>();
      if (detailsStatus.getExitAddresses() != null) {
        for (Map.Entry<String, Long> e :
            detailsStatus.getExitAddresses().entrySet()) {
          if (e.getValue() >= this.now - DateTimeHelper.ONE_DAY) {
            exitAddresses.put(e.getKey(), e.getValue());
          }
        }
      }
      if (this.exitListEntries.containsKey(fingerprint)) {
        for (Map.Entry<String, Long> e :
            this.exitListEntries.get(fingerprint).entrySet()) {
          String exitAddress = e.getKey();
          long scanMillis = e.getValue();
          if (!exitAddresses.containsKey(exitAddress) ||
              exitAddresses.get(exitAddress) < scanMillis) {
            exitAddresses.put(exitAddress, scanMillis);
          }
        }
      }
      detailsStatus.setExitAddresses(exitAddresses);
      SortedSet<String> exitAddressesWithoutOrAddresses =
          new TreeSet<String>(exitAddresses.keySet());
      exitAddressesWithoutOrAddresses.removeAll(
          nodeStatus.getOrAddresses());
      nodeStatus.setExitAddresses(exitAddressesWithoutOrAddresses);

      if (detailsStatus.getFamily() != null &&
          !detailsStatus.getFamily().isEmpty()) {
        SortedSet<String> familyFingerprints = new TreeSet<String>();
        for (String familyMember : detailsStatus.getFamily()) {
          if (familyMember.startsWith("$") &&
              familyMember.length() == 41) {
            familyFingerprints.add(familyMember.substring(1));
          }
        }
        if (!familyFingerprints.isEmpty()) {
          nodeStatus.setFamilyFingerprints(familyFingerprints);
        }
      }

      if (this.geoIpLookupResults.containsKey(fingerprint)) {
        LookupResult lookupResult = this.geoIpLookupResults.get(
            fingerprint);
        detailsStatus.setCountryCode(lookupResult.getCountryCode());
        detailsStatus.setCountryName(lookupResult.getCountryName());
        detailsStatus.setRegionName(lookupResult.getRegionName());
        detailsStatus.setCityName(lookupResult.getCityName());
        detailsStatus.setLatitude(lookupResult.getLatitude());
        detailsStatus.setLongitude(lookupResult.getLongitude());
        detailsStatus.setASNumber(lookupResult.getAsNumber());
        detailsStatus.setASName(lookupResult.getAsName());
        nodeStatus.setCountryCode(lookupResult.getCountryCode());
        nodeStatus.setASNumber(lookupResult.getAsNumber());
      }

      detailsStatus.setConsensusWeightFraction(
          this.consensusWeightFractions.get(fingerprint));
      detailsStatus.setGuardProbability(
          this.guardProbabilities.get(fingerprint));
      detailsStatus.setMiddleProbability(
          this.middleProbabilities.get(fingerprint));
      detailsStatus.setExitProbability(
          this.exitProbabilities.get(fingerprint));

      if (this.rdnsLookupResults.containsKey(fingerprint)) {
        String hostName = this.rdnsLookupResults.get(fingerprint);
        detailsStatus.setHostName(hostName);
        nodeStatus.setLastRdnsLookup(this.startedRdnsLookups);
      }

      detailsStatus.setRelay(nodeStatus.isRelay());
      detailsStatus.setRunning(nodeStatus.getLastSeenMillis() ==
          (nodeStatus.isRelay()
          ? this.relaysLastValidAfterMillis
          : this.bridgesLastPublishedMillis));
      detailsStatus.setNickname(nodeStatus.getNickname());
      detailsStatus.setAddress(nodeStatus.getAddress());
      detailsStatus.setOrAddressesAndPorts(
          nodeStatus.getOrAddressesAndPorts());
      detailsStatus.setFirstSeenMillis(nodeStatus.getFirstSeenMillis());
      detailsStatus.setLastSeenMillis(nodeStatus.getLastSeenMillis());
      detailsStatus.setOrPort(nodeStatus.getOrPort());
      detailsStatus.setDirPort(nodeStatus.getDirPort());
      detailsStatus.setRelayFlags(nodeStatus.getRelayFlags());
      detailsStatus.setConsensusWeight(nodeStatus.getConsensusWeight());
      detailsStatus.setDefaultPolicy(nodeStatus.getDefaultPolicy());
      detailsStatus.setPortList(nodeStatus.getPortList());
      detailsStatus.setRecommendedVersion(
          nodeStatus.getRecommendedVersion());
      detailsStatus.setLastChangedOrAddressOrPort(
          nodeStatus.getLastChangedOrAddressOrPort());

      this.documentStore.store(detailsStatus, fingerprint);
      this.documentStore.store(nodeStatus, fingerprint);
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        relayConsensusesProcessed) + " relay consensuses processed\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        bridgeStatusesProcessed) + " bridge statuses processed\n");
    return sb.toString();
  }
}

