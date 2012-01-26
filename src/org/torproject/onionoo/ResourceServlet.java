/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

  public void init() {
    this.readSummaryFile();
  }

  long summaryFileLastModified = 0L;
  boolean readSummaryFile = false;
  private String versionLine = null, validAfterLine = null,
      freshUntilLine = null;
  private List<String> relayLines = new ArrayList<String>(),
      bridgeLines = new ArrayList<String>();
  private void readSummaryFile() {
    File summaryFile = new File("/srv/onionoo/out/summary.json");
    if (!summaryFile.exists()) {
      readSummaryFile = false;
      return;
    }
    if (summaryFile.lastModified() > this.summaryFileLastModified) {
      this.versionLine = this.validAfterLine = this.freshUntilLine = null;
      this.relayLines.clear();
      this.bridgeLines.clear();
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            summaryFile));
        String line;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("{\"version\":")) {
            this.versionLine = line;
          } else if (line.startsWith("\"valid_after\":")) {
            this.validAfterLine = line;
          } else if (line.startsWith("\"fresh_until\":")) {
            this.freshUntilLine = line;
          } else if (line.startsWith("\"relays\":")) {
            while ((line = br.readLine()) != null && !line.equals("],")) {
              this.relayLines.add(line);
            }
          } else if (line.startsWith("\"bridges\":")) {
            while ((line = br.readLine()) != null && !line.equals("]}")) {
              this.bridgeLines.add(line);
            }
          }
        }
        br.close();
      } catch (IOException e) {
        return;
      }
    }
    this.summaryFileLastModified = summaryFile.lastModified();
    this.readSummaryFile = true;
  }

  public void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {

    this.readSummaryFile();
    if (!this.readSummaryFile) {
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String uri = request.getRequestURI();
    String resourceType = null;
    if (uri.startsWith("/summary/")) {
      resourceType = "summary";
    } else if (uri.startsWith("/details/")) {
      resourceType = "details";
    } else if (uri.startsWith("/bandwidth/")) {
      resourceType = "bandwidth";
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    StringBuilder sb = new StringBuilder();
    if (uri.equals("/" + resourceType + "/all")) {
      this.writeHeader(sb);
      this.writeAllRelays(sb, resourceType);
      this.writeAllBridges(sb, resourceType);
    } else if (uri.equals("/" + resourceType + "/running")) {
      this.writeHeader(sb);
      this.writeRunningRelays(sb, resourceType);
      this.writeRunningBridges(sb, resourceType);
    } else if (uri.equals("/" + resourceType + "/relays")) {
      this.writeHeader(sb);
      this.writeAllRelays(sb, resourceType);
      this.writeNoBridges(sb);
    } else if (uri.equals("/" + resourceType + "/bridges")) {
      this.writeHeader(sb);
      this.writeNoRelays(sb);
      this.writeAllBridges(sb, resourceType);
    } else if (uri.startsWith("/" + resourceType + "/search/")) {
      String searchParameter = this.parseSearchParameter(uri.substring(
          ("/" + resourceType + "/search/").length()));
      if (searchParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.writeHeader(sb);
      this.writeMatchingRelays(sb, searchParameter, resourceType);
      this.writeMatchingBridges(sb, searchParameter, resourceType);
    } else if (uri.startsWith("/" + resourceType + "/lookup/")) {
      Set<String> fingerprintParameters = this.parseFingerprintParameters(
          uri.substring(("/" + resourceType + "/lookup/").length()));
      if (fingerprintParameters == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.writeHeader(sb);
      this.writeRelaysWithFingerprints(sb, fingerprintParameters,
          resourceType);
      this.writeBridgesWithFingerprints(sb, fingerprintParameters,
          resourceType);
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    byte[] result = sb.toString().getBytes();
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json");
    response.setIntHeader("Content-Length", result.length);
    BufferedOutputStream output = new BufferedOutputStream(
        response.getOutputStream());
    output.write(result);
    output.flush();
    output.close();
  }

  private static Pattern searchParameterPattern =
      Pattern.compile("^[0-9a-zA-Z\\.]{1,40}$");
  private String parseSearchParameter(String parameter) {
    if (!searchParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    return parameter;
  }

  private static Pattern fingerprintParameterPattern =
      Pattern.compile("^[a-zA-Z]+$");
  private Set<String> parseFingerprintParameters(String parameter) {
    if (!searchParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    Set<String> parsedFingerprints = new HashSet<String>();
    if (parameter.length() != 40) {
      return null;
    }
    parsedFingerprints.add(parameter);
    return parsedFingerprints;
  }

  private void writeHeader(StringBuilder sb) {
    sb.append(this.versionLine + "\n");
    sb.append(this.validAfterLine + "\n");
    sb.append(this.freshUntilLine + "\n");
  }

  private void writeAllRelays(StringBuilder sb, String resourceType) {
    sb.append("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      String lines = this.getFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        sb.append((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    sb.append("],\n");
  }

  private void writeRunningRelays(StringBuilder sb, String resourceType) {
    sb.append("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      if (line.contains("\"r\":true")) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          sb.append((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    sb.append("\n],\n");
  }

  private void writeNoRelays(StringBuilder sb) {
    sb.append("\"relays\":[\n");
    sb.append("],\n");
  }

  private void writeMatchingRelays(StringBuilder sb, String searchTerm,
      String resourceType) {
    sb.append("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      if (line.toLowerCase().contains("\"n\":\""
          + searchTerm.toLowerCase()) ||
          ("unnamed".startsWith(searchTerm.toLowerCase()) &&
          line.startsWith("{\"f\":")) ||
          line.contains("\"f\":\"" + searchTerm.toUpperCase()) ||
          line.substring(line.indexOf("\"a\":[")).contains("\""
          + searchTerm.toLowerCase())) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          sb.append((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    sb.append("\n],\n");
  }

  private void writeRelaysWithFingerprints(StringBuilder sb,
      Set<String> fingerprints, String resourceType) {
    sb.append("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      for (String fingerprint : fingerprints) {
        if (line.contains("\"f\":\"" + fingerprint.toUpperCase()
            + "\",")) {
          String lines = this.getFromSummaryLine(line, resourceType);
          if (lines.length() > 0) {
            sb.append((written++ > 0 ? ",\n" : "\n") + lines);
          }
          break;
        }
      }
    }
    sb.append("\n],\n");
  }

  private void writeAllBridges(StringBuilder sb, String resourceType) {
    sb.append("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      String lines = this.getFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        sb.append((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    sb.append("\n]}\n");
  }

  private void writeRunningBridges(StringBuilder sb,
      String resourceType) {
    sb.append("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      if (line.contains("\"r\":true")) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          sb.append((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    sb.append("\n]}\n");
  }

  private void writeNoBridges(StringBuilder sb) {
    sb.append("\"bridges\":[\n");
    sb.append("]}\n");
  }

  private void writeMatchingBridges(StringBuilder sb, String searchTerm,
      String resourceType) {
    sb.append("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      if (line.contains("\"h\":\"" + searchTerm.toUpperCase())) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          sb.append((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    sb.append("\n]}\n");
  }

  private void writeBridgesWithFingerprints(StringBuilder sb,
      Set<String> fingerprints, String resourceType) {
    sb.append("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      for (String fingerprint : fingerprints) {
        if (line.contains("\"h\":\"" + fingerprint.toUpperCase()
            + "\",")) {
          String lines = this.getFromSummaryLine(line,
              resourceType);
          if (lines.length() > 0) {
            sb.append((written++ > 0 ? ",\n" : "\n") + lines);
          }
          break;
        }
      }
    }
    sb.append("\n]}\n");
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
    File detailsFile = new File("/srv/onionoo/out/details/"
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
            sb.append(line + "\n");
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
    File detailsFile = new File("/srv/onionoo/out/bandwidth/"
        + fingerprint);
    StringBuilder sb = new StringBuilder();
    String bandwidthLines = null;
    if (detailsFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            detailsFile));
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

