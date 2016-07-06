/* Copyright 2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.SummaryDocument;
import org.torproject.onionoo.docs.UpdateStatus;
import org.torproject.onionoo.util.Time;
import org.torproject.onionoo.util.TimeFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class NodeIndexer implements ServletContextListener, Runnable {

  private static final Logger log = LoggerFactory.getLogger(
      NodeIndexer.class);

  public void contextInitialized(ServletContextEvent contextEvent) {
    ServletContext servletContext = contextEvent.getServletContext();
    File outDir = new File(servletContext.getInitParameter("outDir"));
    if (!outDir.exists() || !outDir.isDirectory()) {
      log.error("\n\n\tOut-dir not found! Expected directory: " + outDir
          + "\n\tVerify the configuration in ./etc/web.xml.template");
      System.exit(1);
    }
    DocumentStore documentStore = DocumentStoreFactory.getDocumentStore();
    documentStore.setOutDir(outDir);
    /* The servlet container created us, and we need to avoid that
     * ApplicationFactory creates another instance of us. */
    NodeIndexerFactory.setNodeIndexer(this);
    this.startIndexing();
  }

  public void contextDestroyed(ServletContextEvent contextEvent) {
    this.stopIndexing();
  }

  private long lastIndexed = -1L;

  private NodeIndex latestNodeIndex = null;

  private Thread nodeIndexerThread = null;

  public synchronized long getLastIndexed(long timeoutMillis) {
    if (this.lastIndexed == -1L && this.nodeIndexerThread != null
        && timeoutMillis > 0L) {
      try {
        this.wait(timeoutMillis);
      } catch (InterruptedException e) {
      }
    }
    return this.lastIndexed;
  }

  public synchronized NodeIndex getLatestNodeIndex(long timeoutMillis) {
    if (this.latestNodeIndex == null && this.nodeIndexerThread != null
        && timeoutMillis > 0L) {
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

  private static final long ONE_MINUTE = 60L * 1000L;

  private static final long ONE_DAY = 24L * 60L * ONE_MINUTE;

  public void run() {
    while (this.nodeIndexerThread != null) {
      this.indexNodeStatuses();
      try {
        Thread.sleep(ONE_MINUTE);
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
    DocumentStore documentStore = DocumentStoreFactory.getDocumentStore();
    UpdateStatus updateStatus = documentStore.retrieve(UpdateStatus.class,
        true);
    if (updateStatus != null) {
      updateStatusMillis = updateStatus.getUpdatedMillis();
    }
    synchronized (this) {
      if (updateStatusMillis <= this.lastIndexed) {
        /* Index on disk is no more recent than the one in memory. */
        return;
      }
    }
    documentStore.invalidateDocumentCache();
    List<String> newRelaysByConsensusWeight = new ArrayList<String>();
    Map<String, SummaryDocument> newRelayFingerprintSummaryLines =
        new HashMap<String, SummaryDocument>();
    Map<String, SummaryDocument> newBridgeFingerprintSummaryLines =
        new HashMap<String, SummaryDocument>();
    Map<String, Set<String>> newRelaysByCountryCode =
        new HashMap<String, Set<String>>();
    Map<String, Set<String>> newRelaysByASNumber =
        new HashMap<String, Set<String>>();
    Map<String, Set<String>> newRelaysByFlag =
        new HashMap<String, Set<String>>();
    Map<String, Set<String>> newBridgesByFlag =
        new HashMap<String, Set<String>>();
    Map<String, Set<String>> newRelaysByContact =
        new HashMap<String, Set<String>>();
    Map<String, Set<String>> newRelaysByFamily =
        new HashMap<String, Set<String>>();
    SortedMap<Integer, Set<String>> newRelaysByFirstSeenDays =
        new TreeMap<Integer, Set<String>>();
    SortedMap<Integer, Set<String>> newBridgesByFirstSeenDays =
        new TreeMap<Integer, Set<String>>();
    SortedMap<Integer, Set<String>> newRelaysByLastSeenDays =
        new TreeMap<Integer, Set<String>>();
    SortedMap<Integer, Set<String>> newBridgesByLastSeenDays =
        new TreeMap<Integer, Set<String>>();
    Set<SummaryDocument> currentRelays = new HashSet<SummaryDocument>();
    Set<SummaryDocument> currentBridges = new HashSet<SummaryDocument>();
    SortedSet<String> fingerprints = documentStore.list(
        SummaryDocument.class);
    long relaysLastValidAfterMillis = 0L;
    long bridgesLastPublishedMillis = 0L;
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
    Time time = TimeFactory.getTime();
    List<String> orderRelaysByConsensusWeight = new ArrayList<String>();
    /* This variable can go away once all Onionoo services had their
     * hourly updater write effective families to summary documents at
     * least once.  Remove this code after September 8, 2015. */
    SortedMap<String, Set<String>> computedEffectiveFamilies =
        new TreeMap<String, Set<String>>();
    for (SummaryDocument entry : currentRelays) {
      String fingerprint = entry.getFingerprint().toUpperCase();
      String hashedFingerprint = entry.getHashedFingerprint()
          .toUpperCase();
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
      /* This condition can go away once all Onionoo services had their
       * hourly updater write effective families to summary documents at
       * least once.  Remove this code after September 8, 2015. */
      if (entry.getFamilyFingerprints() != null
          && !entry.getFamilyFingerprints().isEmpty()) {
        computedEffectiveFamilies.put(fingerprint,
            entry.getFamilyFingerprints());
      }
      if (entry.getEffectiveFamily() != null) {
        newRelaysByFamily.put(fingerprint, entry.getEffectiveFamily());
      }
      int daysSinceFirstSeen = (int) ((time.currentTimeMillis()
          - entry.getFirstSeenMillis()) / ONE_DAY);
      if (!newRelaysByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
        newRelaysByFirstSeenDays.put(daysSinceFirstSeen,
            new HashSet<String>());
      }
      newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(fingerprint);
      newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedFingerprint);
      int daysSinceLastSeen = (int) ((time.currentTimeMillis()
          - entry.getLastSeenMillis()) / ONE_DAY);
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
    /* This loop can go away once all Onionoo services had their hourly
     * updater write effective families to summary documents at least
     * once.  Remove this code after September 8, 2015. */
    for (Map.Entry<String, Set<String>> e :
        computedEffectiveFamilies.entrySet()) {
      String fingerprint = e.getKey();
      Set<String> inMutualFamilyRelation = new HashSet<String>();
      for (String otherFingerprint : e.getValue()) {
        if (computedEffectiveFamilies.containsKey(otherFingerprint)
            && computedEffectiveFamilies.get(otherFingerprint).contains(
            fingerprint)) {
          inMutualFamilyRelation.add(otherFingerprint);
        }
      }
      newRelaysByFamily.put(fingerprint, inMutualFamilyRelation);
    }
    for (SummaryDocument entry : currentBridges) {
      String hashedFingerprint = entry.getFingerprint().toUpperCase();
      String hashedHashedFingerprint = entry.getHashedFingerprint()
          .toUpperCase();
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
          - entry.getFirstSeenMillis()) / ONE_DAY);
      if (!newBridgesByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
        newBridgesByFirstSeenDays.put(daysSinceFirstSeen,
            new HashSet<String>());
      }
      newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedFingerprint);
      newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedHashedFingerprint);
      int daysSinceLastSeen = (int) ((time.currentTimeMillis()
          - entry.getLastSeenMillis()) / ONE_DAY);
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
    newNodeIndex.setRelaysPublishedMillis(relaysLastValidAfterMillis);
    newNodeIndex.setBridgesPublishedMillis(bridgesLastPublishedMillis);
    synchronized (this) {
      this.lastIndexed = updateStatusMillis;
      this.latestNodeIndex = newNodeIndex;
      this.notifyAll();
    }
  }
}

