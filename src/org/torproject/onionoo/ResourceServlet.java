/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

  private static final long serialVersionUID = 7236658979947465319L;

  private boolean maintenanceMode = false;

  private File outDir;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    boolean maintenanceMode =
        config.getInitParameter("maintenance") != null
        && config.getInitParameter("maintenance").equals("1");
    File outDir = new File(config.getInitParameter("outDir"));
    this.init(maintenanceMode, outDir);
  }

  protected void init(boolean maintenanceMode, File outDir) {
    this.maintenanceMode = maintenanceMode;
    this.outDir = outDir;
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
      relaysByASNumber = null, relaysByFlag = null;
  private SortedMap<Integer, Set<String>> relaysByFirstSeenDays = null,
      bridgesByFirstSeenDays = null, relaysByLastSeenDays = null,
      bridgesByLastSeenDays = null;
  private void readSummaryFile() {
    File summaryFile = new File(outDir, "summary");
    if (!summaryFile.exists()) {
      readSummaryFile = false;
      return;
    }
    if (summaryFile.lastModified() > this.summaryFileLastModified) {
      long summaryFileLastModified = summaryFile.lastModified();
      List<String> relaysByConsensusWeight = new ArrayList<String>();
      Map<String, String>
          relayFingerprintSummaryLines = new HashMap<String, String>(),
          bridgeFingerprintSummaryLines = new HashMap<String, String>();
      Map<String, Set<String>>
          relaysByCountryCode = new HashMap<String, Set<String>>(),
          relaysByASNumber = new HashMap<String, Set<String>>(),
          relaysByFlag = new HashMap<String, Set<String>>();
      SortedMap<Integer, Set<String>>
          relaysByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
          bridgesByFirstSeenDays = new TreeMap<Integer, Set<String>>(),
          relaysByLastSeenDays = new TreeMap<Integer, Set<String>>(),
          bridgesByLastSeenDays = new TreeMap<Integer, Set<String>>();
      CurrentNodes cn = new CurrentNodes();
      cn.readRelaySearchDataFile(summaryFile);
      cn.setRelayRunningBits();
      cn.setBridgeRunningBits();
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      this.relaysPublishedString = dateTimeFormat.format(
          cn.getLastValidAfterMillis());
      this.bridgesPublishedString = dateTimeFormat.format(
          cn.getLastPublishedMillis());
      List<String> orderRelaysByConsensusWeight = new ArrayList<String>();
      for (Node entry : cn.getCurrentRelays().values()) {
        String fingerprint = entry.getFingerprint().toUpperCase();
        String hashedFingerprint = entry.getHashedFingerprint().
            toUpperCase();
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
      }
      Collections.sort(orderRelaysByConsensusWeight);
      relaysByConsensusWeight = new ArrayList<String>();
      for (String relay : orderRelaysByConsensusWeight) {
        relaysByConsensusWeight.add(relay.split(" ")[1]);
      }
      for (Node entry : cn.getCurrentBridges().values()) {
        String hashedFingerprint = entry.getFingerprint().toUpperCase();
        String hashedHashedFingerprint = entry.getHashedFingerprint().
            toUpperCase();
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
      this.relaysByFirstSeenDays = relaysByFirstSeenDays;
      this.relaysByLastSeenDays = relaysByLastSeenDays;
      this.bridgesByFirstSeenDays = bridgesByFirstSeenDays;
      this.bridgesByLastSeenDays = bridgesByLastSeenDays;
    }
    this.summaryFileLastModified = summaryFile.lastModified();
    this.readSummaryFile = true;
  }

  private String formatRelaySummaryLine(Node entry) {
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String fingerprint = entry.getFingerprint();
    String running = entry.getRunning() ? "true" : "false";
    SortedSet<String> addresses = new TreeSet<String>();
    addresses.add(entry.getAddress());
    addresses.addAll(entry.getOrAddresses());
    addresses.addAll(entry.getExitAddresses());
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

  private String formatBridgeSummaryLine(Node entry) {
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
        + "last_seen_days,order,limit,offset").split(",")));
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

    /* Set response headers and write the response. */
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json");
    response.setCharacterEncoding("utf-8");
    PrintWriter pw = response.getWriter();
    this.writeRelays(orderedRelays, pw, resourceType);
    this.writeBridges(orderedBridges, pw, resourceType);
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

  private void writeRelays(List<String> relays, PrintWriter pw,
      String resourceType) {
    pw.write("{\"relays_published\":\"" + this.relaysPublishedString
        + "\",\n\"relays\":[");
    int written = 0;
    for (String line : relays) {
      if (line == null) {
        /* TODO This is a workaround for a bug; line shouldn't be null. */
        continue;
      }
      String lines = this.getFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n],\n");
  }

  private void writeBridges(List<String> bridges, PrintWriter pw,
      String resourceType) {
    pw.write("\"bridges_published\":\"" + this.bridgesPublishedString
        + "\",\n\"bridges\":[");
    int written = 0;
    for (String line : bridges) {
      if (line == null) {
        /* TODO This is a workaround for a bug; line shouldn't be null. */
        continue;
      }
      String lines = this.getFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n]}\n");
  }

  private String getFromSummaryLine(String summaryLine,
      String resourceType) {
    if (resourceType.equals("summary")) {
      return this.writeSummaryLine(summaryLine);
    } else if (resourceType.equals("details")) {
      return this.writeDetailsLines(summaryLine);
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
    File detailsFile = new File(this.outDir, "details/" + fingerprint);
    StringBuilder sb = new StringBuilder();
    String detailsLines = null;
    if (detailsFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            detailsFile));
        String line = br.readLine();
        if (line != null) {
          sb.append("{");
          while ((line = br.readLine()) != null) {
            if (!line.startsWith("\"desc_published\":")) {
              sb.append(line + "\n");
            }
          }
        }
        br.close();
        detailsLines = sb.toString();
        if (detailsLines.length() > 1) {
          detailsLines = detailsLines.substring(0,
              detailsLines.length() - 1);
        }
      } catch (IOException e) {
      }
    }
    if (detailsLines != null) {
      return detailsLines;
    } else {
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
    File bandwidthFile = new File(this.outDir, "bandwidth/"
        + fingerprint);
    StringBuilder sb = new StringBuilder();
    String bandwidthLines = null;
    if (bandwidthFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            bandwidthFile));
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line + "\n");
        }
        br.close();
        bandwidthLines = sb.toString();
      } catch (IOException e) {
      }
    }
    if (bandwidthLines != null) {
      bandwidthLines = bandwidthLines.substring(0,
          bandwidthLines.length() - 1);
      return bandwidthLines;
    } else {
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
    File weightsFile = new File(this.outDir, "weights/" + fingerprint);
    StringBuilder sb = new StringBuilder();
    String weightsLines = null;
    if (weightsFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            weightsFile));
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line + "\n");
        }
        br.close();
        weightsLines = sb.toString();
      } catch (IOException e) {
      }
    }
    if (weightsLines != null) {
      weightsLines = weightsLines.substring(0, weightsLines.length() - 1);
      return weightsLines;
    } else {
      return "";
    }
  }
}

