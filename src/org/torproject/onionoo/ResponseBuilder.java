/* Copyright 2011--2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ResponseBuilder {

  private DocumentStore documentStore;

  public ResponseBuilder() {
    this.documentStore = ApplicationFactory.getDocumentStore();
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

  private List<NodeStatus> orderedRelays = new ArrayList<NodeStatus>();
  public void setOrderedRelays(List<NodeStatus> orderedRelays) {
    this.orderedRelays = orderedRelays;
  }

  private List<NodeStatus> orderedBridges = new ArrayList<NodeStatus>();
  public void setOrderedBridges(List<NodeStatus> orderedBridges) {
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

  private void writeRelays(List<NodeStatus> relays, PrintWriter pw) {
    pw.write("{\"relays_published\":\"" + relaysPublishedString
        + "\",\n\"relays\":[");
    int written = 0;
    for (NodeStatus entry : relays) {
      String lines = this.formatNodeStatus(entry);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n],\n");
  }

  private void writeBridges(List<NodeStatus> bridges, PrintWriter pw) {
    pw.write("\"bridges_published\":\"" + bridgesPublishedString
        + "\",\n\"bridges\":[");
    int written = 0;
    for (NodeStatus entry : bridges) {
      String lines = this.formatNodeStatus(entry);
      if (lines.length() > 0) {
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    pw.print("\n]}\n");
  }

  private String formatNodeStatus(NodeStatus entry) {
    if (this.resourceType == null) {
      return "";
    } else if (this.resourceType.equals("summary")) {
      return this.writeSummaryLine(entry);
    } else if (this.resourceType.equals("details")) {
      return this.writeDetailsLines(entry);
    } else if (this.resourceType.equals("bandwidth")) {
      return this.writeBandwidthLines(entry);
    } else if (this.resourceType.equals("weights")) {
      return this.writeWeightsLines(entry);
    } else if (this.resourceType.equals("clients")) {
      return this.writeClientsLines(entry);
    } else if (this.resourceType.equals("uptime")) {
      return this.writeUptimeLines(entry);
    } else {
      return "";
    }
  }

  private String writeSummaryLine(NodeStatus entry) {
    return entry.isRelay() ? writeRelaySummaryLine(entry)
        : writeBridgeSummaryLine(entry);
  }

  private String writeRelaySummaryLine(NodeStatus entry) {
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

  private String writeBridgeSummaryLine(NodeStatus entry) {
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String hashedFingerprint = entry.getFingerprint();
    String running = entry.getRunning() ? "true" : "false";
    return String.format("{%s\"h\":\"%s\",\"r\":%s}",
         (nickname == null ? "" : "\"n\":\"" + nickname + "\","),
         hashedFingerprint, running);
  }

  private String writeDetailsLines(NodeStatus entry) {
    String fingerprint = entry.getFingerprint();
    DetailsDocument detailsDocument = this.documentStore.retrieve(
        DetailsDocument.class, false, fingerprint);
    if (detailsDocument != null &&
        detailsDocument.getDocumentString() != null) {
      StringBuilder sb = new StringBuilder();
      Scanner s = new Scanner(detailsDocument.getDocumentString());
      boolean includeLine = true;
      while (s.hasNextLine()) {
        String line = s.nextLine();
        if (line.equals("{")) {
          /* Omit newline after opening bracket. */
          sb.append("{");
        } else if (line.equals("}")) {
          /* Omit newline after closing bracket. */
          sb.append("}");
          break;
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
      if (detailsLines.endsWith(",\n}")) {
        /* Fix broken JSON if we omitted lines above. */
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

  private String writeBandwidthLines(NodeStatus entry) {
    String fingerprint = entry.getFingerprint();
    BandwidthDocument bandwidthDocument = this.documentStore.retrieve(
        BandwidthDocument.class, false, fingerprint);
    if (bandwidthDocument != null &&
        bandwidthDocument.getDocumentString() != null) {
      String bandwidthLines = bandwidthDocument.getDocumentString();
      bandwidthLines = bandwidthLines.substring(0,
          bandwidthLines.length() - 1);
      return bandwidthLines;
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeWeightsLines(NodeStatus entry) {
    String fingerprint = entry.getFingerprint();
    WeightsDocument weightsDocument = this.documentStore.retrieve(
        WeightsDocument.class, false, fingerprint);
    if (weightsDocument != null &&
        weightsDocument.getDocumentString() != null) {
      String weightsLines = weightsDocument.getDocumentString();
      weightsLines = weightsLines.substring(0, weightsLines.length() - 1);
      return weightsLines;
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeClientsLines(NodeStatus entry) {
    String fingerprint = entry.getFingerprint();
    ClientsDocument clientsDocument = this.documentStore.retrieve(
        ClientsDocument.class, false, fingerprint);
    if (clientsDocument != null &&
        clientsDocument.getDocumentString() != null) {
      String clientsLines = clientsDocument.getDocumentString();
      clientsLines = clientsLines.substring(0, clientsLines.length() - 1);
      return clientsLines;
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeUptimeLines(NodeStatus entry) {
    String fingerprint = entry.getFingerprint();
    UptimeDocument uptimeDocument = this.documentStore.retrieve(
        UptimeDocument.class, false, fingerprint);
    if (uptimeDocument != null &&
        uptimeDocument.getDocumentString() != null) {
      String uptimeLines = uptimeDocument.getDocumentString();
      uptimeLines = uptimeLines.substring(0, uptimeLines.length() - 1);
      return uptimeLines;
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }
}
