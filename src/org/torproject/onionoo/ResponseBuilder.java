/* Copyright 2011--2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ResponseBuilder {

  private static DocumentStore documentStore;

  public static void initialize() {
    documentStore = ApplicationFactory.getDocumentStore();
  }

  private String resourceType;
  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  private String relaysPublishedString;
  public void setRelaysPublishedString(String relaysPublishedString) {
    this.relaysPublishedString = relaysPublishedString;
  }

  private String bridgesPublishedString;
  public void setBridgesPublishedString(String bridgesPublishedString) {
    this.bridgesPublishedString = bridgesPublishedString;
  }

  private List<String> orderedRelays = new ArrayList<String>();
  public void setOrderedRelays(List<String> orderedRelays) {
    this.orderedRelays = orderedRelays;
  }

  private List<String> orderedBridges = new ArrayList<String>();
  public void setOrderedBridges(List<String> orderedBridges) {
    this.orderedBridges = orderedBridges;
  }

  private String[] fields;
  public void setFields(String[] fields) {
    this.fields = new String[fields.length];
    System.arraycopy(fields, 0, this.fields, 0, fields.length);
  }

  public void buildResponse(PrintWriter pw) {
    writeRelays(orderedRelays, pw);
    writeBridges(orderedBridges, pw);
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
    } else if (this.resourceType.equals("clients")) {
      return this.writeClientsLines(summaryLine);
    } else if (this.resourceType.equals("uptime")) {
      return this.writeUptimeLines(summaryLine);
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
        detailsDocument.getDocumentString() != null) {
      StringBuilder sb = new StringBuilder();
      Scanner s = new Scanner(detailsDocument.getDocumentString());
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
        } else if (line.startsWith("\"hibernating\":True")) {
          /* TODO This workaround saves us from bulk-editing all details
           * files in out/details/ and status/details/.  May take this out
           * when all/most of those files with invalid JSON are gone. */
          sb.append(line.replaceAll("T", "t") + "\n");
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
        bandwidthDocument.getDocumentString() != null) {
      String bandwidthLines = bandwidthDocument.getDocumentString();
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
        weightsDocument.getDocumentString() != null) {
      String weightsLines = weightsDocument.getDocumentString();
      weightsLines = weightsLines.substring(0, weightsLines.length() - 1);
      return weightsLines;
    } else {
      // TODO We should probably log that we didn't find a weights
      // document that we expected to exist.
      return "";
    }
  }

  private String writeClientsLines(String summaryLine) {
    String fingerprint = null;
    if (summaryLine.contains("\"h\":\"")) {
      fingerprint = summaryLine.substring(summaryLine.indexOf(
         "\"h\":\"") + "\"h\":\"".length());
    } else {
      return "";
    }
    fingerprint = fingerprint.substring(0, 40);
    ClientsDocument clientsDocument = documentStore.retrieve(
        ClientsDocument.class, false, fingerprint);
    if (clientsDocument != null &&
        clientsDocument.getDocumentString() != null) {
      String clientsLines = clientsDocument.getDocumentString();
      clientsLines = clientsLines.substring(0, clientsLines.length() - 1);
      return clientsLines;
    } else {
      // TODO We should probably log that we didn't find a clients
      // document that we expected to exist.
      return "";
    }
  }

  private String writeUptimeLines(String summaryLine) {
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
    UptimeDocument uptimeDocument = documentStore.retrieve(
        UptimeDocument.class, false, fingerprint);
    if (uptimeDocument != null &&
        uptimeDocument.getDocumentString() != null) {
      String uptimeLines = uptimeDocument.getDocumentString();
      uptimeLines = uptimeLines.substring(0, uptimeLines.length() - 1);
      return uptimeLines;
    } else {
      // TODO We should probably log that we didn't find an uptime
      // document that we expected to exist.
      return "";
    }
  }
}
