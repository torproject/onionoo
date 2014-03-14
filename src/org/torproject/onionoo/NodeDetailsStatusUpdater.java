/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.StringEscapeUtils;
import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.BridgePoolAssignment;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;
import org.torproject.onionoo.LookupService.LookupResult;

public class NodeDetailsStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private DescriptorSource descriptorSource;

  private ReverseDomainNameResolver reverseDomainNameResolver;

  private LookupService lookupService;

  private DocumentStore documentStore;

  private long now;

  private SortedMap<String, NodeStatus> knownNodes =
      new TreeMap<String, NodeStatus>();

  private SortedMap<String, NodeStatus> relays;

  private SortedMap<String, NodeStatus> bridges;

  private long relaysLastValidAfterMillis = -1L;

  private long bridgesLastPublishedMillis = -1L;

  private SortedMap<String, Integer> lastBandwidthWeights = null;

  private int relayConsensusesProcessed = 0, bridgeStatusesProcessed = 0;

  public NodeDetailsStatusUpdater(DescriptorSource descriptorSource,
      ReverseDomainNameResolver reverseDomainNameResolver,
      LookupService lookupService, DocumentStore documentStore,
      Time time) {
    this.descriptorSource = descriptorSource;
    this.reverseDomainNameResolver = reverseDomainNameResolver;
    this.lookupService = lookupService;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
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
        DescriptorType.BRIDGE_POOL_ASSIGNMENTS);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.EXIT_LISTS);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof RelayNetworkStatusConsensus) {
      this.processRelayNetworkStatusConsensus(
          (RelayNetworkStatusConsensus) descriptor);
    } else if (descriptor instanceof ServerDescriptor && relay) {
      this.processRelayServerDescriptor((ServerDescriptor) descriptor);
    } else if (descriptor instanceof BridgeNetworkStatus) {
      this.processBridgeNetworkStatus((BridgeNetworkStatus) descriptor);
    } else if (descriptor instanceof ServerDescriptor && !relay) {
      this.processBridgeServerDescriptor((ServerDescriptor) descriptor);
    } else if (descriptor instanceof BridgePoolAssignment) {
      this.processBridgePoolAssignment((BridgePoolAssignment) descriptor);
    }
  }

  public void updateStatuses() {
    this.readStatusSummary();
    Logger.printStatusTime("Read status summary");
    this.setCurrentNodes();
    Logger.printStatusTime("Set current node fingerprints");
    this.startReverseDomainNameLookups();
    Logger.printStatusTime("Started reverse domain name lookups");
    this.lookUpCitiesAndASes();
    Logger.printStatusTime("Looked up cities and ASes");
    this.setRunningBits();
    Logger.printStatusTime("Set running bits");
    this.calculatePathSelectionProbabilities();
    Logger.printStatusTime("Calculated path selection probabilities");
    this.finishReverseDomainNameLookups();
    Logger.printStatusTime("Finished reverse domain name lookups");
    this.writeStatusSummary();
    Logger.printStatusTime("Wrote status summary");
    /* TODO Does anything break if we take the following out?
     * Like, does DocumentStore make sure there's a status/summary with
     * all node statuses and an out/summary with only recent ones?
    this.writeOutSummary();
    Logger.printStatusTime("Wrote out summary");*/
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
      Boolean recommendedVersion = (recommendedVersions == null ||
          entry.getVersion() == null) ? null :
          recommendedVersions.contains(entry.getVersion());
      NodeStatus newNodeStatus = new NodeStatus(true, nickname,
          fingerprint, address, orAddressesAndPorts, null,
          validAfterMillis, orPort, dirPort, relayFlags, consensusWeight,
          null, null, -1L, defaultPolicy, portList, validAfterMillis,
          validAfterMillis, null, null, recommendedVersion);
      if (this.knownNodes.containsKey(fingerprint)) {
        this.knownNodes.get(fingerprint).update(newNodeStatus);
      } else {
        this.knownNodes.put(fingerprint, newNodeStatus);
      }
    }
    this.relayConsensusesProcessed++;
    if (this.relaysLastValidAfterMillis == validAfterMillis) {
      this.lastBandwidthWeights = consensus.getBandwidthWeights();
    }
  }

  private void processBridgeNetworkStatus(BridgeNetworkStatus status) {
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
          -1L, null, null, publishedMillis, -1L, null, null, null);
      if (this.knownNodes.containsKey(fingerprint)) {
        this.knownNodes.get(fingerprint).update(newNodeStatus);
      } else {
        this.knownNodes.put(fingerprint, newNodeStatus);
      }
    }
    this.bridgeStatusesProcessed++;
  }

  private void readStatusSummary() {
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
      if (this.knownNodes.containsKey(fingerprint)) {
        this.knownNodes.get(fingerprint).update(node);
      } else {
        this.knownNodes.put(fingerprint, node);
      }
    }
  }

  private void setRunningBits() {
    for (NodeStatus node : this.knownNodes.values()) {
      if (node.isRelay() && node.getRelayFlags().contains("Running") &&
          node.getLastSeenMillis() == this.relaysLastValidAfterMillis) {
        node.setRunning(true);
      }
      if (!node.isRelay() && node.getRelayFlags().contains("Running") &&
          node.getLastSeenMillis() == this.bridgesLastPublishedMillis) {
        node.setRunning(true);
      }
    }
  }

  private void lookUpCitiesAndASes() {
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

  private void writeStatusSummary() {
    this.writeSummary(true);
  }

  private void writeSummary(boolean includeArchive) {
    SortedMap<String, NodeStatus> nodes = includeArchive
        ? this.knownNodes : this.getCurrentNodes();
    for (Map.Entry<String, NodeStatus> e : nodes.entrySet()) {
      this.documentStore.store(e.getValue(), e.getKey());
    }
  }

  private SortedMap<String, NodeStatus> getCurrentNodes() {
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

  private void processRelayServerDescriptor(
      ServerDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, false, fingerprint);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String publishedDateTime =
        dateTimeFormat.format(descriptor.getPublishedMillis());
    if (detailsStatus != null) {
      String detailsString = detailsStatus.documentString;
      String descPublishedLine = "\"desc_published\":\""
          + publishedDateTime + "\",";
      Scanner s = new Scanner(detailsString);
      while (s.hasNextLine()) {
        String line = s.nextLine();
        if (line.startsWith("\"desc_published\":\"")) {
          if (descPublishedLine.compareTo(line) < 0) {
            return;
          } else {
            break;
          }
        }
      }
      s.close();
    }
    StringBuilder sb = new StringBuilder();
    String lastRestartedString = dateTimeFormat.format(
        descriptor.getPublishedMillis() - descriptor.getUptime() * 1000L);
    int bandwidthRate = descriptor.getBandwidthRate();
    int bandwidthBurst = descriptor.getBandwidthBurst();
    int observedBandwidth = descriptor.getBandwidthObserved();
    int advertisedBandwidth = Math.min(bandwidthRate,
        Math.min(bandwidthBurst, observedBandwidth));
    sb.append("\"desc_published\":\"" + publishedDateTime + "\",\n"
        + "\"last_restarted\":\"" + lastRestartedString + "\",\n"
        + "\"bandwidth_rate\":" + bandwidthRate + ",\n"
        + "\"bandwidth_burst\":" + bandwidthBurst + ",\n"
        + "\"observed_bandwidth\":" + observedBandwidth + ",\n"
        + "\"advertised_bandwidth\":" + advertisedBandwidth + ",\n"
        + "\"exit_policy\":[");
    int written = 0;
    for (String exitPolicyLine : descriptor.getExitPolicyLines()) {
      sb.append((written++ > 0 ? "," : "") + "\n  \"" + exitPolicyLine
          + "\"");
    }
    sb.append("\n]");
    if (descriptor.getContact() != null) {
      sb.append(",\n\"contact\":\""
          + escapeJSON(descriptor.getContact()) + "\"");
    }
    if (descriptor.getPlatform() != null) {
      sb.append(",\n\"platform\":\""
          + escapeJSON(descriptor.getPlatform()) + "\"");
    }
    if (descriptor.getFamilyEntries() != null) {
      sb.append(",\n\"family\":[");
      written = 0;
      for (String familyEntry : descriptor.getFamilyEntries()) {
        sb.append((written++ > 0 ? "," : "") + "\n  \"" + familyEntry
            + "\"");
      }
      sb.append("\n]");
    }
    if (descriptor.getIpv6DefaultPolicy() != null &&
        (descriptor.getIpv6DefaultPolicy().equals("accept") ||
        descriptor.getIpv6DefaultPolicy().equals("reject")) &&
        descriptor.getIpv6PortList() != null) {
      sb.append(",\n\"exit_policy_v6_summary\":{\""
          + descriptor.getIpv6DefaultPolicy() + "\":[");
      int portsWritten = 0;
      for (String portOrPortRange :
          descriptor.getIpv6PortList().split(",")) {
        sb.append((portsWritten++ > 0 ? "," : "") + "\"" + portOrPortRange
            + "\"");
      }
      sb.append("]}");
    }
    if (descriptor.isHibernating()) {
      sb.append(",\n\"hibernating\":true");
    }
    detailsStatus = new DetailsStatus();
    detailsStatus.documentString = sb.toString();
    this.documentStore.store(detailsStatus, fingerprint);
  }

  private void processBridgeServerDescriptor(
      ServerDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    DetailsStatus detailsStatus = this.documentStore.retrieve(
        DetailsStatus.class, false, fingerprint);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String publishedDateTime =
        dateTimeFormat.format(descriptor.getPublishedMillis());
    String poolAssignmentLine = null;
    if (detailsStatus != null) {
      String detailsString = detailsStatus.documentString;
      String descPublishedLine = "\"desc_published\":\""
          + publishedDateTime + "\",";
      Scanner s = new Scanner(detailsString);
      while (s.hasNextLine()) {
        String line = s.nextLine();
        if (line.startsWith("\"pool_assignment\":")) {
          poolAssignmentLine = line;
        } else if (line.startsWith("\"desc_published\":") &&
            descPublishedLine.compareTo(line) < 0) {
          return;
        }
      }
      s.close();
    }
    StringBuilder sb = new StringBuilder();
    String lastRestartedString = dateTimeFormat.format(
        descriptor.getPublishedMillis() - descriptor.getUptime() * 1000L);
    int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
        Math.min(descriptor.getBandwidthBurst(),
        descriptor.getBandwidthObserved()));
    sb.append("\"desc_published\":\"" + publishedDateTime + "\",\n"
        + "\"last_restarted\":\"" + lastRestartedString + "\",\n"
        + "\"advertised_bandwidth\":" + advertisedBandwidth + ",\n"
        + "\"platform\":\"" + escapeJSON(descriptor.getPlatform())
        + "\"");
    if (poolAssignmentLine != null) {
      sb.append(",\n" + poolAssignmentLine);
    }
    detailsStatus = new DetailsStatus();
    detailsStatus.documentString = sb.toString();
    this.documentStore.store(detailsStatus, fingerprint);
  }

  private static String escapeJSON(String s) {
    return StringEscapeUtils.escapeJavaScript(s).replaceAll("\\\\'", "'");
  }

  private void processBridgePoolAssignment(
      BridgePoolAssignment bridgePoolAssignment) {
    for (Map.Entry<String, String> e :
        bridgePoolAssignment.getEntries().entrySet()) {
      String fingerprint = e.getKey();
      String details = e.getValue();
      StringBuilder sb = new StringBuilder();
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, false, fingerprint);
      if (detailsStatus != null) {
        String detailsString = detailsStatus.documentString;
        Scanner s = new Scanner(detailsString);
        int linesWritten = 0;
        boolean endsWithComma = false;
        while (s.hasNextLine()) {
          String line = s.nextLine();
          if (!line.startsWith("\"pool_assignment\":")) {
            sb.append((linesWritten++ > 0 ? "\n" : "") + line);
            endsWithComma = line.endsWith(",");
          }
        }
        s.close();
        if (sb.length() > 0) {
          sb.append((endsWithComma ? "" : ",") + "\n");
        }
      }
      sb.append("\"pool_assignment\":\"" + details + "\"");
      detailsStatus = new DetailsStatus();
      detailsStatus.documentString = sb.toString();
      this.documentStore.store(detailsStatus, fingerprint);
    }
  }

  private void setCurrentNodes() {
    SortedMap<String, NodeStatus> currentNodes = this.getCurrentNodes();
    this.relays = new TreeMap<String, NodeStatus>();
    this.bridges = new TreeMap<String, NodeStatus>();
    for (Map.Entry<String, NodeStatus> e : currentNodes.entrySet()) {
      if (e.getValue().isRelay()) {
        this.relays.put(e.getKey(), e.getValue());
      } else {
        this.bridges.put(e.getKey(), e.getValue());
      }
    }
  }

  private void startReverseDomainNameLookups() {
    Map<String, Long> addressLastLookupTimes =
        new HashMap<String, Long>();
    for (NodeStatus relay : relays.values()) {
      addressLastLookupTimes.put(relay.getAddress(),
          relay.getLastRdnsLookup());
    }
    this.reverseDomainNameResolver.setAddresses(addressLastLookupTimes);
    this.reverseDomainNameResolver.startReverseDomainNameLookups();
  }

  private void finishReverseDomainNameLookups() {
    this.reverseDomainNameResolver.finishReverseDomainNameLookups();
    Map<String, String> lookupResults =
        this.reverseDomainNameResolver.getLookupResults();
    long startedRdnsLookups =
        this.reverseDomainNameResolver.getLookupStartMillis();
    for (NodeStatus relay : relays.values()) {
      if (lookupResults.containsKey(relay.getAddress())) {
        relay.setHostName(lookupResults.get(relay.getAddress()));
        relay.setLastRdnsLookup(startedRdnsLookups);
      }
    }
  }

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
      System.err.println("Could not determine most recent Wxx parameter "
          + "values, probably because we didn't parse a consensus in "
          + "this execution.  All relays' guard/middle/exit weights are "
          + "going to be 0.0.");
    }
    SortedMap<String, Double>
        advertisedBandwidths = new TreeMap<String, Double>(),
        consensusWeights = new TreeMap<String, Double>(),
        guardWeights = new TreeMap<String, Double>(),
        middleWeights = new TreeMap<String, Double>(),
        exitWeights = new TreeMap<String, Double>();
    double totalAdvertisedBandwidth = 0.0;
    double totalConsensusWeight = 0.0;
    double totalGuardWeight = 0.0;
    double totalMiddleWeight = 0.0;
    double totalExitWeight = 0.0;
    for (Map.Entry<String, NodeStatus> e : this.relays.entrySet()) {
      String fingerprint = e.getKey();
      NodeStatus relay = e.getValue();
      if (!relay.getRunning()) {
        continue;
      }
      boolean isExit = relay.getRelayFlags().contains("Exit") &&
          !relay.getRelayFlags().contains("BadExit");
      boolean isGuard = relay.getRelayFlags().contains("Guard");
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, false, fingerprint);
      if (detailsStatus != null) {
        double advertisedBandwidth = -1.0;
        String detailsString = detailsStatus.documentString;
        Scanner s = new Scanner(detailsString);
        while (s.hasNextLine()) {
          String line = s.nextLine();
          if (!line.startsWith("\"advertised_bandwidth\":")) {
            continue;
          }
          try {
            advertisedBandwidth = (double) Integer.parseInt(
                line.split(":")[1].replaceAll(",", ""));
          } catch (NumberFormatException ex) {
            /* Handle below. */
          }
          break;
        }
        s.close();
        if (advertisedBandwidth >= 0.0) {
          advertisedBandwidths.put(fingerprint, advertisedBandwidth);
          totalAdvertisedBandwidth += advertisedBandwidth;
        }
      }
      double consensusWeight = (double) relay.getConsensusWeight();
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
    for (Map.Entry<String, NodeStatus> e : this.relays.entrySet()) {
      String fingerprint = e.getKey();
      NodeStatus relay = e.getValue();
      if (advertisedBandwidths.containsKey(fingerprint)) {
        relay.setAdvertisedBandwidthFraction(advertisedBandwidths.get(
            fingerprint) / totalAdvertisedBandwidth);
      }
      if (consensusWeights.containsKey(fingerprint)) {
        relay.setConsensusWeightFraction(consensusWeights.get(fingerprint)
            / totalConsensusWeight);
      }
      if (guardWeights.containsKey(fingerprint)) {
        relay.setGuardProbability(guardWeights.get(fingerprint)
            / totalGuardWeight);
      }
      if (middleWeights.containsKey(fingerprint)) {
        relay.setMiddleProbability(middleWeights.get(fingerprint)
            / totalMiddleWeight);
      }
      if (exitWeights.containsKey(fingerprint)) {
        relay.setExitProbability(exitWeights.get(fingerprint)
            / totalExitWeight);
      }
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + Logger.formatDecimalNumber(
        relayConsensusesProcessed) + " relay consensuses processed\n");
    sb.append("    " + Logger.formatDecimalNumber(bridgeStatusesProcessed)
        + " bridge statuses processed\n");
    return sb.toString();
  }
}

