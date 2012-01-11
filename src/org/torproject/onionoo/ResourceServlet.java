/* Copyright 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
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

    PrintWriter out = response.getWriter();
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

    if (uri.equals("/" + resourceType + "/all")) {
      this.writeHeader(out);
      this.writeAllRelays(out, resourceType);
      this.writeAllBridges(out, resourceType);
    } else if (uri.equals("/" + resourceType + "/running")) {
      this.writeHeader(out);
      this.writeRunningRelays(out, resourceType);
      this.writeRunningBridges(out, resourceType);
    } else if (uri.equals("/" + resourceType + "/relays")) {
      this.writeHeader(out);
      this.writeAllRelays(out, resourceType);
      this.writeNoBridges(out);
    } else if (uri.equals("/" + resourceType + "/bridges")) {
      this.writeHeader(out);
      this.writeNoRelays(out);
      this.writeAllBridges(out, resourceType);
    } else if (uri.startsWith("/" + resourceType + "/search/")) {
      String searchParameter = this.parseSearchParameter(uri.substring(
          ("/" + resourceType + "/search/").length()));
      if (searchParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.writeHeader(out);
      this.writeMatchingRelays(out, searchParameter, resourceType);
      this.writeNoBridges(out);
    } else if (uri.startsWith("/" + resourceType + "/lookup/")) {
      Set<String> fingerprintParameters = this.parseFingerprintParameters(
          uri.substring(("/" + resourceType + "/lookup/").length()));
      if (fingerprintParameters == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      this.writeHeader(out);
      this.writeRelaysWithFingerprints(out, fingerprintParameters, resourceType);
      this.writeBridgesWithFingerprints(out, fingerprintParameters, resourceType);
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    out.close();
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

  private void writeHeader(PrintWriter out) {
    out.println(this.versionLine);
    out.println(this.validAfterLine);
    out.println(this.freshUntilLine);
  }

  private void writeAllRelays(PrintWriter out, String resourceType) {
    out.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      String lines = this.getRelayFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        out.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    out.println("],");
  }

  private void writeRunningRelays(PrintWriter out, String resourceType) {
    out.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      if (line.contains("\"r\":true")) {
        String lines = this.getRelayFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          out.print((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    out.println("\n],");
  }

  private void writeNoRelays(PrintWriter out) {
    out.println("\"relays\":[");
    out.println("],");
  }

  private void writeMatchingRelays(PrintWriter out, String searchTerm,
      String resourceType) {
    out.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      if (line.toLowerCase().contains("\"n\":\""
          + searchTerm.toLowerCase()) ||
          ("unnamed".startsWith(searchTerm.toLowerCase()) &&
          line.startsWith("{\"f\":")) ||
          line.contains("\"f\":\"" + searchTerm.toUpperCase()) ||
          line.substring(line.indexOf("\"a\":[")).contains("\""
          + searchTerm)) {
        String lines = this.getRelayFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          out.print((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    out.println("\n],");
  }

  private void writeRelaysWithFingerprints(PrintWriter out,
      Set<String> fingerprints, String resourceType) {
    out.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      for (String fingerprint : fingerprints) {
        if (line.contains("\"f\":\"" + fingerprint.toUpperCase()
            + "\",")) {
          String lines = this.getRelayFromSummaryLine(line, resourceType);
          if (lines.length() > 0) {
            out.print((written++ > 0 ? ",\n" : "\n") + lines);
          }
          break;
        }
      }
    }
    out.println("\n],");
  }

  private void writeAllBridges(PrintWriter out, String resourceType) {
    out.println("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      out.print((written++ > 0 ? ",\n" : "\n"));
      this.writeBridgeFromSummaryLine(out, line, resourceType);
    }
    out.println("]}");
  }

  private void writeRunningBridges(PrintWriter out, String resourceType) {
    out.print("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      if (line.contains("\"r\":true")) {
        out.print((written++ > 0 ? ",\n" : "\n"));
        this.writeBridgeFromSummaryLine(out, line, resourceType);
      }
    }
    out.println("\n]}");
  }

  private void writeNoBridges(PrintWriter out) {
    out.println("\"bridges\":[");
    out.println("]}");
  }

  private void writeBridgesWithFingerprints(PrintWriter out,
      Set<String> fingerprints, String resourceType) {
    out.println("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      for (String fingerprint : fingerprints) {
        if (line.contains("\"h\":\"" + fingerprint.toUpperCase()
            + "\",")) {
          out.print((written++ > 0 ? ",\n" : "\n"));
          this.writeBridgeFromSummaryLine(out, line, resourceType);
          break;
        }
      }
    }
    out.println("]}");
  }

  private String getRelayFromSummaryLine(String summaryLine,
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
    String fingerprint = summaryLine.substring(summaryLine.indexOf(
       "\"f\":\"") + "\"f\":\"".length());
    fingerprint = fingerprint.substring(0, 40);
    File detailsFile = new File("/srv/onionoo/out/details/"
        + fingerprint);
    StringBuilder sb = new StringBuilder();
    String detailsLines = null;
    if (detailsFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            detailsFile));
        String line;
        boolean correctValidAfterLine = false, copyLines = false;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("\"valid_after\"") &&
              line.equals(this.validAfterLine)) {
            correctValidAfterLine = true;
          } else if (line.startsWith("\"nickname\":")) {
            sb.append("{");
            copyLines = true;
          }
          if (copyLines) {
            sb.append(line + "\n");
          }
        }
        br.close();
        if (correctValidAfterLine) {
          detailsLines = sb.toString();
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
    String fingerprint = summaryLine.substring(summaryLine.indexOf(
       "\"f\":\"") + "\"f\":\"".length());
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

  private void writeBridgeFromSummaryLine(PrintWriter out, String line,
      String resourceType) {
    /* TODO Implement me. */
  }
}

