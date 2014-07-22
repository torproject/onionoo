/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

public class SummaryDocumentWriter implements DocumentWriter {

  private DocumentStore documentStore;

  public SummaryDocumentWriter() {
    this.documentStore = ApplicationFactory.getDocumentStore();
  }

  private int writtenDocuments = 0, deletedDocuments = 0;

  public void writeDocuments() {
    long maxLastSeenMillis = 0L;
    for (String fingerprint : this.documentStore.list(NodeStatus.class)) {
      NodeStatus nodeStatus = this.documentStore.retrieve(
          NodeStatus.class, true, fingerprint);
      if (nodeStatus != null &&
          nodeStatus.getLastSeenMillis() > maxLastSeenMillis) {
        maxLastSeenMillis = nodeStatus.getLastSeenMillis();
      }
    }
    long cutoff = maxLastSeenMillis - DateTimeHelper.ONE_WEEK;
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
      boolean isRelay = nodeStatus.isRelay();
      String nickname = nodeStatus.getNickname();
      List<String> addresses = new ArrayList<String>();
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
      boolean running = nodeStatus.getRunning();
      SortedSet<String> relayFlags = nodeStatus.getRelayFlags();
      long consensusWeight = nodeStatus.getConsensusWeight();
      String countryCode = nodeStatus.getCountryCode();
      long firstSeenMillis = nodeStatus.getFirstSeenMillis();
      String aSNumber = nodeStatus.getASNumber();
      String contact = nodeStatus.getContact();
      SortedSet<String> familyFingerprints =
          nodeStatus.getFamilyFingerprints();
      SummaryDocument summaryDocument = new SummaryDocument(isRelay,
          nickname, fingerprint, addresses, lastSeenMillis, running,
          relayFlags, consensusWeight, countryCode, firstSeenMillis,
          aSNumber, contact, familyFingerprints);
      if (this.documentStore.store(summaryDocument, fingerprint)) {
        this.writtenDocuments++;
      };
    }
    Logger.printStatusTime("Wrote summary document files");
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + Logger.formatDecimalNumber(this.writtenDocuments)
        + " summary document files written\n");
    sb.append("    " + Logger.formatDecimalNumber(this.deletedDocuments)
        + " summary document files deleted\n");
    return sb.toString();
  }
}
