package org.torproject.onionoo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

class NodeIndex {

  private String relaysPublishedString;
  public void setRelaysPublishedString(String relaysPublishedString) {
    this.relaysPublishedString = relaysPublishedString;
  }
  public String getRelaysPublishedString() {
    return relaysPublishedString;
  }

  private String bridgesPublishedString;
  public void setBridgesPublishedString(String bridgesPublishedString) {
    this.bridgesPublishedString = bridgesPublishedString;
  }
  public String getBridgesPublishedString() {
    return bridgesPublishedString;
  }

  private List<String> relaysByConsensusWeight;
  public void setRelaysByConsensusWeight(
      List<String> relaysByConsensusWeight) {
    this.relaysByConsensusWeight = relaysByConsensusWeight;
  }
  public List<String> getRelaysByConsensusWeight() {
    return relaysByConsensusWeight;
  }


  private Map<String, SummaryDocument> relayFingerprintSummaryLines;
  public void setRelayFingerprintSummaryLines(
      Map<String, SummaryDocument> relayFingerprintSummaryLines) {
    this.relayFingerprintSummaryLines = relayFingerprintSummaryLines;
  }
  public Map<String, SummaryDocument> getRelayFingerprintSummaryLines() {
    return this.relayFingerprintSummaryLines;
  }

  private Map<String, SummaryDocument> bridgeFingerprintSummaryLines;
  public void setBridgeFingerprintSummaryLines(
      Map<String, SummaryDocument> bridgeFingerprintSummaryLines) {
    this.bridgeFingerprintSummaryLines = bridgeFingerprintSummaryLines;
  }
  public Map<String, SummaryDocument> getBridgeFingerprintSummaryLines() {
    return this.bridgeFingerprintSummaryLines;
  }

  private Map<String, Set<String>> relaysByCountryCode = null;
  public void setRelaysByCountryCode(
      Map<String, Set<String>> relaysByCountryCode) {
    this.relaysByCountryCode = relaysByCountryCode;
  }
  public Map<String, Set<String>> getRelaysByCountryCode() {
    return relaysByCountryCode;
  }

  private Map<String, Set<String>> relaysByASNumber = null;
  public void setRelaysByASNumber(
      Map<String, Set<String>> relaysByASNumber) {
    this.relaysByASNumber = relaysByASNumber;
  }
  public Map<String, Set<String>> getRelaysByASNumber() {
    return relaysByASNumber;
  }

  private Map<String, Set<String>> relaysByFlag = null;
  public void setRelaysByFlag(Map<String, Set<String>> relaysByFlag) {
    this.relaysByFlag = relaysByFlag;
  }
  public Map<String, Set<String>> getRelaysByFlag() {
    return relaysByFlag;
  }

  private Map<String, Set<String>> bridgesByFlag = null;
  public void setBridgesByFlag(Map<String, Set<String>> bridgesByFlag) {
    this.bridgesByFlag = bridgesByFlag;
  }
  public Map<String, Set<String>> getBridgesByFlag() {
    return bridgesByFlag;
  }

  private Map<String, Set<String>> relaysByContact = null;
  public void setRelaysByContact(
      Map<String, Set<String>> relaysByContact) {
    this.relaysByContact = relaysByContact;
  }
  public Map<String, Set<String>> getRelaysByContact() {
    return relaysByContact;
  }

  private Map<String, Set<String>> relaysByFamily = null;
  public void setRelaysByFamily(Map<String, Set<String>> relaysByFamily) {
    this.relaysByFamily = relaysByFamily;
  }
  public Map<String, Set<String>> getRelaysByFamily() {
    return this.relaysByFamily;
  }

  private SortedMap<Integer, Set<String>> relaysByFirstSeenDays;
  public void setRelaysByFirstSeenDays(
      SortedMap<Integer, Set<String>> relaysByFirstSeenDays) {
    this.relaysByFirstSeenDays = relaysByFirstSeenDays;
  }
  public SortedMap<Integer, Set<String>> getRelaysByFirstSeenDays() {
    return relaysByFirstSeenDays;
  }

  private SortedMap<Integer, Set<String>> bridgesByFirstSeenDays;
  public void setBridgesByFirstSeenDays(
      SortedMap<Integer, Set<String>> bridgesByFirstSeenDays) {
    this.bridgesByFirstSeenDays = bridgesByFirstSeenDays;
  }
  public SortedMap<Integer, Set<String>> getBridgesByFirstSeenDays() {
    return bridgesByFirstSeenDays;
  }

