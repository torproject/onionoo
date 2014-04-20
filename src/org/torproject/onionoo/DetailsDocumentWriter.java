package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;

public class DetailsDocumentWriter implements DescriptorListener,
    FingerprintListener, DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public DetailsDocumentWriter() {
    this.descriptorSource = ApplicationFactory.getDescriptorSource();
    this.documentStore = ApplicationFactory.getDocumentStore();
    this.now = ApplicationFactory.getTime().currentTimeMillis();
    this.registerDescriptorListeners();
    this.registerFingerprintListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.EXIT_LISTS);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof ExitList) {
      this.processExitList((ExitList) descriptor);
    }
  }

  private Map<String, Set<ExitListEntry>> exitListEntries =
      new HashMap<String, Set<ExitListEntry>>();

  /* TODO Processing descriptors should really be done in
   * NodeDetailsStatusUpdater, not here.  This is also a bug, because
   * we're only considering newly published exit lists. */
  private void processExitList(ExitList exitList) {
    for (ExitListEntry exitListEntry : exitList.getExitListEntries()) {
      if (exitListEntry.getScanMillis() <
          this.now - 24L * 60L * 60L * 1000L) {
        continue;
      }
      String fingerprint = exitListEntry.getFingerprint();
      if (!this.exitListEntries.containsKey(fingerprint)) {
        this.exitListEntries.put(fingerprint,
            new HashSet<ExitListEntry>());
      }
      this.exitListEntries.get(fingerprint).add(exitListEntry);
    }
  }

  private void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_SERVER_DESCRIPTORS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_STATUSES);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_SERVER_DESCRIPTORS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_POOL_ASSIGNMENTS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.EXIT_LISTS);
  }

  private SortedSet<String> newRelays = new TreeSet<String>(),
      newBridges = new TreeSet<String>();

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    if (relay) {
      this.newRelays.addAll(fingerprints);
    } else {
      this.newBridges.addAll(fingerprints);
    }
  }

  public void writeDocuments() {
    this.updateRelayDetailsFiles();
    this.updateBridgeDetailsFiles();
    Logger.printStatusTime("Wrote details document files");
  }

  private void updateRelayDetailsFiles() {
    for (String fingerprint : this.newRelays) {

      /* Generate network-status-specific part. */
      NodeStatus entry = this.documentStore.retrieve(NodeStatus.class,
          true, fingerprint);
      if (entry == null) {
        continue;
      }
      DetailsDocument detailsDocument = new DetailsDocument();
      detailsDocument.setNickname(entry.getNickname());
      detailsDocument.setFingerprint(fingerprint);
      List<String> orAddresses = new ArrayList<String>();
      orAddresses.add(entry.getAddress() + ":" + entry.getOrPort());
      for (String orAddress : entry.getOrAddressesAndPorts()) {
        orAddresses.add(orAddress.toLowerCase());
      }
      detailsDocument.setOrAddresses(orAddresses);
      if (entry.getDirPort() != 0) {
        detailsDocument.setDirAddress(entry.getAddress() + ":"
            + entry.getDirPort());
      }
      detailsDocument.setLastSeen(DateTimeHelper.format(
          entry.getLastSeenMillis()));
      detailsDocument.setFirstSeen(DateTimeHelper.format(
          entry.getFirstSeenMillis()));
      detailsDocument.setLastChangedAddressOrPort(
          DateTimeHelper.format(entry.getLastChangedOrAddress()));
      detailsDocument.setRunning(entry.getRunning());
      if (!entry.getRelayFlags().isEmpty()) {
        detailsDocument.setFlags(new ArrayList<String>(
            entry.getRelayFlags()));
      }
      detailsDocument.setCountry(entry.getCountryCode());
      detailsDocument.setLatitude(entry.getLatitude());
      detailsDocument.setLongitude(entry.getLongitude());
      detailsDocument.setCountryName(entry.getCountryName());
      detailsDocument.setRegionName(entry.getRegionName());
      detailsDocument.setCityName(entry.getCityName());
      detailsDocument.setAsNumber(entry.getASNumber());
      detailsDocument.setAsName(entry.getASName());
      detailsDocument.setConsensusWeight(entry.getConsensusWeight());
      detailsDocument.setHostName(entry.getHostName());
      detailsDocument.setAdvertisedBandwidthFraction(
          (float) entry.getAdvertisedBandwidthFraction());
      detailsDocument.setConsensusWeightFraction(
          (float) entry.getConsensusWeightFraction());
      detailsDocument.setGuardProbability(
          (float) entry.getGuardProbability());
      detailsDocument.setMiddleProbability(
          (float) entry.getMiddleProbability());
      detailsDocument.setExitProbability(
          (float) entry.getExitProbability());
      String defaultPolicy = entry.getDefaultPolicy();
      String portList = entry.getPortList();
      if (defaultPolicy != null && (defaultPolicy.equals("accept") ||
          defaultPolicy.equals("reject")) && portList != null) {
        Map<String, List<String>> exitPolicySummary =
            new HashMap<String, List<String>>();
        List<String> portsOrPortRanges = Arrays.asList(
            portList.split(","));
        exitPolicySummary.put(defaultPolicy, portsOrPortRanges);
        detailsDocument.setExitPolicySummary(exitPolicySummary);
      }
      detailsDocument.setRecommendedVersion(
          entry.getRecommendedVersion());

      /* Add exit addresses if at least one of them is distinct from the
       * onion-routing addresses. */
      SortedSet<String> exitAddresses = new TreeSet<String>();
      if (this.exitListEntries.containsKey(fingerprint)) {
        for (ExitListEntry exitListEntry :
            this.exitListEntries.get(fingerprint)) {
          String exitAddress = exitListEntry.getExitAddress();
          if (exitAddress.length() > 0 &&
              !entry.getAddress().equals(exitAddress) &&
              !entry.getOrAddresses().contains(exitAddress)) {
            exitAddresses.add(exitAddress.toLowerCase());
          }
        }
      }
      if (!exitAddresses.isEmpty()) {
        detailsDocument.setExitAddresses(new ArrayList<String>(
            exitAddresses));
      }

      /* Append descriptor-specific part from details status file. */
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, true, fingerprint);
      if (detailsStatus != null) {
        detailsDocument.setLastRestarted(
            detailsStatus.getLastRestarted());
        detailsDocument.setBandwidthRate(
            detailsStatus.getBandwidthRate());
        detailsDocument.setBandwidthBurst(
            detailsStatus.getBandwidthBurst());
        detailsDocument.setObservedBandwidth(
            detailsStatus.getObservedBandwidth());
        detailsDocument.setAdvertisedBandwidth(
            detailsStatus.getAdvertisedBandwidth());
        detailsDocument.setExitPolicy(detailsStatus.getExitPolicy());
        detailsDocument.setContact(detailsStatus.getContact());
        detailsDocument.setPlatform(detailsStatus.getPlatform());
        detailsDocument.setFamily(detailsStatus.getFamily());
        detailsDocument.setExitPolicyV6Summary(
            detailsStatus.getExitPolicyV6Summary());
        detailsDocument.setHibernating(detailsStatus.getHibernating());
      }

      /* Write details file to disk. */
      this.documentStore.store(detailsDocument, fingerprint);
    }
  }

  private void updateBridgeDetailsFiles() {
    for (String fingerprint : this.newBridges) {

      /* Generate network-status-specific part. */
      NodeStatus entry = this.documentStore.retrieve(NodeStatus.class,
          true, fingerprint);
      if (entry == null) {
        continue;
      }
      DetailsDocument detailsDocument = new DetailsDocument();
      detailsDocument.setNickname(entry.getNickname());
      detailsDocument.setHashedFingerprint(fingerprint);
      String address = entry.getAddress();
      List<String> orAddresses = new ArrayList<String>();
      orAddresses.add(address + ":" + entry.getOrPort());
      for (String orAddress : entry.getOrAddressesAndPorts()) {
        orAddresses.add(orAddress.toLowerCase());
      }
      detailsDocument.setOrAddresses(orAddresses);
      detailsDocument.setLastSeen(DateTimeHelper.format(
          entry.getLastSeenMillis()));
      detailsDocument.setFirstSeen(DateTimeHelper.format(
          entry.getFirstSeenMillis()));
      detailsDocument.setRunning(entry.getRunning());
      detailsDocument.setFlags(new ArrayList<String>(
          entry.getRelayFlags()));

      /* Append descriptor-specific part from details status file. */
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, true, fingerprint);
      if (detailsStatus != null) {
        detailsDocument.setLastRestarted(
            detailsStatus.getLastRestarted());
        detailsDocument.setAdvertisedBandwidth(
            detailsStatus.getAdvertisedBandwidth());
        detailsDocument.setPlatform(detailsStatus.getPlatform());
        detailsDocument.setPoolAssignment(
            detailsStatus.getPoolAssignment());
      }

      /* Write details file to disk. */
      this.documentStore.store(detailsDocument, fingerprint);
    }
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}
