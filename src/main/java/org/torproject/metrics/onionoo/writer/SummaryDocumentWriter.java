/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.writer;

import org.torproject.metrics.onionoo.docs.DateTimeHelper;
import org.torproject.metrics.onionoo.docs.DocumentStore;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.docs.NodeStatus;
import org.torproject.metrics.onionoo.docs.SummaryDocument;
import org.torproject.metrics.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

public class SummaryDocumentWriter implements DocumentWriter {

  private static final Logger log = LoggerFactory.getLogger(
      SummaryDocumentWriter.class);

  private DocumentStore documentStore;

  public SummaryDocumentWriter() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
  }

  private int writtenDocuments = 0;

  private int deletedDocuments = 0;

  @Override
  public void writeDocuments(long mostRecentStatusMillis) {
    long relaysLastValidAfterMillis = -1L;
    long bridgesLastPublishedMillis = -1L;
    for (String fingerprint : this.documentStore.list(NodeStatus.class)) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      if (nodeStatus != null) {
        if (nodeStatus.isRelay()) {
          relaysLastValidAfterMillis = Math.max(
              relaysLastValidAfterMillis, nodeStatus.getLastSeenMillis());
        } else {
          bridgesLastPublishedMillis = Math.max(
              bridgesLastPublishedMillis, nodeStatus.getLastSeenMillis());
        }
      }
    }
    long cutoff = Math.max(relaysLastValidAfterMillis,
        bridgesLastPublishedMillis) - DateTimeHelper.ONE_WEEK;
    for (String fingerprint : this.documentStore.list(NodeStatus.class)) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class,
          true, fingerprint);
      if (nodeStatus == null) {
        continue;
      }
      if (nodeStatus.getLastSeenMillis() < cutoff) {
        if (this.documentStore.remove(SummaryDocument.class,
            fingerprint)) {
          this.deletedDocuments++;
        }
        continue;
      }
      List<String> addresses = new ArrayList<>();
      addresses.add(nodeStatus.getAddress());
      for (String orAddress : nodeStatus.getOrAddresses()) {
        if (!addresses.contains(orAddress)) {
          addresses.add(orAddress);
        }
      }
      for (String exitAddress : nodeStatus.getExitAddresses()) {
        if (!addresses.contains(exitAddress)) {
          addresses.add(exitAddress);
        }
      }
      long lastSeenMillis = nodeStatus.getLastSeenMillis();
      SortedSet<String> relayFlags = nodeStatus.getRelayFlags();
      boolean isRelay = nodeStatus.isRelay();
      boolean running = relayFlags.contains("Running") && (isRelay
          ? lastSeenMillis == relaysLastValidAfterMillis
          : lastSeenMillis == bridgesLastPublishedMillis);
      long consensusWeight = nodeStatus.getConsensusWeight();
      String countryCode = nodeStatus.getCountryCode();
      long firstSeenMillis = nodeStatus.getFirstSeenMillis();
      String asNumber = nodeStatus.getAsNumber();
      String asName = nodeStatus.getAsName();
      String contact = nodeStatus.getContact();
      SortedSet<String> declaredFamily = nodeStatus.getDeclaredFamily();
      SortedSet<String> effectiveFamily = nodeStatus.getEffectiveFamily();
      String nickname = nodeStatus.getNickname();
      String version = nodeStatus.getVersion();
      String operatingSystem = nodeStatus.getOperatingSystem();
      SortedSet<String> verifiedHostNames = nodeStatus.getVerifiedHostNames();
      SortedSet<String> unverifiedHostNames =
          nodeStatus.getUnverifiedHostNames();
      Boolean recommendedVersion = nodeStatus.isRecommendedVersion();
      SummaryDocument summaryDocument = new SummaryDocument(isRelay,
          nickname, fingerprint, addresses, lastSeenMillis, running,
          relayFlags, consensusWeight, countryCode, firstSeenMillis,
          asNumber, asName, contact, declaredFamily, effectiveFamily, version,
          operatingSystem, verifiedHostNames,
          unverifiedHostNames, recommendedVersion);
      if (this.documentStore.store(summaryDocument, fingerprint)) {
        this.writtenDocuments++;
      }
    }
    log.info("Wrote summary document files");
  }

  @Override
  public String getStatsString() {
    return String.format("    %s summary document files written\n"
        + "    %s summary document files deleted\n",
        FormattingUtils.formatDecimalNumber(this.writtenDocuments),
        FormattingUtils.formatDecimalNumber(this.deletedDocuments));
  }
}