  private SortedMap<Integer, Set<String>> relaysByLastSeenDays;
  public void setRelaysByLastSeenDays(
      SortedMap<Integer, Set<String>> relaysByLastSeenDays) {
    this.relaysByLastSeenDays = relaysByLastSeenDays;
  }
  public SortedMap<Integer, Set<String>> getRelaysByLastSeenDays() {
    return relaysByLastSeenDays;
  }

  private SortedMap<Integer, Set<String>> bridgesByLastSeenDays;
  public void setBridgesByLastSeenDays(
      SortedMap<Integer, Set<String>> bridgesByLastSeenDays) {
    this.bridgesByLastSeenDays = bridgesByLastSeenDays;
  }
  public SortedMap<Integer, Set<String>> getBridgesByLastSeenDays() {
    return bridgesByLastSeenDays;
  }
}

public class NodeIndexer implements ServletContextListener, Runnable {

  public void contextInitialized(ServletContextEvent contextEvent) {
    ServletContext servletContext = contextEvent.getServletContext();
    File outDir = new File(servletContext.getInitParameter("outDir"));
    DocumentStore documentStore = ApplicationFactory.getDocumentStore();
    documentStore.setOutDir(outDir);
    /* The servlet container created us, and we need to avoid that
     * ApplicationFactory creates another instance of us. */
    ApplicationFactory.setNodeIndexer(this);
    this.startIndexing();
  }

  public void contextDestroyed(ServletContextEvent contextEvent) {
    this.stopIndexing();
  }

  private long lastIndexed = -1L;

  private NodeIndex latestNodeIndex = null;

  private Thread nodeIndexerThread = null;

  public synchronized long getLastIndexed(long timeoutMillis) {
    if (this.lastIndexed == -1L && this.nodeIndexerThread != null &&
        timeoutMillis > 0L) {
      try {
        this.wait(timeoutMillis);
      } catch (InterruptedException e) {
      }
    }
    return this.lastIndexed;
  }

  public synchronized NodeIndex getLatestNodeIndex(long timeoutMillis) {
    if (this.latestNodeIndex == null && this.nodeIndexerThread != null &&
        timeoutMillis > 0L) {
      try {
        this.wait(timeoutMillis);
      } catch (InterruptedException e) {
      }
    }
    return this.latestNodeIndex;
  }

  public synchronized void startIndexing() {
    if (this.nodeIndexerThread == null) {
      this.nodeIndexerThread = new Thread(this);
      this.nodeIndexerThread.setDaemon(true);
      this.nodeIndexerThread.start();
    }
  }

  public void run() {
    while (this.nodeIndexerThread != null) {
      this.indexNodeStatuses();
      try {
        Thread.sleep(DateTimeHelper.ONE_MINUTE);
      } catch (InterruptedException e) {
      }
    }
  }

  public synchronized void stopIndexing() {
    Thread indexerThread = this.nodeIndexerThread;
    this.nodeIndexerThread = null;
    indexerThread.interrupt();
  }

