/* Copyright 2011--2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;

public class ResponseBuilder {

  private static long summaryFileLastModified = -1L;
  private static DocumentStore documentStore;
  private static Time time;
  private static boolean successfullyReadSummaryFile = false;
  private static String relaysPublishedString, bridgesPublishedString;
  private static List<String> relaysByConsensusWeight = null;
  private static Map<String, String> relayFingerprintSummaryLines = null,
      bridgeFingerprintSummaryLines = null;
  private static Map<String, Set<String>> relaysByCountryCode = null,
      relaysByASNumber = null, relaysByFlag = null,
      relaysByContact = null;
  private static SortedMap<Integer, Set<String>>
      relaysByFirstSeenDays = null, bridgesByFirstSeenDays = null,
      relaysByLastSeenDays = null, bridgesByLastSeenDays = null;
  private static final long SUMMARY_MAX_AGE = 6L * 60L * 60L * 1000L;

  public static void initialize(DocumentStore documentStoreParam,
      Time timeParam) {
    documentStore = documentStoreParam;
    time = timeParam;
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
    if (updateStatus != null && updateStatus.documentString != null) {
      String updateString = updateStatus.documentString;
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
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      newRelaysPublishedString = dateTimeFormat.format(
          relaysLastValidAfterMillis);
      newBridgesPublishedString = dateTimeFormat.format(
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

  private String resourceType, type, running, search[], lookup, country,
      as, flag, contact[], fields[], order[], offset, limit;
  private int[] firstSeenDays, lastSeenDays;

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }
  public void setType(String type) {
    this.type = type;
  }
  public void setRunning(String running) {
    this.running = running;
  }
  public void setSearch(String[] search) {
    this.search = search;
  }
  public void setLookup(String lookup) {
    this.lookup = lookup;
  }
  public void setCountry(String country) {
    this.country = country;
  }
  public void setAs(String as) {
    this.as = as;
  }
  public void setFlag(String flag) {
    this.flag = flag;
  }
  public void setFirstSeenDays(int[] firstSeenDays) {
    this.firstSeenDays = firstSeenDays;
  }
  public void setLastSeenDays(int[] lastSeenDays) {
    this.lastSeenDays = lastSeenDays;
  }
  public void setContact(String[] contact) {
    this.contact = contact;
  }
  public void setFields(String[] fields) {
    this.fields = fields;
  }
  public void setOrder(String[] order) {
    this.order = order;
  }
  public void setOffset(String offset) {
    this.offset = offset;
  }
  public void setLimit(String limit) {
    this.limit = limit;
  }

  public void buildResponse(PrintWriter pw) {

    /* Filter relays and bridges matching the request. */
    Map<String, String> filteredRelays = new HashMap<String, String>(
        relayFingerprintSummaryLines);
    Map<String, String> filteredBridges = new HashMap<String, String>(
        bridgeFingerprintSummaryLines);
    filterByType(filteredRelays, filteredBridges);
    filterByRunning(filteredRelays, filteredBridges);
    filterBySearchTerms(filteredRelays, filteredBridges);
    filterByFingerprint(filteredRelays, filteredBridges);
    filterByCountryCode(filteredRelays, filteredBridges);
    filterByASNumber(filteredRelays, filteredBridges);
    filterByFlag(filteredRelays, filteredBridges);
    filterNodesByFirstSeenDays(filteredRelays, filteredBridges);
    filterNodesByLastSeenDays(filteredRelays, filteredBridges);
    filterByContact(filteredRelays, filteredBridges);

    /* Re-order and limit results. */
    List<String> orderedRelays = new ArrayList<String>();
    List<String> orderedBridges = new ArrayList<String>();
    order(filteredRelays, filteredBridges, orderedRelays, orderedBridges);
    offset(orderedRelays, orderedBridges);
    limit(orderedRelays, orderedBridges);

    /* Write the response. */
    writeRelays(orderedRelays, pw);
    writeBridges(orderedBridges, pw);
  }

  private void filterByType(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.type == null) {
      return;
    } else if (this.type.equals("relay")) {
      filteredBridges.clear();
    } else {
      filteredRelays.clear();
    }
  }

  private void filterByRunning(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
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
      filteredRelays.remove(fingerprint);
    }
    Set<String> removeBridges = new HashSet<String>();
    for (Map.Entry<String, String> e : filteredBridges.entrySet()) {
      if (e.getValue().contains("\"r\":true") != runningRequested) {
        removeBridges.add(e.getKey());
      }
    }
    for (String fingerprint : removeBridges) {
      filteredBridges.remove(fingerprint);
    }
  }

  private void filterBySearchTerms(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.search == null) {
      return;
    }
    for (String searchTerm : this.search) {
      filterBySearchTerm(filteredRelays, filteredBridges, searchTerm);
    }
  }

  private void filterBySearchTerm(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges, String searchTerm) {
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
      filteredRelays.remove(fingerprint);
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
      filteredBridges.remove(fingerprint);
    }
  }

  private void filterByFingerprint(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.lookup == null) {
      return;
    }
    String fingerprint = this.lookup;
    String relayLine = filteredRelays.get(fingerprint);
    filteredRelays.clear();
    if (relayLine != null) {
      filteredRelays.put(fingerprint, relayLine);
    }
    String bridgeLine = filteredBridges.get(fingerprint);
    filteredBridges.clear();
    if (bridgeLine != null) {
      filteredBridges.put(fingerprint, bridgeLine);
    }
  }

  private void filterByCountryCode(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.country == null) {
      return;
    }
    String countryCode = this.country.toLowerCase();
    if (!relaysByCountryCode.containsKey(countryCode)) {
      filteredRelays.clear();
    } else {
      Set<String> relaysWithCountryCode =
          relaysByCountryCode.get(countryCode);
      Set<String> removeRelays = new HashSet<String>();
      for (Map.Entry<String, String> e : filteredRelays.entrySet()) {
        String fingerprint = e.getKey();
        if (!relaysWithCountryCode.contains(fingerprint)) {
          removeRelays.add(fingerprint);
        }
      }
      for (String fingerprint : removeRelays) {
        filteredRelays.remove(fingerprint);
      }
    }
    filteredBridges.clear();
  }

  private void filterByASNumber(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.as == null) {
      return;
    }
    String aSNumber = this.as.toUpperCase();
    if (!aSNumber.startsWith("AS")) {
      aSNumber = "AS" + aSNumber;
    }
    if (!relaysByASNumber.containsKey(aSNumber)) {
      filteredRelays.clear();
    } else {
      Set<String> relaysWithASNumber =
          relaysByASNumber.get(aSNumber);
      Set<String> removeRelays = new HashSet<String>();
      for (Map.Entry<String, String> e : filteredRelays.entrySet()) {
        String fingerprint = e.getKey();
        if (!relaysWithASNumber.contains(fingerprint)) {
          removeRelays.add(fingerprint);
        }
      }
      for (String fingerprint : removeRelays) {
        filteredRelays.remove(fingerprint);
      }
    }
    filteredBridges.clear();
  }

  private void filterByFlag(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.flag == null) {
      return;
    }
    String flag = this.flag.toLowerCase();
    if (!relaysByFlag.containsKey(flag)) {
      filteredRelays.clear();
    } else {
      Set<String> relaysWithFlag = relaysByFlag.get(flag);
      Set<String> removeRelays = new HashSet<String>();
      for (Map.Entry<String, String> e : filteredRelays.entrySet()) {
        String fingerprint = e.getKey();
        if (!relaysWithFlag.contains(fingerprint)) {
          removeRelays.add(fingerprint);
        }
      }
      for (String fingerprint : removeRelays) {
        filteredRelays.remove(fingerprint);
      }
    }
    filteredBridges.clear();
  }

  private void filterNodesByFirstSeenDays(
      Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.firstSeenDays == null) {
      return;
    }
    filterNodesByDays(filteredRelays, relaysByFirstSeenDays,
        this.firstSeenDays);
    filterNodesByDays(filteredBridges, bridgesByFirstSeenDays,
        this.firstSeenDays);
  }

  private void filterNodesByLastSeenDays(
      Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
    if (this.lastSeenDays == null) {
      return;
    }
    filterNodesByDays(filteredRelays, relaysByLastSeenDays,
        this.lastSeenDays);
    filterNodesByDays(filteredBridges, bridgesByLastSeenDays,
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

  private void filterByContact(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges) {
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
      filteredRelays.remove(fingerprint);
    }
    filteredBridges.clear();
  }

  private void order(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges, List<String> orderedRelays,
      List<String> orderedBridges) {
    if (this.order != null && this.order.length == 1) {
      List<String> orderBy = new ArrayList<String>(
          relaysByConsensusWeight);
      if (this.order[0].startsWith("-")) {
        Collections.reverse(orderBy);
      }
      for (String relay : orderBy) {
        if (filteredRelays.containsKey(relay) &&
            !orderedRelays.contains(filteredRelays.get(relay))) {
          orderedRelays.add(filteredRelays.remove(relay));
        }
      }
      for (String relay : filteredRelays.keySet()) {
        if (!orderedRelays.contains(filteredRelays.get(relay))) {
          orderedRelays.add(filteredRelays.remove(relay));
        }
      }
      Set<String> uniqueBridges = new HashSet<String>(
          filteredBridges.values());
      orderedBridges.addAll(uniqueBridges);
    } else {
      Set<String> uniqueRelays = new HashSet<String>(
          filteredRelays.values());
      orderedRelays.addAll(uniqueRelays);
      Set<String> uniqueBridges = new HashSet<String>(
          filteredBridges.values());
      orderedBridges.addAll(uniqueBridges);
    }
  }

  private void offset(List<String> orderedRelays,
      List<String> orderedBridges) {
    if (offset == null) {
      return;
    }
    int offsetValue = Integer.parseInt(offset);
    while (offsetValue-- > 0 &&
        (!orderedRelays.isEmpty() || !orderedBridges.isEmpty())) {
      if (!orderedRelays.isEmpty()) {
        orderedRelays.remove(0);
      } else {
        orderedBridges.remove(0);
      }
    }
  }

  private void limit(List<String> orderedRelays,
      List<String> orderedBridges) {
    if (limit == null) {
      return;
    }
    int limitValue = Integer.parseInt(limit);
    while (!orderedRelays.isEmpty() &&
        limitValue < orderedRelays.size()) {
      orderedRelays.remove(orderedRelays.size() - 1);
    }
    limitValue -= orderedRelays.size();
    while (!orderedBridges.isEmpty() &&
        limitValue < orderedBridges.size()) {
      orderedBridges.remove(orderedBridges.size() - 1);
    }
  }

  private void writeRelays(List<String> relays, PrintWriter pw) {
    pw.write("{\"relays_published\":\"" + relaysPublishedString
        + "\",\n\"relays\":[");
    int written = 0;
    for (String line : relays) {
      String lines = this.getFromSummaryLine(line);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n],\n");
  }

  private void writeBridges(List<String> bridges, PrintWriter pw) {
    pw.write("\"bridges_published\":\"" + bridgesPublishedString
        + "\",\n\"bridges\":[");
    int written = 0;
    for (String line : bridges) {
      String lines = this.getFromSummaryLine(line);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n]}\n");
  }

  private String getFromSummaryLine(String summaryLine) {
    if (this.resourceType == null) {
      return "";
    } else if (this.resourceType.equals("summary")) {
      return this.writeSummaryLine(summaryLine);
    } else if (this.resourceType.equals("details")) {
      return this.writeDetailsLines(summaryLine);
    } else if (this.resourceType.equals("bandwidth")) {
      return this.writeBandwidthLines(summaryLine);
    } else if (this.resourceType.equals("weights")) {
      return this.writeWeightsLines(summaryLine);
    } else {
      return "";
    }
  }

  private String writeSummaryLine(String summaryLine) {
    return (summaryLine.endsWith(",") ? summaryLine.substring(0,
        summaryLine.length() - 1) : summaryLine);
  }

  private String writeDetailsLines(String summaryLine) {
    String fingerprint = null;
    if (summaryLine.contains("\"f\":\"")) {
      fingerprint = summaryLine.substring(summaryLine.indexOf(
         "\"f\":\"") + "\"f\":\"".length());
    } else if (summaryLine.contains("\"h\":\"")) {
      fingerprint = summaryLine.substring(summaryLine.indexOf(
         "\"h\":\"") + "\"h\":\"".length());
    } else {
      return "";
    }
    fingerprint = fingerprint.substring(0, 40);
    DetailsDocument detailsDocument = documentStore.retrieve(
        DetailsDocument.class, false, fingerprint);
    if (detailsDocument != null &&
        detailsDocument.documentString != null) {
      StringBuilder sb = new StringBuilder();
      Scanner s = new Scanner(detailsDocument.documentString);
      sb.append("{");
      if (s.hasNextLine()) {
        /* Skip version line. */
        s.nextLine();
      }
      boolean includeLine = true;
      while (s.hasNextLine()) {
        String line = s.nextLine();
        if (line.equals("}")) {
          sb.append("}\n");
          break;
        } else if (line.startsWith("\"desc_published\":")) {
          continue;
        } else if (this.fields != null) {
          if (line.startsWith("\"")) {
            includeLine = false;
            for (String field : this.fields) {
              if (line.startsWith("\"" + field + "\":")) {
                sb.append(line + "\n");
                includeLine = true;
              }
            }
          } else if (includeLine) {
            sb.append(line + "\n");
          }
        } else {
          sb.append(line + "\n");
        }
      }
      s.close();
      String detailsLines = sb.toString();
      if (detailsLines.length() > 1) {
        detailsLines = detailsLines.substring(0,
            detailsLines.length() - 1);
      }
      if (detailsLines.endsWith(",\n}")) {
        detailsLines = detailsLines.substring(0,
            detailsLines.length() - 3) + "\n}";
      }
      return detailsLines;
    } else {
      // TODO We should probably log that we didn't find a details
      // document that we expected to exist.
      return "";
    }
  }

  private String writeBandwidthLines(String summaryLine) {
    String fingerprint = null;
    if (summaryLine.contains("\"f\":\"")) {
      fingerprint = summaryLine.substring(summaryLine.indexOf(
         "\"f\":\"") + "\"f\":\"".length());
    } else if (summaryLine.contains("\"h\":\"")) {
      fingerprint = summaryLine.substring(summaryLine.indexOf(
         "\"h\":\"") + "\"h\":\"".length());
    } else {
      return "";
    }
    fingerprint = fingerprint.substring(0, 40);
    BandwidthDocument bandwidthDocument = documentStore.retrieve(
        BandwidthDocument.class, false, fingerprint);
    if (bandwidthDocument != null &&
        bandwidthDocument.documentString != null) {
      String bandwidthLines = bandwidthDocument.documentString;
      bandwidthLines = bandwidthLines.substring(0,
          bandwidthLines.length() - 1);
      return bandwidthLines;
    } else {
      // TODO We should probably log that we didn't find a bandwidth
      // document that we expected to exist.
      return "";
    }
  }

  private String writeWeightsLines(String summaryLine) {
    String fingerprint = null;
    if (summaryLine.contains("\"f\":\"")) {
      fingerprint = summaryLine.substring(summaryLine.indexOf(
         "\"f\":\"") + "\"f\":\"".length());
    } else {
      return "";
    }
    fingerprint = fingerprint.substring(0, 40);
    WeightsDocument weightsDocument = documentStore.retrieve(
        WeightsDocument.class, false, fingerprint);
    if (weightsDocument != null &&
        weightsDocument.documentString != null) {
      String weightsLines = weightsDocument.documentString;
      weightsLines = weightsLines.substring(0, weightsLines.length() - 1);
      return weightsLines;
    } else {
      // TODO We should probably log that we didn't find a weights
      // document that we expected to exist.
      return "";
    }
  }
}
