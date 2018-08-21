/* Copyright 2011--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExitList;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

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
 *      GeoIP database, calculate path selection probabilities, and
 *      compute effective families, and update node statuses accordingly.
 *   4. Retrieve details statuses corresponding to nodes that have been
 *      changed since the start of the update process, possibly update the
 *      node statuses with contents from newly parsed descriptors, update
 *      details statuses with results from lookup operations, new path
 *      selection probabilities, and effective families, and store details
 *      statuses and node statuses back to disk.
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

  private SortedMap<String, NodeStatus> knownNodes = new TreeMap<>();

  private long relaysLastValidAfterMillis = -1L;

  private long bridgesLastPublishedMillis = -1L;

  private SortedMap<String, Integer> lastBandwidthWeights = null;

  private SortedSet<TorVersion> lastRecommendedServerVersions = null;

  private int relayConsensusesProcessed = 0;

  private int bridgeStatusesProcessed = 0;

  /** Initializes a new status updater, obtains references to all relevant
   * singleton instances, and registers as listener at the (singleton)
   * descriptor source. */
  public NodeDetailsStatusUpdater(
      ReverseDomainNameResolver reverseDomainNameResolver,
      LookupService lookupService) {
    this.descriptorSource = DescriptorSourceFactory.getDescriptorSource();
    this.reverseDomainNameResolver = reverseDomainNameResolver;
    this.lookupService = lookupService;
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.now = System.currentTimeMillis();
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

  @Override
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

  private Map<String, SortedSet<String>> declaredFamilies = new HashMap<>();

  private void processRelayServerDescriptor(
      ServerDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, true, fingerprint);
    if (detailsStatus == null) {
      detailsStatus = new DetailsStatus();
    } else if (detailsStatus.getDescPublished() != null
        && detailsStatus.getDescPublished()
        >= descriptor.getPublishedMillis()) {
      /* Already parsed more recent server descriptor from this relay. */
      return;
    }
    int bandwidthRate = descriptor.getBandwidthRate();
    int bandwidthBurst = descriptor.getBandwidthBurst();
    int observedBandwidth = descriptor.getBandwidthObserved();
    int advertisedBandwidth = Math.min(bandwidthRate,
        Math.min(bandwidthBurst, observedBandwidth));
    detailsStatus.setDescPublished(descriptor.getPublishedMillis());
    detailsStatus.setLastRestarted(calculateLastRestartedMillis(descriptor));
    detailsStatus.setBandwidthRate(bandwidthRate);
    detailsStatus.setBandwidthBurst(bandwidthBurst);
    detailsStatus.setObservedBandwidth(observedBandwidth);
    detailsStatus.setAdvertisedBandwidth(advertisedBandwidth);
    detailsStatus.setExitPolicy(descriptor.getExitPolicyLines());
    detailsStatus.setContact(descriptor.getContact());
    detailsStatus.setPlatform(descriptor.getPlatform());
    if (descriptor.getFamilyEntries() != null) {
      SortedSet<String> declaredFamily = new TreeSet<>();
      for (String familyMember : descriptor.getFamilyEntries()) {
        if (familyMember.startsWith("$") && familyMember.length() >= 41) {
          declaredFamily.add(
              familyMember.substring(1, 41).toUpperCase());
        } else {
          declaredFamily.add(familyMember);
        }
      }
      this.declaredFamilies.put(fingerprint, declaredFamily);
    }
    if (descriptor.getIpv6DefaultPolicy() != null
        && (descriptor.getIpv6DefaultPolicy().equals("accept")
        || descriptor.getIpv6DefaultPolicy().equals("reject"))
        && descriptor.getIpv6PortList() != null) {
      Map<String, List<String>> exitPolicyV6Summary = new HashMap<>();
      List<String> portsOrPortRanges = Arrays.asList(
          descriptor.getIpv6PortList().split(","));
      exitPolicyV6Summary.put(descriptor.getIpv6DefaultPolicy(),
          portsOrPortRanges);
      detailsStatus.setExitPolicyV6Summary(exitPolicyV6Summary);
    } else {
      detailsStatus.setExitPolicyV6Summary(null);
    }
    detailsStatus.setHibernating(descriptor.isHibernating() ? true :
        null);
    detailsStatus.setAdvertisedOrAddresses(descriptor.getOrAddresses());
    this.documentStore.store(detailsStatus, fingerprint);
  }

  private Long calculateLastRestartedMillis(ServerDescriptor descriptor) {
    Long lastRestartedMillis = null;
    if (null != descriptor.getUptime()) {
      lastRestartedMillis = descriptor.getPublishedMillis()
          - descriptor.getUptime() * DateTimeHelper.ONE_SECOND;
    }
    return lastRestartedMillis;
  }

  private Map<String, Map<String, Long>> exitListEntries = new HashMap<>();

  private void processExitList(ExitList exitList) {
    for (ExitList.Entry exitListEntry : exitList.getEntries()) {
      String fingerprint = exitListEntry.getFingerprint();
      for (Map.Entry<String, Long> exitAddressScanMillis
          : exitListEntry.getExitAddresses().entrySet()) {
        long scanMillis = exitAddressScanMillis.getValue();
        if (scanMillis < this.now - DateTimeHelper.ONE_DAY) {
          continue;
        }
        if (!this.exitListEntries.containsKey(fingerprint)) {
          this.exitListEntries.put(fingerprint, new HashMap<>());
        }
        String exitAddress = exitAddressScanMillis.getKey();
        if (!this.exitListEntries.get(fingerprint).containsKey(
            exitAddress)
            || this.exitListEntries.get(fingerprint).get(exitAddress)
            < scanMillis) {
          this.exitListEntries.get(fingerprint).put(exitAddress,
              scanMillis);
        }
      }
    }
  }

  private Map<String, Long> lastSeenUnmeasured = new HashMap<>();

  private Map<String, Long> lastSeenMeasured = new HashMap<>();

  private void processRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    long validAfterMillis = consensus.getValidAfterMillis();
    if (validAfterMillis > this.relaysLastValidAfterMillis) {
      this.relaysLastValidAfterMillis = validAfterMillis;
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
      SortedSet<String> orAddressesAndPorts = new TreeSet<>(
          entry.getOrAddresses());
      nodeStatus.addLastAddresses(validAfterMillis, address, orPort,
          dirPort, orAddressesAndPorts);
      if (nodeStatus.getFirstSeenMillis() == 0L
          || validAfterMillis < nodeStatus.getFirstSeenMillis()) {
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
        String version = null;
        if (null != entry.getVersion()
            && entry.getVersion().startsWith("Tor ")) {
          version = entry.getVersion().split(" ")[1];
        }
        nodeStatus.setVersion(version);
      }
      if (entry.getUnmeasured()) {
        if (!this.lastSeenUnmeasured.containsKey(fingerprint)
            || this.lastSeenUnmeasured.get(fingerprint)
            < validAfterMillis) {
          this.lastSeenUnmeasured.put(fingerprint, validAfterMillis);
        }
      } else if (consensus.getConsensusMethod() >= 17) {
        if (!this.lastSeenMeasured.containsKey(fingerprint)
            || this.lastSeenMeasured.get(fingerprint)
            < validAfterMillis) {
          this.lastSeenMeasured.put(fingerprint, validAfterMillis);
        }
      }
    }
    this.relayConsensusesProcessed++;
    if (this.relaysLastValidAfterMillis == validAfterMillis) {
      this.lastBandwidthWeights = consensus.getBandwidthWeights();
      this.lastRecommendedServerVersions = new TreeSet<>();
      for (String recommendedServerVersion :
          consensus.getRecommendedServerVersions()) {
        TorVersion recommendedTorServerVersion
            = TorVersion.of(recommendedServerVersion);
        if (null != recommendedTorServerVersion) {
          this.lastRecommendedServerVersions.add(recommendedTorServerVersion);
        }
      }
    }
  }

  private void processBridgeServerDescriptor(
      ServerDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, true, fingerprint);
    if (detailsStatus == null) {
      detailsStatus = new DetailsStatus();
    } else if (detailsStatus.getDescPublished() != null
        && detailsStatus.getDescPublished()
        >= descriptor.getPublishedMillis()) {
      /* Already parsed more recent server descriptor from this bridge. */
      return;
    }
    int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
        Math.min(descriptor.getBandwidthBurst(),
        descriptor.getBandwidthObserved()));
    detailsStatus.setDescPublished(descriptor.getPublishedMillis());
    detailsStatus.setLastRestarted(calculateLastRestartedMillis(descriptor));
    detailsStatus.setAdvertisedBandwidth(advertisedBandwidth);
    detailsStatus.setPlatform(descriptor.getPlatform());
    this.documentStore.store(detailsStatus, fingerprint);
  }

  private void processBridgeExtraInfoDescriptor(
      ExtraInfoDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, true, fingerprint);
    if (detailsStatus == null) {
      detailsStatus = new DetailsStatus();
    } else if (null != detailsStatus.getExtraInfoDescPublished()
        && detailsStatus.getExtraInfoDescPublished()
        >= descriptor.getPublishedMillis()) {
      /* Already parsed more recent extra-info descriptor from this bridge. */
      return;
    }
    detailsStatus.setExtraInfoDescPublished(
        descriptor.getPublishedMillis());
    detailsStatus.setTransports(descriptor.getTransports());
    this.documentStore.store(detailsStatus, fingerprint);
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
      if (nodeStatus.getFirstSeenMillis() == 0L
          || publishedMillis < nodeStatus.getFirstSeenMillis()) {
        nodeStatus.setFirstSeenMillis(publishedMillis);
      }
      if (publishedMillis > nodeStatus.getLastSeenMillis()) {
        nodeStatus.setRelay(false);
        nodeStatus.setNickname(entry.getNickname());
        nodeStatus.setAddress(entry.getAddress());
        nodeStatus.setOrAddressesAndPorts(new TreeSet<>(
            entry.getOrAddresses()));
        nodeStatus.setOrPort(entry.getOrPort());
        nodeStatus.setDirPort(entry.getDirPort());
        nodeStatus.setRelayFlags(entry.getFlags());
        nodeStatus.setLastSeenMillis(publishedMillis);
      }
    }
    this.bridgeStatusesProcessed++;
  }

  @Override
  public void updateStatuses() {
    this.readNodeStatuses();
    log.info("Read node statuses");
    this.startReverseDomainNameLookups();
    log.info("Started reverse domain name lookups");
    this.lookUpCitiesAndASes();
    log.info("Looked up cities and ASes");
    this.calculatePathSelectionProbabilities();
    log.info("Calculated path selection probabilities");
    this.computeEffectiveAndExtendedFamilies();
    log.info("Computed effective and extended families");
    this.finishReverseDomainNameLookups();
    log.info("Finished reverse domain name lookups");
    this.updateNodeDetailsStatuses();
    log.info("Updated node and details statuses");
  }

  /* Step 2: read node statuses from disk. */

  private SortedSet<String> currentRelays = new TreeSet<>();

  private SortedSet<String> runningRelays = new TreeSet<>();

  private void readNodeStatuses() {
    SortedSet<String> previouslyKnownNodes = this.documentStore.list(
        NodeStatus.class);
    long previousRelaysLastValidAfterMillis = -1L;
    long previousBridgesLastValidAfterMillis = -1L;
    for (String fingerprint : previouslyKnownNodes) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      if (nodeStatus.isRelay() && nodeStatus.getLastSeenMillis()
          > previousRelaysLastValidAfterMillis) {
        previousRelaysLastValidAfterMillis =
            nodeStatus.getLastSeenMillis();
      } else if (!nodeStatus.isRelay() && nodeStatus.getLastSeenMillis()
          > previousBridgesLastValidAfterMillis) {
        previousBridgesLastValidAfterMillis =
            nodeStatus.getLastSeenMillis();
      }
    }
    if (previousRelaysLastValidAfterMillis
        > this.relaysLastValidAfterMillis) {
      this.relaysLastValidAfterMillis =
          previousRelaysLastValidAfterMillis;
    }
    if (previousBridgesLastValidAfterMillis
        > this.bridgesLastPublishedMillis) {
      this.bridgesLastPublishedMillis =
          previousBridgesLastValidAfterMillis;
    }
    long cutoff = Math.max(this.relaysLastValidAfterMillis,
        this.bridgesLastPublishedMillis) - DateTimeHelper.ONE_WEEK;
    for (Map.Entry<String, NodeStatus> e : this.knownNodes.entrySet()) {
      String fingerprint = e.getKey();
      NodeStatus nodeStatus = e.getValue();
      if (nodeStatus.isRelay()
          && nodeStatus.getLastSeenMillis() >= cutoff) {
        this.currentRelays.add(fingerprint);
        if (nodeStatus.getLastSeenMillis()
            == this.relaysLastValidAfterMillis) {
          this.runningRelays.add(fingerprint);
        }
      }
    }
    for (String fingerprint : previouslyKnownNodes) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      NodeStatus updatedNodeStatus;
      if (this.knownNodes.containsKey(fingerprint)) {
        updatedNodeStatus = this.knownNodes.get(fingerprint);
        String address = nodeStatus.getAddress();
        if (address.equals(updatedNodeStatus.getAddress())) {
          /* Only remember the last lookup time if the address has not
           * changed.  Otherwise we'll want to do a fresh lookup. */
          updatedNodeStatus.setLastRdnsLookup(
              nodeStatus.getLastRdnsLookup());
        }
        int orPort = nodeStatus.getOrPort();
        int dirPort = nodeStatus.getDirPort();
        SortedSet<String> orAddressesAndPorts =
            nodeStatus.getOrAddressesAndPorts();
        updatedNodeStatus.addLastAddresses(
            nodeStatus.getLastChangedOrAddressOrPort(), address, orPort,
            dirPort, orAddressesAndPorts);
        if (nodeStatus.getLastSeenMillis()
            > updatedNodeStatus.getLastSeenMillis()) {
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
          updatedNodeStatus.setAsNumber(nodeStatus.getAsNumber());
          updatedNodeStatus.setAsName(nodeStatus.getAsName());
          updatedNodeStatus.setRecommendedVersion(
              nodeStatus.getRecommendedVersion());
          updatedNodeStatus.setVersion(nodeStatus.getVersion());
        }
        if (nodeStatus.getFirstSeenMillis()
            < updatedNodeStatus.getFirstSeenMillis()
            && nodeStatus.getFirstSeenMillis() > 0L) {
          updatedNodeStatus.setFirstSeenMillis(
              nodeStatus.getFirstSeenMillis());
        }
        updatedNodeStatus.setDeclaredFamily(
            nodeStatus.getDeclaredFamily());
        updatedNodeStatus.setEffectiveFamily(
            nodeStatus.getEffectiveFamily());
        updatedNodeStatus.setExtendedFamily(
            nodeStatus.getExtendedFamily());
      } else {
        updatedNodeStatus = nodeStatus;
        this.knownNodes.put(fingerprint, nodeStatus);
      }
      if (updatedNodeStatus.isRelay()
          && updatedNodeStatus.getLastSeenMillis() >= cutoff) {
        this.currentRelays.add(fingerprint);
        if (updatedNodeStatus.getLastSeenMillis()
            == this.relaysLastValidAfterMillis) {
          this.runningRelays.add(fingerprint);
        }
      }
    }
    /* Update family fingerprints in known nodes with any fingerprints we
     * learned when parsing server descriptors in this run.  These are
     * guaranteed to come from more recent server descriptors, so it's
     * safe to override whatever is in node statuses. */
    for (Map.Entry<String, NodeStatus> e : this.knownNodes.entrySet()) {
      String fingerprint = e.getKey();
      if (this.declaredFamilies.containsKey(fingerprint)) {
        NodeStatus nodeStatus = e.getValue();
        nodeStatus.setDeclaredFamily(
            this.declaredFamilies.get(fingerprint));
      }
    }
    this.declaredFamilies.clear();
  }

  /* Step 3: perform lookups and calculate path selection
   * probabilities. */

  private void startReverseDomainNameLookups() {
    Map<String, Long> addressLastLookupTimes = new HashMap<>();
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      addressLastLookupTimes.put(nodeStatus.getAddress(),
          nodeStatus.getLastRdnsLookup());
    }
    this.reverseDomainNameResolver.setAddresses(addressLastLookupTimes);
    this.reverseDomainNameResolver.startReverseDomainNameLookups();
  }

  private SortedMap<String, LookupResult> geoIpLookupResults = new TreeMap<>();

  private void lookUpCitiesAndASes() {
    SortedSet<String> addressStrings = new TreeSet<>();
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
      }
    }
  }

  private SortedMap<String, Float> consensusWeightFractions = new TreeMap<>();

  private SortedMap<String, Float> guardProbabilities = new TreeMap<>();

  private SortedMap<String, Float> middleProbabilities = new TreeMap<>();

  private SortedMap<String, Float> exitProbabilities = new TreeMap<>();

  private void calculatePathSelectionProbabilities() {
    boolean consensusContainsBandwidthWeights = false;
    double wgg = 0.0;
    double wgd = 0.0;
    double wmg = 0.0;
    double wmm = 0.0;
    double wme = 0.0;
    double wmd = 0.0;
    double wee = 0.0;
    double wed = 0.0;
    if (this.lastBandwidthWeights != null) {
      SortedSet<String> weightKeys = new TreeSet<>(Arrays.asList(
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
      log.debug("Not calculating new path selection probabilities, "
          + "because we could not determine most recent Wxx parameter "
          + "values, probably because we didn't parse a consensus in "
          + "this execution.");
      return;
    }
    SortedMap<String, Double> consensusWeights = new TreeMap<>();
    SortedMap<String, Double> guardWeights = new TreeMap<>();
    SortedMap<String, Double> middleWeights = new TreeMap<>();
    SortedMap<String, Double> exitWeights = new TreeMap<>();
    double totalConsensusWeight = 0.0;
    double totalGuardWeight = 0.0;
    double totalMiddleWeight = 0.0;
    double totalExitWeight = 0.0;
    for (String fingerprint : this.runningRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      boolean isExit = nodeStatus.getRelayFlags().contains("Exit")
          && !nodeStatus.getRelayFlags().contains("BadExit");
      boolean isGuard = nodeStatus.getRelayFlags().contains("Guard");
      double consensusWeight = nodeStatus.getConsensusWeight();
      consensusWeights.put(fingerprint, consensusWeight);
      totalConsensusWeight += consensusWeight;
      if (consensusContainsBandwidthWeights) {
        double guardWeight = consensusWeight;
        double middleWeight = consensusWeight;
        double exitWeight = consensusWeight;
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
      }
      if (guardWeights.containsKey(fingerprint)) {
        this.guardProbabilities.put(fingerprint, (float)
            (guardWeights.get(fingerprint) / totalGuardWeight));
      }
      if (middleWeights.containsKey(fingerprint)) {
        this.middleProbabilities.put(fingerprint, (float)
            (middleWeights.get(fingerprint) / totalMiddleWeight));
      }
      if (exitWeights.containsKey(fingerprint)) {
        this.exitProbabilities.put(fingerprint, (float)
            (exitWeights.get(fingerprint) / totalExitWeight));
      }
    }
  }

  private void computeEffectiveAndExtendedFamilies() {
    SortedMap<String, SortedSet<String>> declaredFamilies = new TreeMap<>();
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      if (nodeStatus != null && nodeStatus.getDeclaredFamily() != null
          && !nodeStatus.getDeclaredFamily().isEmpty()) {
        declaredFamilies.put(fingerprint, nodeStatus.getDeclaredFamily());
      }
    }
    SortedMap<String, SortedSet<String>> effectiveFamilies = new TreeMap<>();
    for (Map.Entry<String, SortedSet<String>> e :
        declaredFamilies.entrySet()) {
      String fingerprint = e.getKey();
      SortedSet<String> declaredFamily = e.getValue();
      SortedSet<String> effectiveFamily = new TreeSet<>();
      for (String declaredFamilyMember : declaredFamily) {
        if (declaredFamilies.containsKey(declaredFamilyMember)
            && declaredFamilies.get(declaredFamilyMember).contains(
            fingerprint)) {
          effectiveFamily.add(declaredFamilyMember);
        }
      }
      if (!effectiveFamily.isEmpty()) {
        effectiveFamilies.put(fingerprint, effectiveFamily);
      }
    }
    SortedMap<String, SortedSet<String>> extendedFamilies = new TreeMap<>();
    SortedSet<String> visited = new TreeSet<>();
    for (String fingerprint : effectiveFamilies.keySet()) {
      if (visited.contains(fingerprint)) {
        continue;
      }
      SortedSet<String> toVisit = new TreeSet<>();
      toVisit.add(fingerprint);
      SortedSet<String> extendedFamily = new TreeSet<>();
      while (!toVisit.isEmpty()) {
        String visiting = toVisit.first();
        toVisit.remove(visiting);
        extendedFamily.add(visiting);
        SortedSet<String> members = effectiveFamilies.get(visiting);
        if (members != null) {
          for (String member : members) {
            if (!toVisit.contains(member) && !visited.contains(member)) {
              toVisit.add(member);
            }
          }
        }
        visited.add(visiting);
      }
      if (extendedFamily.size() > 1) {
        for (String member : extendedFamily) {
          SortedSet<String> extendedFamilyWithoutMember =
              new TreeSet<>(extendedFamily);
          extendedFamilyWithoutMember.remove(member);
          extendedFamilies.put(member, extendedFamilyWithoutMember);
        }
      }
    }
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      if (effectiveFamilies.containsKey(fingerprint)
          || extendedFamilies.containsKey(fingerprint)) {
        nodeStatus.setEffectiveFamily(effectiveFamilies.get(fingerprint));
        nodeStatus.setExtendedFamily(extendedFamilies.get(fingerprint));
      } else if ((nodeStatus.getEffectiveFamily() != null
          && !nodeStatus.getEffectiveFamily().isEmpty())
          || (nodeStatus.getIndirectFamily() != null
          && !nodeStatus.getIndirectFamily().isEmpty())) {
        nodeStatus.setEffectiveFamily(null);
        nodeStatus.setExtendedFamily(null);
      }
    }
  }

  private long startedRdnsLookups = -1L;

  private SortedMap<String, SortedSet<String>> rdnsVerifiedLookupResults =
      new TreeMap<>();
  private SortedMap<String, SortedSet<String>> rdnsUnverifiedLookupResults =
      new TreeMap<>();

  private void finishReverseDomainNameLookups() {
    this.reverseDomainNameResolver.finishReverseDomainNameLookups();
    this.startedRdnsLookups =
        this.reverseDomainNameResolver.getLookupStartMillis();
    Map<String, SortedSet<String>> verifiedLookupResults =
        this.reverseDomainNameResolver.getVerifiedLookupResults();
    Map<String, SortedSet<String>> unverifiedLookupResults =
        this.reverseDomainNameResolver.getUnverifiedLookupResults();
    for (String fingerprint : this.currentRelays) {
      NodeStatus nodeStatus = this.knownNodes.get(fingerprint);
      SortedSet<String> verifiedHostNames =
              verifiedLookupResults.get(nodeStatus.getAddress());
      SortedSet<String> unverifiedHostNames =
              unverifiedLookupResults.get(nodeStatus.getAddress());
      if (null != verifiedHostNames && !verifiedHostNames.isEmpty()) {
        this.rdnsVerifiedLookupResults.put(fingerprint, verifiedHostNames);
      }
      if (null != unverifiedHostNames && !unverifiedHostNames.isEmpty()) {
        this.rdnsUnverifiedLookupResults.put(fingerprint, unverifiedHostNames);
      }
    }
  }

  /* Step 4: update details statuses and then node statuses. */

  private void updateNodeDetailsStatuses() {
    for (Map.Entry<String, NodeStatus> entry : this.knownNodes.entrySet()) {
      String fingerprint = entry.getKey();
      NodeStatus nodeStatus = entry.getValue();
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, true, fingerprint);
      if (detailsStatus == null) {
        detailsStatus = new DetailsStatus();
      }

      nodeStatus.setContact(detailsStatus.getContact());
      if (null != detailsStatus.getPlatform()) {
        String[] platformParts = detailsStatus.getPlatform().split(" on ");
        if (platformParts.length > 1) {
          nodeStatus.setOperatingSystem(platformParts[1].toLowerCase());
        }
      }

      /* Extract tor software version for bridges from their "platform" line.
       * (We already know this for relays from "v" lines in the consensus.) */
      if (!nodeStatus.isRelay()) {
        String version = null;
        if (null != detailsStatus.getPlatform()
            && detailsStatus.getPlatform().startsWith("Tor ")) {
          version = detailsStatus.getPlatform().split(" ")[1];
        }
        nodeStatus.setVersion(version);
      }

      /* Compare tor software version (for relays and bridges) with the
       * recommended-server-versions line in the last known consensus and set
       * the recommended_version field accordingly. */
      if (null != this.lastRecommendedServerVersions
          && null != nodeStatus.getVersion()) {
        TorVersion torVersion = TorVersion.of(nodeStatus.getVersion());
        nodeStatus.setRecommendedVersion(this.lastRecommendedServerVersions
            .contains(torVersion));
        nodeStatus.setVersionStatus(null != torVersion
            ? torVersion.determineVersionStatus(
                this.lastRecommendedServerVersions)
            : TorVersionStatus.UNRECOMMENDED);
      }

      Map<String, Long> exitAddresses = new HashMap<>();
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
          if (!exitAddresses.containsKey(exitAddress)
              || exitAddresses.get(exitAddress) < scanMillis) {
            exitAddresses.put(exitAddress, scanMillis);
          }
        }
      }
      detailsStatus.setExitAddresses(exitAddresses);
      nodeStatus.setExitAddresses(new TreeSet<>(exitAddresses.keySet()));

      detailsStatus.setAllegedFamily(nodeStatus.getAllegedFamily());
      detailsStatus.setEffectiveFamily(nodeStatus.getEffectiveFamily());
      detailsStatus.setIndirectFamily(nodeStatus.getIndirectFamily());

      if (this.geoIpLookupResults.containsKey(fingerprint)) {
        LookupResult lookupResult = this.geoIpLookupResults.get(
            fingerprint);
        detailsStatus.setCountryCode(lookupResult.getCountryCode());
        detailsStatus.setCountryName(lookupResult.getCountryName());
        detailsStatus.setRegionName(lookupResult.getRegionName());
        detailsStatus.setCityName(lookupResult.getCityName());
        detailsStatus.setLatitude(lookupResult.getLatitude());
        detailsStatus.setLongitude(lookupResult.getLongitude());
        detailsStatus.setAsNumber(lookupResult.getAsNumber());
        detailsStatus.setAsName(lookupResult.getAsName());
        nodeStatus.setCountryCode(lookupResult.getCountryCode());
        nodeStatus.setAsNumber(lookupResult.getAsNumber());
        if (null != lookupResult.getAsName()) {
          nodeStatus.setAsName(lookupResult.getAsName().toLowerCase());
        }
      }

      if (this.consensusWeightFractions.containsKey(fingerprint)) {
        detailsStatus.setConsensusWeightFraction(
            this.consensusWeightFractions.get(fingerprint));
      }
      if (this.guardProbabilities.containsKey(fingerprint)) {
        detailsStatus.setGuardProbability(
            this.guardProbabilities.get(fingerprint));
      }
      if (this.middleProbabilities.containsKey(fingerprint)) {
        detailsStatus.setMiddleProbability(
            this.middleProbabilities.get(fingerprint));
      }
      if (this.exitProbabilities.containsKey(fingerprint)) {
        detailsStatus.setExitProbability(
            this.exitProbabilities.get(fingerprint));
      }

      if (this.rdnsVerifiedLookupResults.containsKey(fingerprint)) {
        SortedSet<String> verifiedHostNames =
            this.rdnsVerifiedLookupResults.get(fingerprint);
        detailsStatus.setVerifiedHostNames(verifiedHostNames);
        nodeStatus.setVerifiedHostNames(verifiedHostNames);
        nodeStatus.setLastRdnsLookup(this.startedRdnsLookups);
      }

      if (this.rdnsUnverifiedLookupResults.containsKey(fingerprint)) {
        SortedSet<String> unverifiedHostNames =
            this.rdnsUnverifiedLookupResults.get(fingerprint);
        detailsStatus.setUnverifiedHostNames(unverifiedHostNames);
        nodeStatus.setUnverifiedHostNames(unverifiedHostNames);
        nodeStatus.setLastRdnsLookup(this.startedRdnsLookups);
      }

      if (detailsStatus.getLastSeenMillis()
          < nodeStatus.getLastSeenMillis()) {
        if (this.lastSeenMeasured.containsKey(fingerprint)) {
          if (this.lastSeenUnmeasured.containsKey(fingerprint)
              && this.lastSeenUnmeasured.get(fingerprint)
              > this.lastSeenMeasured.get(fingerprint)) {
            detailsStatus.setMeasured(false);
          } else {
            detailsStatus.setMeasured(true);
          }
        } else if (this.lastSeenUnmeasured.containsKey(fingerprint)) {
          detailsStatus.setMeasured(false);
        }
      }

      detailsStatus.setRelay(nodeStatus.isRelay());
      detailsStatus.setRunning(nodeStatus.getRelayFlags().contains("Running")
          && nodeStatus.getLastSeenMillis()
          == (nodeStatus.isRelay() ? this.relaysLastValidAfterMillis
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
      detailsStatus.setVersion(nodeStatus.getVersion());
      if (null != nodeStatus.getVersionStatus()) {
        detailsStatus.setVersionStatus(nodeStatus.getVersionStatus()
            .toString());
      }

      this.documentStore.store(detailsStatus, fingerprint);
      this.documentStore.store(nodeStatus, fingerprint);
    }
  }

  @Override
  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        relayConsensusesProcessed)).append(" relay consensuses processed\n");
    sb.append("    ").append(FormattingUtils.formatDecimalNumber(
        bridgeStatusesProcessed)).append(" bridge statuses processed\n");
    return sb.toString();
  }
}

