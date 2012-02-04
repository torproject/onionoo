/* Copyright 2011, 2012 The Tor Project
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
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

  private static final long serialVersionUID = 7236658979947465319L;

  public void init() {
    this.readSummaryFile();
  }

  long summaryFileLastModified = 0L;
  boolean readSummaryFile = false;
  private String versionLine = null, validAfterLine = null,
      freshUntilLine = null, relaysPublishedLine = null,
      bridgesPublishedLine = null;
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
          } else if (line.startsWith("\"relays_published\":")) {
            this.relaysPublishedLine = line;
          } else if (line.startsWith("\"bridges_published\":")) {
            this.bridgesPublishedLine = line;
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

    /* Handle any errors resulting from invalid requests. */
    if (uri.equals("/" + resourceType + "/all")) {
    } else if (uri.equals("/" + resourceType + "/running")) {
    } else if (uri.equals("/" + resourceType + "/relays")) {
    } else if (uri.equals("/" + resourceType + "/bridges")) {
    } else if (uri.startsWith("/" + resourceType + "/search/")) {
      String searchParameter = this.parseSearchParameter(uri.substring(
          ("/" + resourceType + "/search/").length()));
      if (searchParameter == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    } else if (uri.startsWith("/" + resourceType + "/lookup/")) {
      Set<String> fingerprintParameters = this.parseFingerprintParameters(
          uri.substring(("/" + resourceType + "/lookup/").length()));
      if (fingerprintParameters == null) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    } else {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    /* Set response headers and start writing the response. */
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setContentType("application/json");
    PrintWriter pw = response.getWriter();
    if (uri.equals("/" + resourceType + "/all")) {
      this.writeHeader(pw);
      pw.print(this.relaysPublishedLine + "\n");
      this.writeAllRelays(pw, resourceType);
      pw.print(this.bridgesPublishedLine + "\n");
      this.writeAllBridges(pw, resourceType);
    } else if (uri.equals("/" + resourceType + "/running")) {
      this.writeHeader(pw);
      pw.print(this.relaysPublishedLine + "\n");
      this.writeRunningRelays(pw, resourceType);
      pw.print(this.bridgesPublishedLine + "\n");
      this.writeRunningBridges(pw, resourceType);
    } else if (uri.equals("/" + resourceType + "/relays")) {
      this.writeHeader(pw);
      pw.print(this.relaysPublishedLine + "\n");
      this.writeAllRelays(pw, resourceType);
      pw.print(this.bridgesPublishedLine + "\n");
      this.writeNoBridges(pw);
    } else if (uri.equals("/" + resourceType + "/bridges")) {
      this.writeHeader(pw);
      pw.print(this.relaysPublishedLine + "\n");
      this.writeNoRelays(pw);
      pw.print(this.bridgesPublishedLine + "\n");
      this.writeAllBridges(pw, resourceType);
    } else if (uri.startsWith("/" + resourceType + "/search/")) {
      String searchParameter = this.parseSearchParameter(uri.substring(
          ("/" + resourceType + "/search/").length()));
      this.writeHeader(pw);
      pw.print(this.relaysPublishedLine + "\n");
      this.writeMatchingRelays(pw, searchParameter, resourceType);
      pw.print(this.bridgesPublishedLine + "\n");
      this.writeMatchingBridges(pw, searchParameter, resourceType);
    } else if (uri.startsWith("/" + resourceType + "/lookup/")) {
      Set<String> fingerprintParameters = this.parseFingerprintParameters(
          uri.substring(("/" + resourceType + "/lookup/").length()));
      this.writeHeader(pw);
      pw.print(this.relaysPublishedLine + "\n");
      this.writeRelaysWithFingerprints(pw, fingerprintParameters,
          resourceType);
      pw.print(this.bridgesPublishedLine + "\n");
      this.writeBridgesWithFingerprints(pw, fingerprintParameters,
          resourceType);
    }
    pw.flush();
    pw.close();
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
    if (!fingerprintParameterPattern.matcher(parameter).matches()) {
      return null;
    }
    Set<String> parsedFingerprints = new HashSet<String>();
    if (parameter.length() != 40) {
      return null;
    }
    parsedFingerprints.add(parameter);
    return parsedFingerprints;
  }

  private void writeHeader(PrintWriter pw) {
    pw.print(this.versionLine + "\n");
    pw.print(this.validAfterLine + "\n");
    pw.print(this.freshUntilLine + "\n");
  }

  private void writeAllRelays(PrintWriter pw, String resourceType) {
    pw.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      String lines = this.getFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("],\n");
  }

  private void writeRunningRelays(PrintWriter pw, String resourceType) {
    pw.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      if (line.contains("\"r\":true")) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          pw.print((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    pw.print("\n],\n");
  }

  private void writeNoRelays(PrintWriter pw) {
    pw.print("\"relays\":[\n");
    pw.print("],\n");
  }

  private void writeMatchingRelays(PrintWriter pw, String searchTerm,
      String resourceType) {
    pw.print("\"relays\":[");
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
          pw.print((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    pw.print("\n],\n");
  }

  private void writeRelaysWithFingerprints(PrintWriter pw,
      Set<String> fingerprints, String resourceType) {
    pw.print("\"relays\":[");
    int written = 0;
    for (String line : this.relayLines) {
      for (String fingerprint : fingerprints) {
        if (line.contains("\"f\":\"" + fingerprint.toUpperCase()
            + "\",")) {
          String lines = this.getFromSummaryLine(line, resourceType);
          if (lines.length() > 0) {
            pw.print((written++ > 0 ? ",\n" : "\n") + lines);
          }
          break;
        }
      }
    }
    pw.print("\n],\n");
  }

  private void writeAllBridges(PrintWriter pw, String resourceType) {
    pw.print("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      String lines = this.getFromSummaryLine(line, resourceType);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n]}\n");
  }

  private void writeRunningBridges(PrintWriter pw, String resourceType) {
    pw.print("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      if (line.contains("\"r\":true")) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          pw.print((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    pw.print("\n]}\n");
  }

  private void writeNoBridges(PrintWriter pw) {
    pw.print("\"bridges\":[\n");
    pw.print("]}\n");
  }

  private void writeMatchingBridges(PrintWriter pw, String searchTerm,
      String resourceType) {
    pw.print("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      if (line.contains("\"h\":\"" + searchTerm.toUpperCase())) {
        String lines = this.getFromSummaryLine(line, resourceType);
        if (lines.length() > 0) {
          pw.print((written++ > 0 ? ",\n" : "\n") + lines);
        }
      }
    }
    pw.print("\n]}\n");
  }

  private void writeBridgesWithFingerprints(PrintWriter pw,
      Set<String> fingerprints, String resourceType) {
    pw.print("\"bridges\":[");
    int written = 0;
    for (String line : this.bridgeLines) {
      for (String fingerprint : fingerprints) {
        if (line.contains("\"h\":\"" + fingerprint.toUpperCase()
            + "\",")) {
          String lines = this.getFromSummaryLine(line,
              resourceType);
          if (lines.length() > 0) {
            pw.print((written++ > 0 ? ",\n" : "\n") + lines);
          }
          break;
        }
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

