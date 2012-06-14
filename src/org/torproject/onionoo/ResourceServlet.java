/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

public class ResourceServlet extends HttpServlet {

  private static final long serialVersionUID = 7236658979947465319L;

  private String outDirString;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    this.outDirString = config.getInitParameter("outDir");
    this.readSummaryFile();
  }

  long summaryFileLastModified = -1L;
  boolean readSummaryFile = false;
  private String relaysPublishedLine = null, bridgesPublishedLine = null;
  private List<String> relayLines = new ArrayList<String>(),
      bridgeLines = new ArrayList<String>(),
      relaysByConsensusWeight = new ArrayList<String>();
  private Map<String, String>
      relayFingerprintSummaryLines = new HashMap<String, String>(),
      bridgeFingerprintSummaryLines = new HashMap<String, String>();
  private void readSummaryFile() {
    File summaryFile = new File(this.outDirString + "summary.json");
    if (!summaryFile.exists()) {
      readSummaryFile = false;
      return;
    }
    if (summaryFile.lastModified() > this.summaryFileLastModified) {
      this.relayLines.clear();
      this.bridgeLines.clear();
      this.relayFingerprintSummaryLines.clear();
      this.bridgeFingerprintSummaryLines.clear();
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            summaryFile));
        String line;
        while ((line = br.readLine()) != null) {
          if (line.contains("\"relays_published\":")) {
            this.relaysPublishedLine = line.startsWith("{") ? line :
                "{" + line;
          } else if (line.startsWith("\"bridges_published\":")) {
            this.bridgesPublishedLine = line;
          } else if (line.startsWith("\"relays\":")) {
            while ((line = br.readLine()) != null && !line.equals("],")) {
              this.relayLines.add(line);
              int fingerprintStart = line.indexOf("\"f\":\"");
              if (fingerprintStart > 0) {
                fingerprintStart += "\"f\":\"".length();
                String fingerprint = line.substring(fingerprintStart,
                    fingerprintStart + 40);
                String hashedFingerprint = DigestUtils.shaHex(
                    Hex.decodeHex(fingerprint.toCharArray())).
                    toUpperCase();
                this.relayFingerprintSummaryLines.put(fingerprint, line);
                this.relayFingerprintSummaryLines.put(hashedFingerprint,
                    line);
              }
            }
          } else if (line.startsWith("\"bridges\":")) {
            while ((line = br.readLine()) != null && !line.equals("]}")) {
              this.bridgeLines.add(line);
              int hashedFingerprintStart = line.indexOf("\"h\":\"");
              if (hashedFingerprintStart > 0) {
                hashedFingerprintStart += "\"h\":\"".length();
                String hashedFingerprint = line.substring(
                    hashedFingerprintStart, hashedFingerprintStart + 40);
                String hashedHashedFingerprint = DigestUtils.shaHex(
                    Hex.decodeHex(hashedFingerprint.toCharArray())).
                    toUpperCase();
                this.bridgeFingerprintSummaryLines.put(hashedFingerprint,
                    line);
                this.bridgeFingerprintSummaryLines.put(
                    hashedHashedFingerprint, line);
              }
            }
          }
        }
        br.close();
      } catch (IOException e) {
        return;
      } catch (DecoderException e) {
        return;
      }
      List<String> orderRelaysByConsensusWeight = new ArrayList<String>();
      File relaysByConsensusWeightFile =
          new File(this.outDirString + "/relays-by-consensus-weight.csv");
      if (relaysByConsensusWeightFile.exists()) {
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              relaysByConsensusWeightFile));
          String line;
          while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length != 2) {
              return;
            }
            long consensusWeight = Long.parseLong(parts[1]);
            String fingerprint = parts[0];
            orderRelaysByConsensusWeight.add(
                String.format("%020d %s", consensusWeight, fingerprint));
            String hashedFingerprint = DigestUtils.shaHex(
                Hex.decodeHex(fingerprint.toCharArray())).
                toUpperCase();
            orderRelaysByConsensusWeight.add(
                String.format("%020d %s", consensusWeight,
                hashedFingerprint));
          }
          br.close();
          Collections.sort(orderRelaysByConsensusWeight);
          this.relaysByConsensusWeight = new ArrayList<String>();
          for (String relay : orderRelaysByConsensusWeight) {
            this.relaysByConsensusWeight.add(relay.split(" ")[1]);
          }
        } catch (IOException e) {
          return;
        } catch (NumberFormatException e) {
          return;
        } catch (DecoderException e) {
          return;
        }
      }
    }
    this.summaryFileLastModified = summaryFile.lastModified();
    this.readSummaryFile = true;
  }

  public long getLastModified(HttpServletRequest request) {
    this.readSummaryFile();
    return this.summaryFileLastModified;
  }

  public void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {

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
    boolean isOldStyleUri = false;
    if (uri.startsWith("/summary/")) {
      resourceType = "summary";
      isOldStyleUri = true;
    } else if (uri.startsWith("/details/")) {
      resourceType = "details";
      isOldStyleUri = true;
    } else if (uri.startsWith("/bandwidth/")) {
      resourceType = "bandwidth";
      isOldStyleUri = true;
    } else if (uri.startsWith("/summary")) {
      resourceType = "summary";
    } else if (uri.startsWith("/details")) {
      resourceType = "details";
    } else if (uri.startsWith("/bandwidth")) {
      resourceType = "bandwidth";
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    /* Extract parameters either from the old-style URI or from request
     * parameters. */
    Map<String, String> parameterMap;
    if (isOldStyleUri) {
      parameterMap = this.getParameterMapForOldStyleUri(uri,
          resourceType);
      if (parameterMap == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    } else {
      parameterMap = new HashMap<String, String>();
      for (Object parameterKey : request.getParameterMap().keySet()) {
        String[] parameterValues =
            request.getParameterValues((String) parameterKey);
        parameterMap.put((String) parameterKey, parameterValues[0]);
      }
    }

    /* Make sure that the request doesn't contain any unknown
     * parameters. */
    Set<String> knownParameters = new HashSet<String>(Arrays.asList(
        "type,running,search,lookup,order,limit,offset".split(",")));
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
      String typeParameterValue = parameterMap.get("type");
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
      String runningParameterValue = parameterMap.get("running");
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
      String searchTerm = this.parseSearchParameter(
          parameterMap.get("search"));
      if (searchTerm == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.filterBySearchTerm(filteredRelays, filteredBridges,
          searchTerm);
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

    /* Re-order and limit results. */
    List<String> orderedRelays = new ArrayList<String>();
    List<String> orderedBridges = new ArrayList<String>();
    if (parameterMap.containsKey("order")) {
      String orderParameter = parameterMap.get("order");
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
      for (String relay : filteredRelays.values()) {
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
      if (limit >= 0) {
        while (limit < orderedRelays.size()) {
          orderedRelays.remove(orderedRelays.size() - 1);
        }
      }
      limit -= orderedRelays.size();
      if (limit >= 0) {
        while (limit < orderedBridges.size()) {
          orderedBridges.remove(orderedBridges.size() - 1);
        }
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

  private Map<String, String> getParameterMapForOldStyleUri(String uri,
      String resourceType) {
    Map<String, String> result = new HashMap<String, String>();
    if (uri.equals("/" + resourceType + "/all")) {
    } else if (uri.equals("/" + resourceType + "/running")) {
      result.put("running", "true");
    } else if (uri.equals("/" + resourceType + "/relays")) {
      result.put("type", "relays");
    } else if (uri.equals("/" + resourceType + "/bridges")) {
      result.put("type", "bridges");
    } else if (uri.startsWith("/" + resourceType + "/search/")) {
      String searchParameter = this.parseSearchParameter(uri.substring(
          ("/" + resourceType + "/search/").length()));
      if (searchParameter == null) {
        result = null;
      } else {
        result.put("search", searchParameter);
      }
    } else if (uri.startsWith("/" + resourceType + "/lookup/")) {
      String fingerprintParameter = this.parseFingerprintParameter(
          uri.substring(("/" + resourceType + "/lookup/").length()));
      if (fingerprintParameter == null) {
        result = null;
      } else {
        result.put("lookup", fingerprintParameter);
      }
    } else {
      result = null;
    }
    return result;
  }

  private static Pattern searchParameterPattern =
      Pattern.compile("^\\$?[0-9a-fA-F]{1,40}$|^[0-9a-zA-Z\\.]{1,19}$");
  private String parseSearchParameter(String parameter) {
    if (!searchParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
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

  private void filterBySearchTerm(Map<String, String> filteredRelays,
      Map<String, String> filteredBridges, String searchTerm) {
    Set<String> removeRelays = new HashSet<String>();
    for (Map.Entry<String, String> e : filteredRelays.entrySet()) {
      String line = e.getValue();
      boolean lineMatches = false;
      if (searchTerm.startsWith("$")) {
        /* Search is for $-prefixed fingerprint. */
        if (line.contains("\"f\":\""
            + searchTerm.substring(1).toUpperCase())) {
          /* $-prefixed fingerprint matches. */
          lineMatches = true;
        }
      } else if (line.toLowerCase().contains("\"n\":\""
          + searchTerm.toLowerCase())) {
        /* Nickname matches. */
        lineMatches = true;
      } else if ("unnamed".startsWith(searchTerm.toLowerCase()) &&
          (line.startsWith("{\"f\":") || line.startsWith("{\"h\":"))) {
        /* Nickname "Unnamed" matches. */
        lineMatches = true;
      } else if (line.contains("\"f\":\"" + searchTerm.toUpperCase())) {
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
    if (searchTerm.startsWith("$")) {
      searchTerm = searchTerm.substring(1);
    }
    for (Map.Entry<String, String> e : filteredBridges.entrySet()) {
      String line = e.getValue();
      if (!line.contains("\"h\":\"" + searchTerm.toUpperCase())) {
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

  private void writeRelays(List<String> relays, PrintWriter pw,
      String resourceType) {
    pw.print(this.relaysPublishedLine + "\n");
    pw.print("\"relays\":[");
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
    pw.print(this.bridgesPublishedLine + "\n");
    pw.print("\"bridges\":[");
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
    File detailsFile = new File(this.outDirString + "details/"
        + fingerprint);
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
    File bandwidthFile = new File(this.outDirString + "bandwidth/"
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
}

