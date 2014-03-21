/* Copyright 2011--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

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

public class RequestHandler {

  private static long summaryFileLastModified = -1L;
  private static DocumentStore documentStore;
  private static Time time;
  private static boolean successfullyReadSummaryFile = false;
  private static String relaysPublishedString, bridgesPublishedString;
  private static List<String> relaysByConsensusWeight = null;
  private static Map<String, String> relayFingerprintSummaryLines = null,
      bridgeFingerprintSummaryLines = null;
  private static Map<String, Set<String>> relaysByCountryCode = null,
      relaysByASNumber = null, relaysByFlag = null, bridgesByFlag = null,
      relaysByContact = null;
  private static SortedMap<Integer, Set<String>>
      relaysByFirstSeenDays = null, bridgesByFirstSeenDays = null,
      relaysByLastSeenDays = null, bridgesByLastSeenDays = null;
  private static final long SUMMARY_MAX_AGE = DateTimeHelper.SIX_HOURS;

  public static void initialize() {
    documentStore = ApplicationFactory.getDocumentStore();
    time = ApplicationFactory.getTime();
    readSummaryFile();
  }

  public static boolean update() {
    readSummaryFile();
    return successfullyReadSummaryFile;
  }

  private static void readSummaryFile() {
    long newSummaryFileLastModified = -1L;
    UpdateStatus updateStatus = documentStore.retrieve(UpdateStatus.class,
        false);
    if (updateStatus != null &&
        updateStatus.getDocumentString() != null) {
      String updateString = updateStatus.getDocumentString();
      try {
        newSummaryFileLastModified = Long.parseLong(updateString.trim());
      } catch (NumberFormatException e) {
        /* Handle below. */
      }
    }
    if (newSummaryFileLastModified < 0L) {
      // TODO Does this actually solve anything?  Should we instead
      // switch to a variant of the maintenance mode and re-check when
      // the next requests comes in that happens x seconds after this one?
      successfullyReadSummaryFile = false;
      return;
    }
    if (newSummaryFileLastModified + SUMMARY_MAX_AGE
        < time.currentTimeMillis()) {
      // TODO Does this actually solve anything?  Should we instead
      // switch to a variant of the maintenance mode and re-check when
      // the next requests comes in that happens x seconds after this one?
      successfullyReadSummaryFile = false;
      return;
    }
    if (newSummaryFileLastModified > summaryFileLastModified) {
      List<String> newRelaysByConsensusWeight = new ArrayList<String>();
      Map<String, String>
          newRelayFingerprintSummaryLines = new HashMap<String, String>(),
          newBridgeFingerprintSummaryLines =
          new HashMap<String, String>();
      Map<String, Set<String>>
          newRelaysByCountryCode = new HashMap<String, Set<String>>(),
          newRelaysByASNumber = new HashMap<String, Set<String>>(),
          newRelaysByFlag = new HashMap<String, Set<String>>(),
          newBridgesByFlag = new HashMap<String, Set<String>>(),
          newRelaysByContact = new HashMap<String, Set<String>>();
      SortedMap<Integer, Set<String>>
          newRelaysByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
          newBridgesByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
          newRelaysByLastSeenDays = new TreeMap<Integer, Set<String>>(),
          newBridgesByLastSeenDays = new TreeMap<Integer, Set<String>>();
      long relaysLastValidAfterMillis = -1L,
          bridgesLastPublishedMillis = -1L;
      String newRelaysPublishedString, newBridgesPublishedString;
      Set<NodeStatus> currentRelays = new HashSet<NodeStatus>(),
          currentBridges = new HashSet<NodeStatus>();
      SortedSet<String> fingerprints = documentStore.list(
          NodeStatus.class, false);
      // TODO We should be able to learn if something goes wrong when
      // reading the summary file, rather than silently having an empty
      // list of fingerprints.
      for (String fingerprint : fingerprints) {
        NodeStatus node = documentStore.retrieve(NodeStatus.class, true,
            fingerprint);
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
      newRelaysPublishedString = DateTimeHelper.format(
          relaysLastValidAfterMillis);
      newBridgesPublishedString = DateTimeHelper.format(
          bridgesLastPublishedMillis);
      List<String> orderRelaysByConsensusWeight = new ArrayList<String>();
      for (NodeStatus entry : currentRelays) {
        String fingerprint = entry.getFingerprint().toUpperCase();
        String hashedFingerprint = entry.getHashedFingerprint().
            toUpperCase();
        entry.setRunning(entry.getLastSeenMillis() ==
            relaysLastValidAfterMillis);
        String line = formatRelaySummaryLine(entry);
        newRelayFingerprintSummaryLines.put(fingerprint, line);
        newRelayFingerprintSummaryLines.put(hashedFingerprint, line);
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
        int daysSinceFirstSeen = (int) ((newSummaryFileLastModified
            - entry.getFirstSeenMillis()) / 86400000L);
        if (!newRelaysByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
          newRelaysByFirstSeenDays.put(daysSinceFirstSeen,
              new HashSet<String>());
        }
        newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(fingerprint);
        newRelaysByFirstSeenDays.get(daysSinceFirstSeen).add(
            hashedFingerprint);
        int daysSinceLastSeen = (int) ((newSummaryFileLastModified
            - entry.getLastSeenMillis()) / 86400000L);
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
      for (NodeStatus entry : currentBridges) {
        String hashedFingerprint = entry.getFingerprint().toUpperCase();
        String hashedHashedFingerprint = entry.getHashedFingerprint().
            toUpperCase();
        entry.setRunning(entry.getRelayFlags().contains("Running") &&
            entry.getLastSeenMillis() == bridgesLastPublishedMillis);
        String line = formatBridgeSummaryLine(entry);
        newBridgeFingerprintSummaryLines.put(hashedFingerprint, line);
        newBridgeFingerprintSummaryLines.put(hashedHashedFingerprint,
            line);
        for (String flag : entry.getRelayFlags()) {
          String flagLowerCase = flag.toLowerCase();
          if (!newBridgesByFlag.containsKey(flagLowerCase)) {
            newBridgesByFlag.put(flagLowerCase, new HashSet<String>());
          }
          newBridgesByFlag.get(flagLowerCase).add(hashedFingerprint);
          newBridgesByFlag.get(flagLowerCase).add(
              hashedHashedFingerprint);
        }
        int daysSinceFirstSeen = (int) ((newSummaryFileLastModified
            - entry.getFirstSeenMillis()) / 86400000L);
        if (!newBridgesByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
          newBridgesByFirstSeenDays.put(daysSinceFirstSeen,
              new HashSet<String>());
        }
        newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
            hashedFingerprint);
        newBridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
            hashedHashedFingerprint);
        int daysSinceLastSeen = (int) ((newSummaryFileLastModified
            - entry.getLastSeenMillis()) / 86400000L);
        if (!newBridgesByLastSeenDays.containsKey(daysSinceLastSeen)) {
          newBridgesByLastSeenDays.put(daysSinceLastSeen,
              new HashSet<String>());
        }
        newBridgesByLastSeenDays.get(daysSinceLastSeen).add(
            hashedFingerprint);
        newBridgesByLastSeenDays.get(daysSinceLastSeen).add(
            hashedHashedFingerprint);
      }
      relaysByConsensusWeight = newRelaysByConsensusWeight;
      relayFingerprintSummaryLines = newRelayFingerprintSummaryLines;
      bridgeFingerprintSummaryLines = newBridgeFingerprintSummaryLines;
      relaysByCountryCode = newRelaysByCountryCode;
      relaysByASNumber = newRelaysByASNumber;
      relaysByFlag = newRelaysByFlag;
      bridgesByFlag = newBridgesByFlag;
      relaysByContact = newRelaysByContact;
      relaysByFirstSeenDays = newRelaysByFirstSeenDays;
      relaysByLastSeenDays = newRelaysByLastSeenDays;
      bridgesByFirstSeenDays = newBridgesByFirstSeenDays;
      bridgesByLastSeenDays = newBridgesByLastSeenDays;
      relaysPublishedString = newRelaysPublishedString;
      bridgesPublishedString = newBridgesPublishedString;
    }
    summaryFileLastModified = newSummaryFileLastModified;
    successfullyReadSummaryFile = true;
  }

  private static String formatRelaySummaryLine(NodeStatus entry) {
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String fingerprint = entry.getFingerprint();
    String running = entry.getRunning() ? "true" : "false";
    List<String> addresses = new ArrayList<String>();
    addresses.add(entry.getAddress());
    for (String orAddress : entry.getOrAddresses()) {
      addresses.add(orAddress);
    }
    for (String exitAddress : entry.getExitAddresses()) {
      if (!addresses.contains(exitAddress)) {
        addresses.add(exitAddress);
      }
    }
    StringBuilder addressesBuilder = new StringBuilder();
    int written = 0;
    for (String address : addresses) {
      addressesBuilder.append((written++ > 0 ? "," : "") + "\""
          + address.toLowerCase() + "\"");
    }
    return String.format("{%s\"f\":\"%s\",\"a\":[%s],\"r\":%s}",
        (nickname == null ? "" : "\"n\":\"" + nickname + "\","),
        fingerprint, addressesBuilder.toString(), running);
  }

  private static String formatBridgeSummaryLine(NodeStatus entry) {
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String hashedFingerprint = entry.getFingerprint();
    String running = entry.getRunning() ? "true" : "false";
    return String.format("{%s\"h\":\"%s\",\"r\":%s}",
         (nickname == null ? "" : "\"n\":\"" + nickname + "\","),
         hashedFingerprint, running);
  }

  public static long getLastModified() {
    readSummaryFile();
    return summaryFileLastModified;
  }

  private String resourceType;
  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  private String type;
  public void setType(String type) {
    this.type = type;
  }

  private String running;
  public void setRunning(String running) {
    this.running = running;
  }

  private String[] search;
  public void setSearch(String[] search) {
    this.search = new String[search.length];
    System.arraycopy(search, 0, this.search, 0, search.length);
  }

  private String lookup;
  public void setLookup(String lookup) {
    this.lookup = lookup;
  }

  private String country;
  public void setCountry(String country) {
    this.country = country;
  }

  private String as;
  public void setAs(String as) {
    this.as = as;
  }

  private String flag;
  public void setFlag(String flag) {
    this.flag = flag;
  }

  private String[] contact;
  public void setContact(String[] contact) {
    this.contact = new String[contact.length];
    System.arraycopy(contact, 0, this.contact, 0, contact.length);
  }

  private String[] order;
  public void setOrder(String[] order) {
    this.order = new String[order.length];
    System.arraycopy(order, 0, this.order, 0, order.length);
  }

  private String offset;
  public void setOffset(String offset) {
    this.offset = offset;
  }

  private String limit;
  public void setLimit(String limit) {
    this.limit = limit;
  }

  private int[] firstSeenDays;
  public void setFirstSeenDays(int[] firstSeenDays) {
    this.firstSeenDays = new int[firstSeenDays.length];
    System.arraycopy(firstSeenDays, 0, this.firstSeenDays, 0,
        firstSeenDays.length);
  }

  private int[] lastSeenDays;
  public void setLastSeenDays(int[] lastSeenDays) {
    this.lastSeenDays = new int[lastSeenDays.length];
    System.arraycopy(lastSeenDays, 0, this.lastSeenDays, 0,
        lastSeenDays.length);
  }

  private Map<String, String> filteredRelays =
      new HashMap<String, String>();

  private Map<String, String> filteredBridges =
      new HashMap<String, String>();

  public void handleRequest() {
    this.filteredRelays.putAll(relayFingerprintSummaryLines);
    this.filteredBridges.putAll(bridgeFingerprintSummaryLines);
    this.filterByResourceType();
    this.filterByType();
    this.filterByRunning();
    this.filterBySearchTerms();
    this.filterByFingerprint();
    this.filterByCountryCode();
    this.filterByASNumber();
    this.filterByFlag();
    this.filterNodesByFirstSeenDays();
    this.filterNodesByLastSeenDays();
    this.filterByContact();
    this.order();
    this.offset();
    this.limit();
  }


  private void filterByResourceType() {
    if (this.resourceType.equals("clients")) {
      this.filteredRelays.clear();
    }
    if (this.resourceType.equals("weights")) {
      this.filteredBridges.clear();
    }
  }

  private void filterByType() {
    if (this.type == null) {
      return;
    } else if (this.type.equals("relay")) {
      this.filteredBridges.clear();
    } else {
      this.filteredRelays.clear();
    }
  }

  private void filterByRunning() {
    if (this.running == null) {
      return;
    }
    boolean runningRequested = this.running.equals("true");
    Set<String> removeRelays = new HashSet<String>();
    for (Map.Entry<String, String> e : filteredRelays.entrySet()) {
      if (e.getValue().contains("\"r\":true") != runningRequested) {
        removeRelays.add(e.getKey());
      }
    }
    for (String fingerprint : removeRelays) {
      this.filteredRelays.remove(fingerprint);
    }
    Set<String> removeBridges = new HashSet<String>();
    for (Map.Entry<String, String> e : filteredBridges.entrySet()) {
      if (e.getValue().contains("\"r\":true") != runningRequested) {
        removeBridges.add(e.getKey());
      }
    }
    for (String fingerprint : removeBridges) {
      this.filteredBridges.remove(fingerprint);
    }
  }

  private void filterBySearchTerms() {
    if (this.search == null) {
      return;
    }
    for (String searchTerm : this.search) {
      filterBySearchTerm(searchTerm);
    }
  }

  private void filterBySearchTerm(String searchTerm) {
    Set<String> removeRelays = new HashSet<String>();
    for (Map.Entry<String, String> e : filteredRelays.entrySet()) {
      String fingerprint = e.getKey();
      String line = e.getValue();
      boolean lineMatches = false;
      String nickname = "unnamed";
      if (line.contains("\"n\":\"")) {
        nickname = line.substring(line.indexOf("\"n\":\"") + 5).
            split("\"")[0].toLowerCase();
      }
      if (searchTerm.startsWith("$")) {
        /* Search is for $-prefixed fingerprint. */
        if (fingerprint.startsWith(
            searchTerm.substring(1).toUpperCase())) {
          /* $-prefixed fingerprint matches. */
          lineMatches = true;
        }
      } else if (nickname.contains(searchTerm.toLowerCase())) {
        /* Nickname matches. */
        lineMatches = true;
      } else if (fingerprint.startsWith(searchTerm.toUpperCase())) {
        /* Non-$-prefixed fingerprint matches. */
        lineMatches = true;
      } else if (line.substring(line.indexOf("\"a\":[")).contains("\""
          + searchTerm.toLowerCase())) {
        /* Address matches. */
        lineMatches = true;
      }
      if (!lineMatches) {
        removeRelays.add(e.getKey());
      }
    }
    for (String fingerprint : removeRelays) {
      this.filteredRelays.remove(fingerprint);
    }
    Set<String> removeBridges = new HashSet<String>();
    for (Map.Entry<String, String> e : filteredBridges.entrySet()) {
      String hashedFingerprint = e.getKey();
      String line = e.getValue();
      boolean lineMatches = false;
      String nickname = "unnamed";
      if (line.contains("\"n\":\"")) {
        nickname = line.substring(line.indexOf("\"n\":\"") + 5).
            split("\"")[0].toLowerCase();
      }
      if (searchTerm.startsWith("$")) {
        /* Search is for $-prefixed hashed fingerprint. */
        if (hashedFingerprint.startsWith(
            searchTerm.substring(1).toUpperCase())) {
          /* $-prefixed hashed fingerprint matches. */
          lineMatches = true;
        }
      } else if (nickname.contains(searchTerm.toLowerCase())) {
        /* Nickname matches. */
        lineMatches = true;
      } else if (hashedFingerprint.startsWith(searchTerm.toUpperCase())) {
        /* Non-$-prefixed hashed fingerprint matches. */
        lineMatches = true;
      }
      if (!lineMatches) {
        removeBridges.add(e.getKey());
      }
    }
    for (String fingerprint : removeBridges) {
      this.filteredBridges.remove(fingerprint);
    }
  }

  private void filterByFingerprint() {
    if (this.lookup == null) {
      return;
    }
    String fingerprint = this.lookup;
    String relayLine = this.filteredRelays.get(fingerprint);
    this.filteredRelays.clear();
    if (relayLine != null) {
      this.filteredRelays.put(fingerprint, relayLine);
    }
    String bridgeLine = this.filteredBridges.get(fingerprint);
    this.filteredBridges.clear();
    if (bridgeLine != null) {
      this.filteredBridges.put(fingerprint, bridgeLine);
    }
  }

  private void filterByCountryCode() {
    if (this.country == null) {
      return;
    }
    String countryCode = this.country.toLowerCase();
    if (!relaysByCountryCode.containsKey(countryCode)) {
      this.filteredRelays.clear();
    } else {
      Set<String> relaysWithCountryCode =
          relaysByCountryCode.get(countryCode);
      Set<String> removeRelays = new HashSet<String>();
      for (Map.Entry<String, String> e : this.filteredRelays.entrySet()) {
        String fingerprint = e.getKey();
        if (!relaysWithCountryCode.contains(fingerprint)) {
          removeRelays.add(fingerprint);
        }
      }
      for (String fingerprint : removeRelays) {
        this.filteredRelays.remove(fingerprint);
      }
    }
    this.filteredBridges.clear();
  }

  private void filterByASNumber() {
    if (this.as == null) {
      return;
    }
    String aSNumber = this.as.toUpperCase();
    if (!aSNumber.startsWith("AS")) {
      aSNumber = "AS" + aSNumber;
    }
    if (!relaysByASNumber.containsKey(aSNumber)) {
      this.filteredRelays.clear();
    } else {
      Set<String> relaysWithASNumber =
          relaysByASNumber.get(aSNumber);
      Set<String> removeRelays = new HashSet<String>();
      for (Map.Entry<String, String> e : this.filteredRelays.entrySet()) {
        String fingerprint = e.getKey();
        if (!relaysWithASNumber.contains(fingerprint)) {
          removeRelays.add(fingerprint);
        }
      }
      for (String fingerprint : removeRelays) {
        this.filteredRelays.remove(fingerprint);
      }
    }
    this.filteredBridges.clear();
  }

  private void filterByFlag() {
    if (this.flag == null) {
      return;
    }
    String flag = this.flag.toLowerCase();
    if (!relaysByFlag.containsKey(flag)) {
      this.filteredRelays.clear();
    } else {
      Set<String> relaysWithFlag = relaysByFlag.get(flag);
      Set<String> removeRelays = new HashSet<String>();
      for (Map.Entry<String, String> e : this.filteredRelays.entrySet()) {
        String fingerprint = e.getKey();
        if (!relaysWithFlag.contains(fingerprint)) {
          removeRelays.add(fingerprint);
        }
      }
      for (String fingerprint : removeRelays) {
        this.filteredRelays.remove(fingerprint);
      }
    }
    if (!bridgesByFlag.containsKey(flag)) {
      this.filteredBridges.clear();
    } else {
      Set<String> bridgesWithFlag = bridgesByFlag.get(flag);
      Set<String> removeBridges = new HashSet<String>();
      for (Map.Entry<String, String> e :
          this.filteredBridges.entrySet()) {
        String fingerprint = e.getKey();
        if (!bridgesWithFlag.contains(fingerprint)) {
          removeBridges.add(fingerprint);
        }
      }
      for (String fingerprint : removeBridges) {
        this.filteredBridges.remove(fingerprint);
      }
    }
  }

  private void filterNodesByFirstSeenDays() {
    if (this.firstSeenDays == null) {
      return;
    }
    filterNodesByDays(this.filteredRelays, relaysByFirstSeenDays,
        this.firstSeenDays);
    filterNodesByDays(this.filteredBridges, bridgesByFirstSeenDays,
        this.firstSeenDays);
  }

  private void filterNodesByLastSeenDays() {
    if (this.lastSeenDays == null) {
      return;
    }
    filterNodesByDays(this.filteredRelays, relaysByLastSeenDays,
        this.lastSeenDays);
    filterNodesByDays(this.filteredBridges, bridgesByLastSeenDays,
        this.lastSeenDays);
  }

  private void filterNodesByDays(Map<String, String> filteredNodes,
      SortedMap<Integer, Set<String>> nodesByDays, int[] days) {
    Set<String> removeNodes = new HashSet<String>();
    for (Set<String> nodes : nodesByDays.headMap(days[0]).values()) {
      removeNodes.addAll(nodes);
    }
    if (days[1] < Integer.MAX_VALUE) {
      for (Set<String> nodes :
          nodesByDays.tailMap(days[1] + 1).values()) {
        removeNodes.addAll(nodes);
      }
    }
    for (String fingerprint : removeNodes) {
      filteredNodes.remove(fingerprint);
    }
  }

  private void filterByContact() {
    if (this.contact == null) {
      return;
    }
    Set<String> removeRelays = new HashSet<String>();
    for (Map.Entry<String, Set<String>> e : relaysByContact.entrySet()) {
      String contact = e.getKey();
      for (String contactPart : this.contact) {
        if (contact == null ||
            !contact.contains(contactPart.toLowerCase())) {
          removeRelays.addAll(e.getValue());
          break;
        }
      }
    }
    for (String fingerprint : removeRelays) {
      this.filteredRelays.remove(fingerprint);
    }
    this.filteredBridges.clear();
  }

  private void order() {
    if (this.order != null && this.order.length == 1) {
      List<String> orderBy = new ArrayList<String>(
          relaysByConsensusWeight);
      if (this.order[0].startsWith("-")) {
        Collections.reverse(orderBy);
      }
      for (String relay : orderBy) {
        if (this.filteredRelays.containsKey(relay) &&
            !this.orderedRelays.contains(filteredRelays.get(relay))) {
          this.orderedRelays.add(this.filteredRelays.remove(relay));
        }
      }
      for (String relay : this.filteredRelays.keySet()) {
        if (!this.orderedRelays.contains(this.filteredRelays.get(relay))) {
          this.orderedRelays.add(this.filteredRelays.remove(relay));
        }
      }
      Set<String> uniqueBridges = new HashSet<String>(
          this.filteredBridges.values());
      this.orderedBridges.addAll(uniqueBridges);
    } else {
      Set<String> uniqueRelays = new HashSet<String>(
          this.filteredRelays.values());
      this.orderedRelays.addAll(uniqueRelays);
      Set<String> uniqueBridges = new HashSet<String>(
          this.filteredBridges.values());
      this.orderedBridges.addAll(uniqueBridges);
    }
  }

  private void offset() {
    if (this.offset == null) {
      return;
    }
    int offsetValue = Integer.parseInt(this.offset);
    while (offsetValue-- > 0 &&
        (!this.orderedRelays.isEmpty() ||
        !this.orderedBridges.isEmpty())) {
      if (!this.orderedRelays.isEmpty()) {
        this.orderedRelays.remove(0);
      } else {
        this.orderedBridges.remove(0);
      }
    }
  }

  private void limit() {
    if (this.limit == null) {
      return;
    }
    int limitValue = Integer.parseInt(this.limit);
    while (!this.orderedRelays.isEmpty() &&
        limitValue < this.orderedRelays.size()) {
      this.orderedRelays.remove(this.orderedRelays.size() - 1);
    }
    limitValue -= this.orderedRelays.size();
    while (!this.orderedBridges.isEmpty() &&
        limitValue < this.orderedBridges.size()) {
      this.orderedBridges.remove(this.orderedBridges.size() - 1);
    }
  }

  private List<String> orderedRelays = new ArrayList<String>();
  public List<String> getOrderedRelays() {
    return this.orderedRelays;
  }

  private List<String> orderedBridges = new ArrayList<String>();
  public List<String> getOrderedBridges() {
    return this.orderedBridges;
  }

  public String getRelaysPublishedString() {
    return relaysPublishedString;
  }

  public String getBridgesPublishedString() {
    return bridgesPublishedString;
  }
}
