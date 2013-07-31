/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

  private static final long serialVersionUID = 7236658979947465319L;

  private boolean maintenanceMode = false;

  private DocumentStore documentStore;

  private boolean checkSummaryStale = false;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    boolean maintenanceMode =
        config.getInitParameter("maintenance") != null
        && config.getInitParameter("maintenance").equals("1");
    File outDir = new File(config.getInitParameter("outDir"));
    this.checkSummaryStale = true;
    this.init(maintenanceMode, outDir);
  }

  protected void init(boolean maintenanceMode, File outDir) {
    this.maintenanceMode = maintenanceMode;
    this.documentStore = new DocumentStore(outDir);
    if (!maintenanceMode) {
      this.readSummaryFile();
    }
  }

  long summaryFileLastModified = -1L;
  boolean readSummaryFile = false;
  private String relaysPublishedString, bridgesPublishedString;
  private List<String> relaysByConsensusWeight = null;
  private Map<String, String> relayFingerprintSummaryLines = null,
      bridgeFingerprintSummaryLines = null;
  private Map<String, Set<String>> relaysByCountryCode = null,
      relaysByASNumber = null, relaysByFlag = null,
      relaysByContact = null;
  private SortedMap<Integer, Set<String>> relaysByFirstSeenDays = null,
      bridgesByFirstSeenDays = null, relaysByLastSeenDays = null,
      bridgesByLastSeenDays = null;
  private static final long SUMMARY_MAX_AGE = 6L * 60L * 60L * 1000L;
  private void readSummaryFile() {
    long summaryFileLastModified = -1L;
    UpdateStatus updateStatus = this.documentStore.retrieve(
        UpdateStatus.class, false);
    if (updateStatus != null && updateStatus.documentString != null) {
      String updateString = updateStatus.documentString;
      try {
        summaryFileLastModified = Long.parseLong(updateString.trim());
      } catch (NumberFormatException e) {
        /* Handle below. */
      }
    }
    if (summaryFileLastModified < 0L) {
      // TODO Does this actually solve anything?  Should we instead
      // switch to a variant of the maintenance mode and re-check when
      // the next requests comes in that happens x seconds after this one?
      this.readSummaryFile = false;
      return;
    }
    if (this.checkSummaryStale &&
        summaryFileLastModified + SUMMARY_MAX_AGE
        < System.currentTimeMillis()) {
      // TODO Does this actually solve anything?  Should we instead
      // switch to a variant of the maintenance mode and re-check when
      // the next requests comes in that happens x seconds after this one?
      this.readSummaryFile = false;
      return;
    }
    if (summaryFileLastModified > this.summaryFileLastModified) {
      List<String> relaysByConsensusWeight = new ArrayList<String>();
      Map<String, String>
          relayFingerprintSummaryLines = new HashMap<String, String>(),
          bridgeFingerprintSummaryLines = new HashMap<String, String>();
      Map<String, Set<String>>
          relaysByCountryCode = new HashMap<String, Set<String>>(),
          relaysByASNumber = new HashMap<String, Set<String>>(),
          relaysByFlag = new HashMap<String, Set<String>>(),
          relaysByContact = new HashMap<String, Set<String>>();
      SortedMap<Integer, Set<String>>
          relaysByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
          bridgesByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
          relaysByLastSeenDays = new TreeMap<Integer, Set<String>>(),
          bridgesByLastSeenDays = new TreeMap<Integer, Set<String>>();
      long relaysLastValidAfterMillis = -1L,
          bridgesLastPublishedMillis = -1L;
      Set<NodeStatus> currentRelays = new HashSet<NodeStatus>(),
          currentBridges = new HashSet<NodeStatus>();
      SortedSet<String> fingerprints = this.documentStore.list(
          NodeStatus.class, false);
      // TODO We should be able to learn if something goes wrong when
      // reading the summary file, rather than silently having an empty
      // list of fingerprints.
      for (String fingerprint : fingerprints) {
        NodeStatus node = this.documentStore.retrieve(NodeStatus.class,
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
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      this.relaysPublishedString = dateTimeFormat.format(
          relaysLastValidAfterMillis);
      this.bridgesPublishedString = dateTimeFormat.format(
          bridgesLastPublishedMillis);
      List<String> orderRelaysByConsensusWeight = new ArrayList<String>();
      for (NodeStatus entry : currentRelays) {
        String fingerprint = entry.getFingerprint().toUpperCase();
        String hashedFingerprint = entry.getHashedFingerprint().
            toUpperCase();
        entry.setRunning(entry.getLastSeenMillis() ==
            relaysLastValidAfterMillis);
        String line = this.formatRelaySummaryLine(entry);
        relayFingerprintSummaryLines.put(fingerprint, line);
        relayFingerprintSummaryLines.put(hashedFingerprint, line);
        long consensusWeight = entry.getConsensusWeight();
        orderRelaysByConsensusWeight.add(String.format("%020d %s",
            consensusWeight, fingerprint));
        orderRelaysByConsensusWeight.add(String.format("%020d %s",
            consensusWeight, hashedFingerprint));
        if (entry.getCountryCode() != null) {
          String countryCode = entry.getCountryCode();
          if (!relaysByCountryCode.containsKey(countryCode)) {
            relaysByCountryCode.put(countryCode, new HashSet<String>());
          }
          relaysByCountryCode.get(countryCode).add(fingerprint);
          relaysByCountryCode.get(countryCode).add(hashedFingerprint);
        }
        if (entry.getASNumber() != null) {
          String aSNumber = entry.getASNumber();
          if (!relaysByASNumber.containsKey(aSNumber)) {
            relaysByASNumber.put(aSNumber, new HashSet<String>());
          }
          relaysByASNumber.get(aSNumber).add(fingerprint);
          relaysByASNumber.get(aSNumber).add(hashedFingerprint);
        }
        for (String flag : entry.getRelayFlags()) {
          String flagLowerCase = flag.toLowerCase();
          if (!relaysByFlag.containsKey(flagLowerCase)) {
            relaysByFlag.put(flagLowerCase, new HashSet<String>());
          }
          relaysByFlag.get(flagLowerCase).add(fingerprint);
          relaysByFlag.get(flagLowerCase).add(hashedFingerprint);
        }
        int daysSinceFirstSeen = (int) ((summaryFileLastModified
            - entry.getFirstSeenMillis()) / 86400000L);
        if (!relaysByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
          relaysByFirstSeenDays.put(daysSinceFirstSeen,
              new HashSet<String>());
        }
        relaysByFirstSeenDays.get(daysSinceFirstSeen).add(fingerprint);
        relaysByFirstSeenDays.get(daysSinceFirstSeen).add(
            hashedFingerprint);
        int daysSinceLastSeen = (int) ((summaryFileLastModified
            - entry.getLastSeenMillis()) / 86400000L);
        if (!relaysByLastSeenDays.containsKey(daysSinceLastSeen)) {
          relaysByLastSeenDays.put(daysSinceLastSeen,
              new HashSet<String>());
        }
        relaysByLastSeenDays.get(daysSinceLastSeen).add(fingerprint);
        relaysByLastSeenDays.get(daysSinceLastSeen).add(
            hashedFingerprint);
        String contact = entry.getContact();
        if (!relaysByContact.containsKey(contact)) {
          relaysByContact.put(contact, new HashSet<String>());
        }
        relaysByContact.get(contact).add(fingerprint);
        relaysByContact.get(contact).add(hashedFingerprint);
      }
      Collections.sort(orderRelaysByConsensusWeight);
      relaysByConsensusWeight = new ArrayList<String>();
      for (String relay : orderRelaysByConsensusWeight) {
        relaysByConsensusWeight.add(relay.split(" ")[1]);
      }
      for (NodeStatus entry : currentBridges) {
        String hashedFingerprint = entry.getFingerprint().toUpperCase();
        String hashedHashedFingerprint = entry.getHashedFingerprint().
            toUpperCase();
        entry.setRunning(entry.getRelayFlags().contains("Running") &&
            entry.getLastSeenMillis() == bridgesLastPublishedMillis);
        String line = this.formatBridgeSummaryLine(entry);
        bridgeFingerprintSummaryLines.put(hashedFingerprint, line);
        bridgeFingerprintSummaryLines.put(hashedHashedFingerprint, line);
        int daysSinceFirstSeen = (int) ((summaryFileLastModified
            - entry.getFirstSeenMillis()) / 86400000L);
        if (!bridgesByFirstSeenDays.containsKey(daysSinceFirstSeen)) {
          bridgesByFirstSeenDays.put(daysSinceFirstSeen,
              new HashSet<String>());
        }
        bridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
            hashedFingerprint);
        bridgesByFirstSeenDays.get(daysSinceFirstSeen).add(
            hashedHashedFingerprint);
        int daysSinceLastSeen = (int) ((summaryFileLastModified
            - entry.getLastSeenMillis()) / 86400000L);
        if (!bridgesByLastSeenDays.containsKey(daysSinceLastSeen)) {
          bridgesByLastSeenDays.put(daysSinceLastSeen,
              new HashSet<String>());
        }
        bridgesByLastSeenDays.get(daysSinceLastSeen).add(
            hashedFingerprint);
        bridgesByLastSeenDays.get(daysSinceLastSeen).add(
            hashedHashedFingerprint);
      }
      this.relaysByConsensusWeight = relaysByConsensusWeight;
      this.relayFingerprintSummaryLines = relayFingerprintSummaryLines;
      this.bridgeFingerprintSummaryLines = bridgeFingerprintSummaryLines;
      this.relaysByCountryCode = relaysByCountryCode;
      this.relaysByASNumber = relaysByASNumber;
      this.relaysByFlag = relaysByFlag;
      this.relaysByContact = relaysByContact;
      this.relaysByFirstSeenDays = relaysByFirstSeenDays;
      this.relaysByLastSeenDays = relaysByLastSeenDays;
      this.bridgesByFirstSeenDays = bridgesByFirstSeenDays;
      this.bridgesByLastSeenDays = bridgesByLastSeenDays;
    }
    this.summaryFileLastModified = summaryFileLastModified;
    this.readSummaryFile = true;
  }

  private String formatRelaySummaryLine(NodeStatus entry) {
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

  private String formatBridgeSummaryLine(NodeStatus entry) {
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String hashedFingerprint = entry.getFingerprint();
    String running = entry.getRunning() ? "true" : "false";
    return String.format("{%s\"h\":\"%s\",\"r\":%s}",
         (nickname == null ? "" : "\"n\":\"" + nickname + "\","),
         hashedFingerprint, running);
  }

  public long getLastModified(HttpServletRequest request) {
    if (this.maintenanceMode) {
      return super.getLastModified(request);
    } else {
      return this.getLastModified();
    }
  }

  private long getLastModified() {
    this.readSummaryFile();
    return this.summaryFileLastModified;
  }

  protected static class HttpServletRequestWrapper {
    private HttpServletRequest request;
    protected HttpServletRequestWrapper(HttpServletRequest request) {
      this.request = request;
    }
    protected String getRequestURI() {
      return this.request.getRequestURI();
    }
    protected Map getParameterMap() {
      return this.request.getParameterMap();
    }
    protected String[] getParameterValues(String parameterKey) {
      return this.request.getParameterValues(parameterKey);
    }
  }

  protected static class HttpServletResponseWrapper {
    private HttpServletResponse response = null;
    protected HttpServletResponseWrapper(HttpServletResponse response) {
      this.response = response;
    }
    protected void sendError(int errorStatusCode) throws IOException {
      this.response.sendError(errorStatusCode);
    }
    protected void setHeader(String headerName, String headerValue) {
      this.response.setHeader(headerName, headerValue);
    }
    protected void setContentType(String contentType) {
      this.response.setContentType(contentType);
    }
    protected void setCharacterEncoding(String characterEncoding) {
      this.response.setCharacterEncoding(characterEncoding);
    }
    protected PrintWriter getWriter() throws IOException {
      return this.response.getWriter();
    }
  }

  public void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
    HttpServletRequestWrapper requestWrapper =
        new HttpServletRequestWrapper(request);
    HttpServletResponseWrapper responseWrapper =
        new HttpServletResponseWrapper(response);
    this.doGet(requestWrapper, responseWrapper);
  }

  public void doGet(HttpServletRequestWrapper request,
      HttpServletResponseWrapper response) throws IOException {

    if (this.maintenanceMode) {
      response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      return;
    }

    this.readSummaryFile();
    if (!this.readSummaryFile) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String uri = request.getRequestURI();
    if (uri.startsWith("/onionoo/")) {
      uri = uri.substring("/onionoo".length());
    }
    String resourceType = null;
    if (uri.startsWith("/summary")) {
      resourceType = "summary";
    } else if (uri.startsWith("/details")) {
      resourceType = "details";
    } else if (uri.startsWith("/bandwidth")) {
      resourceType = "bandwidth";
    } else if (uri.startsWith("/weights")) {
      resourceType = "weights";
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    /* Extract parameters either from the old-style URI or from request
     * parameters. */
    Map<String, String> parameterMap = new HashMap<String, String>();
    for (Object parameterKey : request.getParameterMap().keySet()) {
      String[] parameterValues =
          request.getParameterValues((String) parameterKey);
      parameterMap.put((String) parameterKey, parameterValues[0]);
    }

    /* Make sure that the request doesn't contain any unknown
     * parameters. */
    Set<String> knownParameters = new HashSet<String>(Arrays.asList((
        "type,running,search,lookup,country,as,flag,first_seen_days,"
        + "last_seen_days,contact,order,limit,offset,fields").
        split(",")));
    for (String parameterKey : parameterMap.keySet()) {
      if (!knownParameters.contains(parameterKey)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }

    /* Filter relays and bridges matching the request. */
    Map<String, String> filteredRelays = new HashMap<String, String>(
        this.relayFingerprintSummaryLines);
    Map<String, String> filteredBridges = new HashMap<String, String>(
        this.bridgeFingerprintSummaryLines);
    if (parameterMap.containsKey("type")) {
      String typeParameterValue = parameterMap.get("type").toLowerCase();
      boolean relaysRequested = true;
      if (typeParameterValue.equals("bridge")) {
        relaysRequested = false;
      } else if (!typeParameterValue.equals("relay")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterByType(filteredRelays, filteredBridges, relaysRequested);
    }
    if (parameterMap.containsKey("running")) {
      String runningParameterValue =
          parameterMap.get("running").toLowerCase();
      boolean runningRequested = true;
      if (runningParameterValue.equals("false")) {
        runningRequested = false;
      } else if (!runningParameterValue.equals("true")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterByRunning(filteredRelays, filteredBridges,
          runningRequested);
    }
    if (parameterMap.containsKey("search")) {
      String[] searchTerms = this.parseSearchParameters(
          parameterMap.get("search"));
      if (searchTerms == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterBySearchTerms(filteredRelays, filteredBridges,
          searchTerms);
    }
    if (parameterMap.containsKey("lookup")) {
      String fingerprintParameter = this.parseFingerprintParameter(
          parameterMap.get("lookup"));
      if (fingerprintParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      String fingerprint = fingerprintParameter.toUpperCase();
      this.filterByFingerprint(filteredRelays, filteredBridges,
          fingerprint);
    }
    if (parameterMap.containsKey("country")) {
      String countryCodeParameter = this.parseCountryCodeParameter(
          parameterMap.get("country"));
      if (countryCodeParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterByCountryCode(filteredRelays, filteredBridges,
          countryCodeParameter);
    }
    if (parameterMap.containsKey("as")) {
      String aSNumberParameter = this.parseASNumberParameter(
          parameterMap.get("as"));
      if (aSNumberParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterByASNumber(filteredRelays, filteredBridges,
          aSNumberParameter);
    }
    if (parameterMap.containsKey("flag")) {
      String flagParameter = this.parseFlagParameter(
          parameterMap.get("flag"));
      if (flagParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterByFlag(filteredRelays, filteredBridges, flagParameter);
    }
    if (parameterMap.containsKey("first_seen_days")) {
      int[] days = this.parseDaysParameter(
          parameterMap.get("first_seen_days"));
      if (days == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterNodesByDays(filteredRelays, this.relaysByFirstSeenDays,
          days);
      this.filterNodesByDays(filteredBridges, this.bridgesByFirstSeenDays,
          days);
    }
    if (parameterMap.containsKey("last_seen_days")) {
      int[] days = this.parseDaysParameter(
          parameterMap.get("last_seen_days"));
      if (days == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterNodesByDays(filteredRelays, this.relaysByLastSeenDays,
          days);
      this.filterNodesByDays(filteredBridges, this.bridgesByLastSeenDays,
          days);
    }
    if (parameterMap.containsKey("contact")) {
      String[] contactParts = this.parseContactParameter(
          parameterMap.get("contact"));
      if (contactParts == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterByContact(filteredRelays, filteredBridges, contactParts);
    }

    /* Re-order and limit results. */
    List<String> orderedRelays = new ArrayList<String>();
    List<String> orderedBridges = new ArrayList<String>();
    if (parameterMap.containsKey("order")) {
      String orderParameter = parameterMap.get("order").toLowerCase();
      boolean descending = false;
      if (orderParameter.startsWith("-")) {
        descending = true;
        orderParameter = orderParameter.substring(1);
      }
      if (!orderParameter.equals("consensus_weight")) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      List<String> orderBy = new ArrayList<String>(
          this.relaysByConsensusWeight);
      if (descending) {
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
    if (parameterMap.containsKey("offset")) {
      String offsetParameter = parameterMap.get("offset");
      if (offsetParameter.length() > 6) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      int offset = 0;
      try {
        offset = Integer.parseInt(offsetParameter);
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      while (offset-- > 0 &&
          (!orderedRelays.isEmpty() || !orderedBridges.isEmpty())) {
        if (!orderedRelays.isEmpty()) {
          orderedRelays.remove(0);
        } else {
          orderedBridges.remove(0);
        }
      }
    }
    if (parameterMap.containsKey("limit")) {
      String limitParameter = parameterMap.get("limit");
      if (limitParameter.length() > 6) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      int limit = -1;
      try {
        limit = Integer.parseInt(limitParameter);
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      while (!orderedRelays.isEmpty() && limit < orderedRelays.size()) {
        orderedRelays.remove(orderedRelays.size() - 1);
      }
      limit -= orderedRelays.size();
      while (!orderedBridges.isEmpty() && limit < orderedBridges.size()) {
        orderedBridges.remove(orderedBridges.size() - 1);
      }
    }

    /* Possibly include only a subset of fields in the response
     * document. */
    String[] fields = null;
    if (parameterMap.containsKey("fields")) {
      fields = this.parseFieldsParameter(parameterMap.get("fields"));
      if (fields == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }

    /* Set response headers and write the response. */
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json");
    response.setCharacterEncoding("utf-8");
    PrintWriter pw = response.getWriter();
    this.writeRelays(orderedRelays, pw, resourceType, fields);
    this.writeBridges(orderedBridges, pw, resourceType, fields);
    pw.flush();
    pw.close();
  }

  private static Pattern searchParameterPattern =
      Pattern.compile("^\\$?[0-9a-fA-F]{1,40}$|" /* Fingerprint. */
      + "^[0-9a-zA-Z\\.]{1,19}$|" /* Nickname or IPv4 address. */
      + "^\\[[0-9a-fA-F:\\.]{1,39}\\]?$"); /* IPv6 address. */
  private String[] parseSearchParameters(String parameter) {
    String[] searchParameters;
    if (parameter.contains(" ")) {
      searchParameters = parameter.split(" ");
    } else {
      searchParameters = new String[] { parameter };
    }
    for (String searchParameter : searchParameters) {
      if (!searchParameterPattern.matcher(searchParameter).matches()) {
        return null;
      }
    }
    return searchParameters;
  }

  private static Pattern fingerprintParameterPattern =
      Pattern.compile("^[0-9a-zA-Z]{1,40}$");
  private String parseFingerprintParameter(String parameter) {
    if (!fingerprintParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    if (parameter.length() != 40) {
      return null;
    }
    return parameter;
  }

  private static Pattern countryCodeParameterPattern =
      Pattern.compile("^[0-9a-zA-Z]{2}$");
  private String parseCountryCodeParameter(String parameter) {
    if (!countryCodeParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern aSNumberParameterPattern =
      Pattern.compile("^[asAS]{0,2}[0-9]{1,10}$");
  private String parseASNumberParameter(String parameter) {
    if (!aSNumberParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern flagPattern =
      Pattern.compile("^[a-zA-Z0-9]{1,20}$");
  private String parseFlagParameter(String parameter) {
    if (!flagPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern daysPattern = Pattern.compile("^[0-9-]{1,10}$");
  private int[] parseDaysParameter(String parameter) {
    if (!daysPattern.matcher(parameter).matches()) {
      return null;
    }
    int x = 0, y = Integer.MAX_VALUE;
    try {
      if (!parameter.contains("-")) {
        x = y = Integer.parseInt(parameter);
      } else {
        String[] parts = parameter.split("-", 2);
        if (parts[0].length() > 0) {
          x = Integer.parseInt(parts[0]);
        }
        if (parts.length > 1 && parts[1].length() > 0) {
          y = Integer.parseInt(parts[1]);
        }
      }
    } catch (NumberFormatException e) {
      return null;
    }
    if (x > y) {
      return null;
    }
    return new int[] { x, y };
  }

  private String[] parseContactParameter(String parameter) {
    for (char c : parameter.toCharArray()) {
      if (c < 32 || c >= 127) {
        return null;
      }
    }
    return parameter.split(" ");
  }

  private static Pattern fieldsParameterPattern =
      Pattern.compile("^[0-9a-zA-Z_,]*$");
  private String[] parseFieldsParameter(String parameter) {
    if (!fieldsParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter.split(",");
  }

  private void filterByType(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges, boolean relaysRequested) {
    if (relaysRequested) {
      filteredBridges.clear();
    } else {
      filteredRelays.clear();
    }
  }

  private void filterByRunning(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges, boolean runningRequested) {
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
      Map<String, String> filteredBridges, String[] searchTerms) {
    for (String searchTerm : searchTerms) {
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
      Map<String, String> filteredBridges, String fingerprint) {
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
      Map<String, String> filteredBridges, String countryCodeParameter) {
    String countryCode = countryCodeParameter.toLowerCase();
    if (!this.relaysByCountryCode.containsKey(countryCode)) {
      filteredRelays.clear();
    } else {
      Set<String> relaysWithCountryCode =
          this.relaysByCountryCode.get(countryCode);
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
      Map<String, String> filteredBridges, String aSNumberParameter) {
    String aSNumber = aSNumberParameter.toUpperCase();
    if (!aSNumber.startsWith("AS")) {
      aSNumber = "AS" + aSNumber;
    }
    if (!this.relaysByASNumber.containsKey(aSNumber)) {
      filteredRelays.clear();
    } else {
      Set<String> relaysWithASNumber =
          this.relaysByASNumber.get(aSNumber);
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
      Map<String, String> filteredBridges, String flagParameter) {
    String flag = flagParameter.toLowerCase();
    if (!this.relaysByFlag.containsKey(flag)) {
      filteredRelays.clear();
    } else {
      Set<String> relaysWithFlag = this.relaysByFlag.get(flag);
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
      Map<String, String> filteredBridges, String[] contactParts) {
    Set<String> removeRelays = new HashSet<String>();
    for (Map.Entry<String, Set<String>> e :
        this.relaysByContact.entrySet()) {
      String contact = e.getKey();
      for (String contactPart : contactParts) {
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

  private void writeRelays(List<String> relays, PrintWriter pw,
      String resourceType, String[] fields) {
    pw.write("{\"relays_published\":\"" + this.relaysPublishedString
        + "\",\n\"relays\":[");
    int written = 0;
    for (String line : relays) {
      if (line == null) {
        /* TODO This is a workaround for a bug; line shouldn't be null. */
        continue;
      }
      String lines = this.getFromSummaryLine(line, resourceType, fields);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n],\n");
  }

  private void writeBridges(List<String> bridges, PrintWriter pw,
      String resourceType, String[] fields) {
    pw.write("\"bridges_published\":\"" + this.bridgesPublishedString
        + "\",\n\"bridges\":[");
    int written = 0;
    for (String line : bridges) {
      if (line == null) {
        /* TODO This is a workaround for a bug; line shouldn't be null. */
        continue;
      }
      String lines = this.getFromSummaryLine(line, resourceType, fields);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n]}\n");
  }

  private String getFromSummaryLine(String summaryLine,
      String resourceType, String[] fields) {
    if (resourceType.equals("summary")) {
      return this.writeSummaryLine(summaryLine);
    } else if (resourceType.equals("details")) {
      return this.writeDetailsLines(summaryLine, fields);
    } else if (resourceType.equals("bandwidth")) {
      return this.writeBandwidthLines(summaryLine);
    } else if (resourceType.equals("weights")) {
      return this.writeWeightsLines(summaryLine);
    } else {
      return "";
    }
  }

  private String writeSummaryLine(String summaryLine) {
    return (summaryLine.endsWith(",") ? summaryLine.substring(0,
        summaryLine.length() - 1) : summaryLine);
  }

  private String writeDetailsLines(String summaryLine, String[] fields) {
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
    DetailsDocument detailsDocument = this.documentStore.retrieve(
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
        } else if (fields != null) {
          if (line.startsWith("\"")) {
            includeLine = false;
            for (String field : fields) {
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
    BandwidthDocument bandwidthDocument = this.documentStore.retrieve(
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
    WeightsDocument weightsDocument = this.documentStore.retrieve(
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

