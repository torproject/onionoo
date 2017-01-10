/* Copyright 2016--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import org.torproject.onionoo.docs.DetailsDocument;
import org.torproject.onionoo.docs.DetailsStatus;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.UpdateStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class DetailsDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      DetailsDocumentWriter.class);

  private DocumentStore documentStore;

  public DetailsDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  @Override
  public void writeDocuments() {
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, true);
    long updatedMillis = updateStatus != null
        ? updateStatus.getUpdatedMillis() : 0L;
    SortedSet<String> updatedDetailsStatuses = this.documentStore.list(
        DetailsStatus.class, updatedMillis);
    for (String fingerprint : updatedDetailsStatuses) {
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, true, fingerprint);
      if (detailsStatus.isRelay()) {
        this.updateRelayDetailsFile(fingerprint, detailsStatus);
      } else {
        this.updateBridgeDetailsFile(fingerprint, detailsStatus);
      }
    }
    log.info("Wrote details document files");
  }

  private void updateRelayDetailsFile(String fingerprint,
      DetailsStatus detailsStatus) {
    DetailsDocument detailsDocument = new DetailsDocument();
    detailsDocument.setNickname(detailsStatus.getNickname());
    detailsDocument.setFingerprint(fingerprint);
    List<String> orAddresses = new ArrayList<>();
    orAddresses.add(detailsStatus.getAddress() + ":"
        + detailsStatus.getOrPort());
    for (String orAddress : detailsStatus.getOrAddressesAndPorts()) {
      orAddresses.add(orAddress.toLowerCase());
    }
    detailsDocument.setOrAddresses(orAddresses);
    if (detailsStatus.getDirPort() != 0) {
      detailsDocument.setDirAddress(detailsStatus.getAddress() + ":"
          + detailsStatus.getDirPort());
    }
    detailsDocument.setLastSeen(detailsStatus.getLastSeenMillis());
    detailsDocument.setFirstSeen(detailsStatus.getFirstSeenMillis());
    detailsDocument.setLastChangedAddressOrPort(
        detailsStatus.getLastChangedOrAddressOrPort());
    detailsDocument.setRunning(detailsStatus.isRunning());
    detailsDocument.setFlags(detailsStatus.getRelayFlags());
    detailsDocument.setConsensusWeight(
        detailsStatus.getConsensusWeight());
    detailsDocument.setHostName(detailsStatus.getHostName());
    String defaultPolicy = detailsStatus.getDefaultPolicy();
    String portList = detailsStatus.getPortList();
    if (defaultPolicy != null && (defaultPolicy.equals("accept")
        || defaultPolicy.equals("reject")) && portList != null) {
      Map<String, List<String>> exitPolicySummary = new HashMap<>();
      List<String> portsOrPortRanges = Arrays.asList(portList.split(","));
      exitPolicySummary.put(defaultPolicy, portsOrPortRanges);
      detailsDocument.setExitPolicySummary(exitPolicySummary);
    }
    detailsDocument.setRecommendedVersion(
        detailsStatus.getRecommendedVersion());
    detailsDocument.setCountry(detailsStatus.getCountryCode());
    detailsDocument.setLatitude(detailsStatus.getLatitude());
    detailsDocument.setLongitude(detailsStatus.getLongitude());
    detailsDocument.setCountryName(detailsStatus.getCountryName());
    detailsDocument.setRegionName(detailsStatus.getRegionName());
    detailsDocument.setCityName(detailsStatus.getCityName());
    detailsDocument.setAsNumber(detailsStatus.getAsNumber());
    detailsDocument.setAsName(detailsStatus.getAsName());
    if (detailsStatus.isRunning()) {
      detailsDocument.setConsensusWeightFraction(
          detailsStatus.getConsensusWeightFraction());
      detailsDocument.setGuardProbability(
          detailsStatus.getGuardProbability());
      detailsDocument.setMiddleProbability(
          detailsStatus.getMiddleProbability());
      detailsDocument.setExitProbability(
          detailsStatus.getExitProbability());
    }
    detailsDocument.setLastRestarted(detailsStatus.getLastRestarted());
    detailsDocument.setBandwidthRate(detailsStatus.getBandwidthRate());
    detailsDocument.setBandwidthBurst(detailsStatus.getBandwidthBurst());
    detailsDocument.setObservedBandwidth(
        detailsStatus.getObservedBandwidth());
    detailsDocument.setAdvertisedBandwidth(
        detailsStatus.getAdvertisedBandwidth());
    detailsDocument.setExitPolicy(detailsStatus.getExitPolicy());
    detailsDocument.setContact(detailsStatus.getContact());
    detailsDocument.setPlatform(detailsStatus.getPlatform());
    if (detailsStatus.getAllegedFamily() != null
        && !detailsStatus.getAllegedFamily().isEmpty()) {
      SortedSet<String> allegedFamily = new TreeSet<>();
      for (String familyMember : detailsStatus.getAllegedFamily()) {
        if (familyMember.length() >= 40) {
          allegedFamily.add("$" + familyMember);
        } else {
          allegedFamily.add(familyMember);
        }
      }
      detailsDocument.setAllegedFamily(allegedFamily);
    }
    if (detailsStatus.getEffectiveFamily() != null
        && !detailsStatus.getEffectiveFamily().isEmpty()) {
      SortedSet<String> effectiveFamily = new TreeSet<>();
      for (String familyMember : detailsStatus.getEffectiveFamily()) {
        effectiveFamily.add("$" + familyMember);
      }
      detailsDocument.setEffectiveFamily(effectiveFamily);
    }
    if (detailsStatus.getIndirectFamily() != null
        && !detailsStatus.getIndirectFamily().isEmpty()) {
      SortedSet<String> indirectFamily = new TreeSet<>();
      for (String familyMember : detailsStatus.getIndirectFamily()) {
        indirectFamily.add("$" + familyMember);
      }
      detailsDocument.setIndirectFamily(indirectFamily);
    }
    detailsDocument.setExitPolicyV6Summary(
        detailsStatus.getExitPolicyV6Summary());
    detailsDocument.setHibernating(detailsStatus.getHibernating());
    if (detailsStatus.getExitAddresses() != null) {
      SortedSet<String> exitAddressesWithoutOrAddresses =
          new TreeSet<>(detailsStatus.getExitAddresses().keySet());
      exitAddressesWithoutOrAddresses.removeAll(
          detailsStatus.getOrAddresses());
      detailsDocument.setExitAddresses(new ArrayList<>(
          exitAddressesWithoutOrAddresses));
    }
    detailsDocument.setMeasured(detailsStatus.getMeasured());
    this.documentStore.store(detailsDocument, fingerprint);
  }

  private void updateBridgeDetailsFile(String fingerprint,
      DetailsStatus detailsStatus) {
    DetailsDocument detailsDocument = new DetailsDocument();
    detailsDocument.setNickname(detailsStatus.getNickname());
    detailsDocument.setHashedFingerprint(fingerprint);
    String address = detailsStatus.getAddress();
    List<String> orAddresses = new ArrayList<>();
    orAddresses.add(address + ":" + detailsStatus.getOrPort());
    SortedSet<String> orAddressesAndPorts =
        detailsStatus.getOrAddressesAndPorts();
    if (orAddressesAndPorts != null) {
      for (String orAddress : orAddressesAndPorts) {
        orAddresses.add(orAddress.toLowerCase());
      }
    }
    detailsDocument.setOrAddresses(orAddresses);
    detailsDocument.setLastSeen(detailsStatus.getLastSeenMillis());
    detailsDocument.setFirstSeen(detailsStatus.getFirstSeenMillis());
    detailsDocument.setRunning(detailsStatus.isRunning());
    detailsDocument.setFlags(detailsStatus.getRelayFlags());
    detailsDocument.setLastRestarted(detailsStatus.getLastRestarted());
    detailsDocument.setAdvertisedBandwidth(
        detailsStatus.getAdvertisedBandwidth());
    detailsDocument.setPlatform(detailsStatus.getPlatform());
    detailsDocument.setTransports(detailsStatus.getTransports());
    this.documentStore.store(detailsDocument, fingerprint);
  }

  @Override
  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}