  private void indexNodeStatuses() {
    long updateStatusMillis = -1L;
    DocumentStore documentStore = ApplicationFactory.getDocumentStore();
    UpdateStatus updateStatus = documentStore.retrieve(UpdateStatus.class,
        false);
    if (updateStatus != null &&
        updateStatus.getDocumentString() != null) {
      String updateString = updateStatus.getDocumentString();
      try {
        updateStatusMillis = Long.parseLong(updateString.trim());
      } catch (NumberFormatException e) {
        /* Handle below. */
      }
    }
    synchronized (this) {
      if (updateStatusMillis <= this.lastIndexed) {
        return;
      }
    }
    List<String> newRelaysByConsensusWeight = new ArrayList<String>();
    Map<String, SummaryDocument>
        newRelayFingerprintSummaryLines =
        new HashMap<String, SummaryDocument>(),
        newBridgeFingerprintSummaryLines =
        new HashMap<String, SummaryDocument>();
    Map<String, Set<String>>
        newRelaysByCountryCode = new HashMap<String, Set<String>>(),
        newRelaysByASNumber = new HashMap<String, Set<String>>(),
        newRelaysByFlag = new HashMap<String, Set<String>>(),
        newBridgesByFlag = new HashMap<String, Set<String>>(),
        newRelaysByContact = new HashMap<String, Set<String>>(),
        newRelaysByFamily = new HashMap<String, Set<String>>();
    SortedMap<Integer, Set<String>>
        newRelaysByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
        newBridgesByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
        newRelaysByLastSeenDays = new TreeMap<Integer, Set<String>>(),
        newBridgesByLastSeenDays = new TreeMap<Integer, Set<String>>();
    Set<SummaryDocument> currentRelays = new HashSet<SummaryDocument>(),
        currentBridges = new HashSet<SummaryDocument>();
    SortedSet<String> fingerprints = documentStore.list(
        SummaryDocument.class);
    long relaysLastValidAfterMillis = 0L, bridgesLastPublishedMillis = 0L;
    for (String fingerprint : fingerprints) {
      SummaryDocument node = documentStore.retrieve(SummaryDocument.class,
          true, fingerprint);
      if (node.isRelay()) {
        relaysLastValidAfterMillis = Math.max(
            relaysLastValidAfterMillis, node.getLastSeenMillis());
        currentRelays.add(node);
      } else {
        bridgesLastPublishedMillis = Math.max(
            bridgesLastPublishedMillis, node.getLastSeenMillis());
        currentBridges.add(node);
      }
    }
    Time time = ApplicationFactory.getTime();
    List<String> orderRelaysByConsensusWeight = new ArrayList<String>();
    for (SummaryDocument entry : currentRelays) {
      String fingerprint = entry.getFingerprint().toUpperCase();
      String hashedFingerprint = entry.getHashedFingerprint().
          toUpperCase();
      newRelayFingerprintSummaryLines.put(fingerprint, entry);
      newRelayFingerprintSummaryLines.put(hashedFingerprint, entry);
      long consensusWeight = entry.getConsensusWeight();
      orderRelaysByConsensusWeight.add(String.format("%020d %s",
          consensusWeight, fingerprint));
      orderRelaysByConsensusWeight.add(String.format("%020d %s",
          consensusWeight, hashedFingerprint));
      if (entry.getCountryCode() != null) {
        String countryCode = entry.getCountryCode();
        if (!newRelaysByCountryCode.containsKey(countryCode)) {
          newRelaysByCountryCode.put(countryCode,
              new HashSet<String>());
        }
        newRelaysByCountryCode.get(countryCode).add(fingerprint);
        newRelaysByCountryCode.get(countryCode).add(hashedFingerprint);
      }
      if (entry.getASNumber() != null) {
        String aSNumber = entry.getASNumber();
        if (!newRelaysByASNumber.containsKey(aSNumber)) {
          newRelaysByASNumber.put(aSNumber, new HashSet<String>());
        }
        newRelaysByASNumber.get(aSNumber).add(fingerprint);
        newRelaysByASNumber.get(aSNumber).add(hashedFingerprint);
      }
      for (String flag : entry.getRelayFlags()) {
        String flagLowerCase = flag.toLowerCase();
        if (!newRelaysByFlag.containsKey(flagLowerCase)) {
          newRelaysByFlag.put(flagLowerCase, new HashSet<String>());
        }
        newRelaysByFlag.get(flagLowerCase).add(fingerprint);
        newRelaysByFlag.get(flagLowerCase).add(hashedFingerprint);
      }
      if (entry.getFamilyFingerprints() != null) {
        newRelaysByFamily.put(fingerprint, entry.getFamilyFingerprints());
      }
      int daysSinceFirstSeen = (int) ((time.currentTimeMillis()
          - entry.getFirstSeenMillis()) / DateTimeHelper.ONE_DAY);
      if (!newRelaysByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
        newRelaysByFirstSeenDays.put(daysSinceFirstSeen,
            new HashSet<String>());
      }
      newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(fingerprint);
      newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedFingerprint);
      int daysSinceLastSeen = (int) ((time.currentTimeMillis()
          - entry.getLastSeenMillis()) / DateTimeHelper.ONE_DAY);
      if (!newRelaysByLastSeenDays.containsKey(daysSinceLastSeen)) {
        newRelaysByLastSeenDays.put(daysSinceLastSeen,
            new HashSet<String>());
      }
      newRelaysByLastSeenDays.get(daysSinceLastSeen).add(fingerprint);
      newRelaysByLastSeenDays.get(daysSinceLastSeen).add(
          hashedFingerprint);
      String contact = entry.getContact();
      if (!newRelaysByContact.containsKey(contact)) {
        newRelaysByContact.put(contact, new HashSet<String>());
      }
      newRelaysByContact.get(contact).add(fingerprint);
      newRelaysByContact.get(contact).add(hashedFingerprint);
    }
    Collections.sort(orderRelaysByConsensusWeight);
    newRelaysByConsensusWeight = new ArrayList<String>();
    for (String relay : orderRelaysByConsensusWeight) {
      newRelaysByConsensusWeight.add(relay.split(" ")[1]);
    }
    for (Map.Entry<String, Set<String>> e :
        newRelaysByFamily.entrySet()) {
      String fingerprint = e.getKey();
      Set<String> inMutualFamilyRelation = new HashSet<String>();
      for (String otherFingerprint : e.getValue()) {
        if (newRelaysByFamily.containsKey(otherFingerprint) &&
            newRelaysByFamily.get(otherFingerprint).contains(
                fingerprint)) {
          inMutualFamilyRelation.add(otherFingerprint);
        }
      }
      e.getValue().retainAll(inMutualFamilyRelation);
    }
    for (SummaryDocument entry : currentBridges) {
      String hashedFingerprint = entry.getFingerprint().toUpperCase();
      String hashedHashedFingerprint = entry.getHashedFingerprint().
          toUpperCase();
      newBridgeFingerprintSummaryLines.put(hashedFingerprint, entry);
      newBridgeFingerprintSummaryLines.put(hashedHashedFingerprint,
          entry);
      for (String flag : entry.getRelayFlags()) {
        String flagLowerCase = flag.toLowerCase();
        if (!newBridgesByFlag.containsKey(flagLowerCase)) {
          newBridgesByFlag.put(flagLowerCase, new HashSet<String>());
        }
        newBridgesByFlag.get(flagLowerCase).add(hashedFingerprint);
        newBridgesByFlag.get(flagLowerCase).add(
            hashedHashedFingerprint);
      }
      int daysSinceFirstSeen = (int) ((time.currentTimeMillis()
          - entry.getFirstSeenMillis()) / DateTimeHelper.ONE_DAY);
      if (!newBridgesByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
        newBridgesByFirstSeenDays.put(daysSinceFirstSeen,
            new HashSet<String>());
      }
      newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedFingerprint);
      newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedHashedFingerprint);
      int daysSinceLastSeen = (int) ((time.currentTimeMillis()
          - entry.getLastSeenMillis()) / DateTimeHelper.ONE_DAY);
      if (!newBridgesByLastSeenDays.containsKey(daysSinceLastSeen)) {
        newBridgesByLastSeenDays.put(daysSinceLastSeen,
            new HashSet<String>());
      }
      newBridgesByLastSeenDays.get(daysSinceLastSeen).add(
          hashedFingerprint);
      newBridgesByLastSeenDays.get(daysSinceLastSeen).add(
          hashedHashedFingerprint);
    }
    NodeIndex newNodeIndex = new NodeIndex();
    newNodeIndex.setRelaysByConsensusWeight(newRelaysByConsensusWeight);
    newNodeIndex.setRelayFingerprintSummaryLines(
        newRelayFingerprintSummaryLines);
    newNodeIndex.setBridgeFingerprintSummaryLines(
        newBridgeFingerprintSummaryLines);
    newNodeIndex.setRelaysByCountryCode(newRelaysByCountryCode);
    newNodeIndex.setRelaysByASNumber(newRelaysByASNumber);
    newNodeIndex.setRelaysByFlag(newRelaysByFlag);
    newNodeIndex.setBridgesByFlag(newBridgesByFlag);
    newNodeIndex.setRelaysByContact(newRelaysByContact);
    newNodeIndex.setRelaysByFamily(newRelaysByFamily);
    newNodeIndex.setRelaysByFirstSeenDays(newRelaysByFirstSeenDays);
    newNodeIndex.setRelaysByLastSeenDays(newRelaysByLastSeenDays);
    newNodeIndex.setBridgesByFirstSeenDays(newBridgesByFirstSeenDays);
    newNodeIndex.setBridgesByLastSeenDays(newBridgesByLastSeenDays);
    newNodeIndex.setRelaysPublishedString(DateTimeHelper.format(
        relaysLastValidAfterMillis));
    newNodeIndex.setBridgesPublishedString(DateTimeHelper.format(
        bridgesLastPublishedMillis));
    synchronized (this) {
      this.lastIndexed = updateStatusMillis;
      this.latestNodeIndex = newNodeIndex;
      this.notifyAll();
    }
  }
}

