/* Copyright 2016--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import static org.torproject.onionoo.docs.DateTimeHelper.ONE_DAY;
import static org.torproject.onionoo.docs.DateTimeHelper.ONE_MINUTE;

import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.SummaryDocument;
import org.torproject.onionoo.docs.UpdateStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class NodeIndexer implements ServletContextListener, Runnable {

  private static final Logger log = LoggerFactory.getLogger(
      NodeIndexer.class);

  @Override
  public void contextInitialized(ServletContextEvent contextEvent) {
    File outDir = new File(System.getProperty("onionoo.basedir",
        "/srv/onionoo.torproject.org/onionoo"), "out");
    if (!outDir.exists() || !outDir.isDirectory()) {
      log.error("\n\n\tOut-dir not found! Expected directory: " + outDir
          + "\n\tSet system property 'onionoo.basedir'.");
      System.exit(1);
    }
    DocumentStore documentStore = DocumentStoreFactory.getDocumentStore();
    documentStore.setOutDir(outDir);
    /* The servlet container created us, and we need to avoid that
     * ApplicationFactory creates another instance of us. */
    NodeIndexerFactory.setNodeIndexer(this);
    this.startIndexing();
  }

  @Override
  public void contextDestroyed(ServletContextEvent contextEvent) {
    this.stopIndexing();
  }

  private long lastIndexed = -1L;

  private NodeIndex latestNodeIndex = null;

  private Thread nodeIndexerThread = null;

  /** Returns the creation time of the last known node index in
   * milliseconds since the epoch, or <code>-1</code> if no node index
   * could be retrieved within <code>timeoutMillis</code> milliseconds. */
  public synchronized long getLastIndexed(long timeoutMillis) {
    if (this.lastIndexed == -1L && this.nodeIndexerThread != null
        && timeoutMillis > 0L) {
      try {
        this.wait(timeoutMillis);
      } catch (InterruptedException e) {
        /* Nothing that we could handle, just return what we have
         * below. */
      }
    }
    return this.lastIndexed;
  }

  /** Returns the last known node index, or null if no node index could be
   * retrieved within <code>timeoutMillis</code> milliseconds. */
  public synchronized NodeIndex getLatestNodeIndex(long timeoutMillis) {
    if (this.latestNodeIndex == null && this.nodeIndexerThread != null
        && timeoutMillis > 0L) {
      try {
        this.wait(timeoutMillis);
      } catch (InterruptedException e) {
        /* Nothing that we could handle, just return what we have
         * below. */
      }
    }
    return this.latestNodeIndex;
  }

  /** Start reading the node index into memory periodically in a
   * background thread. */
  public synchronized void startIndexing() {
    if (this.nodeIndexerThread == null) {
      this.nodeIndexerThread = new Thread(this, "Onionoo-Node-Indexer");
      this.nodeIndexerThread.setDaemon(true);
      this.nodeIndexerThread.start();
    }
  }

  @Override
  public void run() {
    try {
      while (this.nodeIndexerThread != null) {
        this.indexNodeStatuses();
        try {
          Thread.sleep(ONE_MINUTE);
        } catch (InterruptedException e) {
          /* Nothing that we could handle, just check if there's new data
           * to index now. */
        }
      }
    } catch (Throwable th) { // catch all and log
      log.error("Indexing failed: {}", th.getMessage(), th);
    }
  }

  /** Stop the background process that is periodically reading the node
   * index. */
  public synchronized void stopIndexing() {
    Thread indexerThread = this.nodeIndexerThread;
    this.nodeIndexerThread = null;
    indexerThread.interrupt();
  }

  /* specialTime is only used for testing, see ResourceServletTest */
  private long specialTime = -1L;

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
    Map<String, SummaryDocument> newRelayFingerprintSummaryLines =
        new HashMap<>();
    Map<String, SummaryDocument> newBridgeFingerprintSummaryLines =
        new HashMap<>();
    Map<String, Set<String>> newRelaysByCountryCode = new HashMap<>();
    Map<String, Set<String>> newRelaysByAsNumber = new HashMap<>();
    Map<String, Set<String>> newRelaysByAsName = new HashMap<>();
    Map<String, Set<String>> newRelaysByFlag = new HashMap<>();
    Map<String, Set<String>> newBridgesByFlag = new HashMap<>();
    Map<String, Set<String>> newRelaysByContact = new HashMap<>();
    Map<String, Set<String>> newRelaysByFamily = new HashMap<>();
    Map<String, Set<String>> newRelaysByVersion = new HashMap<>();
    Map<String, Set<String>> newBridgesByVersion = new HashMap<>();
    Map<String, Set<String>> newRelaysByOperatingSystem = new HashMap<>();
    Map<String, Set<String>> newBridgesByOperatingSystem = new HashMap<>();
    Map<String, Set<String>> newRelaysByHostName = new HashMap<>();
    Map<Boolean, Set<String>> newRelaysByRecommendedVersion = new HashMap<>();
    newRelaysByRecommendedVersion.put(true, new HashSet<>());
    newRelaysByRecommendedVersion.put(false, new HashSet<>());
    Map<Boolean, Set<String>> newBridgesByRecommendedVersion = new HashMap<>();
    newBridgesByRecommendedVersion.put(true, new HashSet<>());
    newBridgesByRecommendedVersion.put(false, new HashSet<>());
    SortedMap<Integer, Set<String>> newRelaysByFirstSeenDays = new TreeMap<>();
    SortedMap<Integer, Set<String>> newBridgesByFirstSeenDays = new TreeMap<>();
    SortedMap<Integer, Set<String>> newRelaysByLastSeenDays = new TreeMap<>();
    SortedMap<Integer, Set<String>> newBridgesByLastSeenDays = new TreeMap<>();
    Set<SummaryDocument> currentRelays = new HashSet<>();
    Set<SummaryDocument> currentBridges = new HashSet<>();
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

    /* This variable can go away once all Onionoo services had their
     * hourly updater write effective families to summary documents at
     * least once.  Remove this code after September 8, 2015. */
    SortedMap<String, Set<String>> computedEffectiveFamilies = new TreeMap<>();
    for (SummaryDocument entry : currentRelays) {
      String fingerprint = entry.getFingerprint().toUpperCase();
      String hashedFingerprint = entry.getHashedFingerprint()
          .toUpperCase();
      newRelayFingerprintSummaryLines.put(fingerprint, entry);
      newRelayFingerprintSummaryLines.put(hashedFingerprint, entry);
      String countryCode;
      if (null != entry.getCountryCode()) {
        countryCode = entry.getCountryCode();
      } else {
        /* The country code xz will never be assigned for use with ISO 3166-1
         * and is "user-assigned". Fun fact: UN/LOCODE assigns XZ to represent
         * installations in international waters. */
        countryCode = "xz";
      }
      if (!newRelaysByCountryCode.containsKey(countryCode)) {
        newRelaysByCountryCode.put(countryCode,
            new HashSet<String>());
      }
      newRelaysByCountryCode.get(countryCode).add(fingerprint);
      newRelaysByCountryCode.get(countryCode).add(hashedFingerprint);
      String asNumber;
      if (null != entry.getAsNumber()) {
        asNumber = entry.getAsNumber();
      } else {
        /* Autonomous system number 0 is reserved by RFC6707, to be used for
         * networks that are not routed. The use of this number should be,
         * and in the majority of cases probably is, filtered and so it
         * shouldn't appear in any lookup databases. */
        asNumber = "AS0";
      }
      if (!newRelaysByAsNumber.containsKey(asNumber)) {
        newRelaysByAsNumber.put(asNumber, new HashSet<String>());
      }
      newRelaysByAsNumber.get(asNumber).add(fingerprint);
      newRelaysByAsNumber.get(asNumber).add(hashedFingerprint);
      String asName = entry.getAsName();
      if (!newRelaysByAsName.containsKey(asName)) {
        newRelaysByAsName.put(asName, new HashSet<>());
      }
      newRelaysByAsName.get(asName).add(fingerprint);
      newRelaysByAsName.get(asName).add(hashedFingerprint);
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
      int daysSinceFirstSeen = (int) ((
          (specialTime < 0 ? System.currentTimeMillis() : specialTime)
          - entry.getFirstSeenMillis()) / ONE_DAY);
      if (!newRelaysByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
        newRelaysByFirstSeenDays.put(daysSinceFirstSeen,
            new HashSet<String>());
      }
      newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(fingerprint);
      newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedFingerprint);
      int daysSinceLastSeen = (int) ((
          (specialTime < 0 ? System.currentTimeMillis() : specialTime)
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
      String version = entry.getVersion();
      if (null != version) {
        if (!newRelaysByVersion.containsKey(version)) {
          newRelaysByVersion.put(version, new HashSet<String>());
        }
        newRelaysByVersion.get(version).add(fingerprint);
        newRelaysByVersion.get(version).add(hashedFingerprint);
      }
      String operatingSystem = entry.getOperatingSystem();
      if (null != operatingSystem) {
        if (!newRelaysByOperatingSystem.containsKey(operatingSystem)) {
          newRelaysByOperatingSystem.put(operatingSystem, new HashSet<>());
        }
        newRelaysByOperatingSystem.get(operatingSystem).add(fingerprint);
        newRelaysByOperatingSystem.get(operatingSystem).add(hashedFingerprint);
      }
      List<String> allHostNames = new ArrayList<>();
      List<String> verifiedHostNames = entry.getVerifiedHostNames();
      if (null != verifiedHostNames) {
        allHostNames.addAll(verifiedHostNames);
      }
      List<String> unverifiedHostNames = entry.getUnverifiedHostNames();
      if (null != unverifiedHostNames) {
        allHostNames.addAll(unverifiedHostNames);
      }
      for (String hostName : allHostNames) {
        String hostNameLowerCase = hostName.toLowerCase();
        if (!newRelaysByHostName.containsKey(hostNameLowerCase)) {
          newRelaysByHostName.put(hostNameLowerCase, new HashSet<>());
        }
        newRelaysByHostName.get(hostNameLowerCase).add(fingerprint);
        newRelaysByHostName.get(hostNameLowerCase).add(hashedFingerprint);
      }
      Boolean recommendedVersion = entry.getRecommendedVersion();
      if (null != recommendedVersion) {
        newRelaysByRecommendedVersion.get(recommendedVersion).add(fingerprint);
        newRelaysByRecommendedVersion.get(recommendedVersion).add(
            hashedFingerprint);
      }
    }
    /* This loop can go away once all Onionoo services had their hourly
     * updater write effective families to summary documents at least
     * once.  Remove this code after September 8, 2015. */
    for (Map.Entry<String, Set<String>> e :
        computedEffectiveFamilies.entrySet()) {
      String fingerprint = e.getKey();
      Set<String> inMutualFamilyRelation = new HashSet<>();
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
      int daysSinceFirstSeen = (int) ((
          (specialTime < 0 ? System.currentTimeMillis() : specialTime)
          - entry.getFirstSeenMillis()) / ONE_DAY);
      if (!newBridgesByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
        newBridgesByFirstSeenDays.put(daysSinceFirstSeen,
            new HashSet<String>());
      }
      newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedFingerprint);
      newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
          hashedHashedFingerprint);
      int daysSinceLastSeen = (int) ((
          (specialTime < 0 ? System.currentTimeMillis() : specialTime)
          - entry.getLastSeenMillis()) / ONE_DAY);
      if (!newBridgesByLastSeenDays.containsKey(daysSinceLastSeen)) {
        newBridgesByLastSeenDays.put(daysSinceLastSeen,
            new HashSet<String>());
      }
      newBridgesByLastSeenDays.get(daysSinceLastSeen).add(
          hashedFingerprint);
      newBridgesByLastSeenDays.get(daysSinceLastSeen).add(
          hashedHashedFingerprint);
      String version = entry.getVersion();
      if (null != version) {
        if (!newBridgesByVersion.containsKey(version)) {
          newBridgesByVersion.put(version, new HashSet<>());
        }
        newBridgesByVersion.get(version).add(hashedFingerprint);
        newBridgesByVersion.get(version).add(hashedHashedFingerprint);
      }
      String operatingSystem = entry.getOperatingSystem();
      if (null != operatingSystem) {
        if (!newBridgesByOperatingSystem.containsKey(operatingSystem)) {
          newBridgesByOperatingSystem.put(operatingSystem, new HashSet<>());
        }
        newBridgesByOperatingSystem.get(operatingSystem)
            .add(hashedFingerprint);
        newBridgesByOperatingSystem.get(operatingSystem)
            .add(hashedHashedFingerprint);
      }
      Boolean recommendedVersion = entry.getRecommendedVersion();
      if (null != recommendedVersion) {
        newBridgesByRecommendedVersion.get(recommendedVersion).add(
            hashedFingerprint);
        newBridgesByRecommendedVersion.get(recommendedVersion).add(
            hashedHashedFingerprint);
      }
    }
    NodeIndex newNodeIndex = new NodeIndex();
    newNodeIndex.setRelayFingerprintSummaryLines(
        newRelayFingerprintSummaryLines);
    newNodeIndex.setBridgeFingerprintSummaryLines(
        newBridgeFingerprintSummaryLines);
    newNodeIndex.setRelaysByCountryCode(newRelaysByCountryCode);
    newNodeIndex.setRelaysByAsNumber(newRelaysByAsNumber);
    newNodeIndex.setRelaysByAsName(newRelaysByAsName);
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
    newNodeIndex.setRelaysByVersion(newRelaysByVersion);
    newNodeIndex.setBridgesByVersion(newBridgesByVersion);
    newNodeIndex.setRelaysByOperatingSystem(newRelaysByOperatingSystem);
    newNodeIndex.setBridgesByOperatingSystem(newBridgesByOperatingSystem);
    newNodeIndex.setRelaysByHostName(newRelaysByHostName);
    newNodeIndex.setRelaysByRecommendedVersion(newRelaysByRecommendedVersion);
    newNodeIndex.setBridgesByRecommendedVersion(newBridgesByRecommendedVersion);
    synchronized (this) {
      this.lastIndexed = updateStatusMillis;
      this.latestNodeIndex = newNodeIndex;
      this.notifyAll();
    }
  }
}

