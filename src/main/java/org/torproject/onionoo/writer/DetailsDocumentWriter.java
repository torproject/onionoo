package org.torproject.onionoo.writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.DetailsDocument;
import org.torproject.onionoo.docs.DetailsStatus;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.NodeStatus;
import org.torproject.onionoo.docs.UpdateStatus;
import org.torproject.onionoo.util.TimeFactory;

public class DetailsDocumentWriter implements DocumentWriter {

  private final static Logger log = LoggerFactory.getLogger(
      DetailsDocumentWriter.class);

  private DocumentStore documentStore;

  private long now;

  public DetailsDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.now = TimeFactory.getTime().currentTimeMillis();
  }

  private SortedSet<String> newRelays = new TreeSet<String>(),
      newBridges = new TreeSet<String>();

  public void writeDocuments() {
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null ?
        updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updatedNodeStatuses = this.documentStore.list(
        NodeStatus.class, updatedMillis);
    for (String fingerprint : updatedNodeStatuses) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      if (nodeStatus.isRelay()) {
        newRelays.add(fingerprint);
      } else {
        newBridges.add(fingerprint);
      }
    }
    SortedSet<String> updatedDetailsStatuses = this.documentStore.list(
        DetailsStatus.class, updatedMillis);
    for (String fingerprint : updatedDetailsStatuses) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      if (nodeStatus == null) {
        continue;
      } else if (nodeStatus.isRelay()) {
        newRelays.add(fingerprint);
      } else {
        newBridges.add(fingerprint);
      }
    }
    this.updateRelayDetailsFiles();
    this.updateBridgeDetailsFiles();
    log.info("Wrote details document files");
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
      detailsDocument.setLastSeen(entry.getLastSeenMillis());
      detailsDocument.setFirstSeen(entry.getFirstSeenMillis());
      detailsDocument.setLastChangedAddressOrPort(
          entry.getLastChangedOrAddress());
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

      /* Append descriptor-specific part and exit addresses from details
       * status file. */
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
        if (detailsStatus.getExitAddresses() != null) {
          SortedSet<String> exitAddresses = new TreeSet<String>();
          for (Map.Entry<String, Long> e :
              detailsStatus.getExitAddresses().entrySet()) {
            String exitAddress = e.getKey().toLowerCase();
            long scanMillis = e.getValue();
            if (!entry.getAddress().equals(exitAddress) &&
                !entry.getOrAddresses().contains(exitAddress) &&
                scanMillis >= this.now - DateTimeHelper.ONE_DAY) {
              exitAddresses.add(exitAddress);
            }
          }
          if (!exitAddresses.isEmpty()) {
            detailsDocument.setExitAddresses(new ArrayList<String>(
                exitAddresses));
          }
        }
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
      detailsDocument.setLastSeen(entry.getLastSeenMillis());
      detailsDocument.setFirstSeen(entry.getFirstSeenMillis());
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
