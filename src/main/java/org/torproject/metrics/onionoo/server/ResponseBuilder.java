/* Copyright 2011--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import org.torproject.metrics.onionoo.docs.BandwidthDocument;
import org.torproject.metrics.onionoo.docs.ClientsDocument;
import org.torproject.metrics.onionoo.docs.DetailsDocument;
import org.torproject.metrics.onionoo.docs.DocumentStore;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.docs.SummaryDocument;
import org.torproject.metrics.onionoo.docs.UptimeDocument;
import org.torproject.metrics.onionoo.docs.WeightsDocument;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

public class ResponseBuilder {

  private static final Logger logger =
      LoggerFactory.getLogger(ResponseBuilder.class);

  private static ObjectMapper objectMapper = new ObjectMapper();

  private DocumentStore documentStore;
  private String buildRevision;

  /** Initialize document store and the build revision. */
  public ResponseBuilder() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    Properties buildProperties = new Properties();
    try (InputStream is = getClass().getClassLoader()
        .getResourceAsStream("onionoo.buildrevision.properties")) {
      buildProperties.load(is);
      buildRevision = buildProperties.getProperty("onionoo.build.revision",
          null);
    } catch (Exception ex) {
      // We create documents anyway: only log a warning.
      logger.warn("No build revision available.", ex);
      buildRevision = null;
    }
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

  private List<SummaryDocument> orderedRelays = new ArrayList<>();

  public void setOrderedRelays(List<SummaryDocument> orderedRelays) {
    this.orderedRelays = orderedRelays;
  }

  private List<SummaryDocument> orderedBridges = new ArrayList<>();

  public void setOrderedBridges(List<SummaryDocument> orderedBridges) {
    this.orderedBridges = orderedBridges;
  }

  private int relaysSkipped;

  public void setRelaysSkipped(int relaysSkipped) {
    this.relaysSkipped = relaysSkipped;
  }

  private int bridgesSkipped;

  public void setBridgesSkipped(int bridgesSkipped) {
    this.bridgesSkipped = bridgesSkipped;
  }

  private int relaysTruncated;

  public void setRelaysTruncated(int relaysTruncated) {
    this.relaysTruncated = relaysTruncated;
  }

  private int bridgesTruncated;

  public void setBridgesTruncated(int bridgesTruncated) {
    this.bridgesTruncated = bridgesTruncated;
  }

  private List<String> fields;

  public void setFields(String[] fields) {
    this.fields = Arrays.asList(fields);
  }

  public void buildResponse(PrintWriter pw) {
    writeRelays(this.orderedRelays, pw);
    writeBridges(this.orderedBridges, pw);
  }

  private int charsWritten = 0;

  public int getCharsWritten() {
    return this.charsWritten;
  }

  private static final String PROTOCOL_VERSION = "8.0";

  private static final String NEXT_MAJOR_VERSION_SCHEDULED = null;

  private void writeRelays(List<SummaryDocument> relays, PrintWriter pw) {
    this.write(pw, "{\"version\":\"%s\",\n", PROTOCOL_VERSION);
    if (null != NEXT_MAJOR_VERSION_SCHEDULED) {
      this.write(pw, "\"next_major_version_scheduled\":\"%s\",\n",
          NEXT_MAJOR_VERSION_SCHEDULED);
    }
    if (null != buildRevision) {
      this.write(pw, "\"build_revision\":\"%s\",\n", buildRevision);
    }
    this.write(pw, "\"relays_published\":\"%s\",\n",
        this.relaysPublishedString);
    if (this.relaysSkipped > 0) {
      this.write(pw, "\"relays_skipped\":%d,\n", this.relaysSkipped);
    }
    this.write(pw, "\"relays\":[");

    int written = 0;
    for (SummaryDocument entry : relays) {
      String lines = this.formatNodeStatus(entry);
      if (lines.length() > 0) {
        this.write(pw, "%s%s", written++ > 0 ? ",\n" : "\n", lines);
      }
    }
    this.write(pw, "\n],\n");
    if (this.relaysTruncated > 0) {
      this.write(pw, "\"relays_truncated\":%d,\n", this.relaysTruncated);
    }
  }

  private void writeBridges(List<SummaryDocument> bridges,
      PrintWriter pw) {
    this.write(pw, "\"bridges_published\":\"" + bridgesPublishedString
        + "\",\n");
    if (this.bridgesSkipped > 0) {
      this.write(pw, "\"bridges_skipped\":%d,\n", this.bridgesSkipped);
    }
    this.write(pw, "\"bridges\":[");
    int written = 0;
    for (SummaryDocument entry : bridges) {
      String lines = this.formatNodeStatus(entry);
      if (lines.length() > 0) {
        this.write(pw, (written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    this.write(pw, "\n]");
    if (this.bridgesTruncated > 0) {
      this.write(pw, ",\n\"bridges_truncated\":%d", this.bridgesTruncated);
    }
    this.write(pw, "}\n");
  }

  private void write(PrintWriter pw, String format, Object ... args) {
    String stringToWrite = String.format(format, args);
    this.charsWritten += stringToWrite.length();
    pw.write(stringToWrite);
  }

  private String formatNodeStatus(SummaryDocument entry) {
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

  private String writeSummaryLine(SummaryDocument entry) {
    return entry.isRelay() ? writeRelaySummaryLine(entry)
        : writeBridgeSummaryLine(entry);
  }

  private String writeRelaySummaryLine(SummaryDocument entry) {
    String nickname = entry.getNickname();
    String fingerprint = entry.getFingerprint();
    String running = entry.isRunning() ? "true" : "false";
    List<String> addresses = entry.getAddresses();
    StringBuilder addressesBuilder = new StringBuilder();
    int written = 0;
    for (String address : addresses) {
      addressesBuilder.append(written++ > 0 ? "," : "").append("\"")
          .append(address.toLowerCase()).append("\"");
    }
    return String.format("{\"n\":\"%s\",\"f\":\"%s\",\"a\":[%s],\"r\":%s}",
        nickname, fingerprint, addressesBuilder.toString(), running);
  }

  private String writeBridgeSummaryLine(SummaryDocument entry) {
    String nickname = entry.getNickname();
    String hashedFingerprint = entry.getFingerprint();
    String running = entry.isRunning() ? "true" : "false";
    return String.format("{\"n\":\"%s\",\"h\":\"%s\",\"r\":%s}",
        nickname, hashedFingerprint, running);
  }

  private String writeDetailsLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    DetailsDocument detailsDocument = documentStore.retrieve(
        DetailsDocument.class, false, fingerprint);
    String documentString = "";
    if (null != detailsDocument) {
      documentString = detailsDocument.getDocumentString();
      if (null != this.fields) {
        try {
          LinkedHashMap<String, Object> parsedDocument
              = objectMapper.readValue(documentString,
              new TypeReference<LinkedHashMap<String, Object>>() {});
          parsedDocument.keySet().retainAll(this.fields);
          documentString = objectMapper.writeValueAsString(parsedDocument);
        } catch (IOException e) {
          /* Handle below. */
          documentString = "";
        }
      }
    }
    // TODO We should probably log that we didn't find a details
    // document that we expected to exist.
    return documentString;
  }

  private String writeBandwidthLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    BandwidthDocument bandwidthDocument = this.documentStore.retrieve(
        BandwidthDocument.class, false, fingerprint);
    if (bandwidthDocument != null
        && bandwidthDocument.getDocumentString() != null) {
      return bandwidthDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeWeightsLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    WeightsDocument weightsDocument = this.documentStore.retrieve(
        WeightsDocument.class, false, fingerprint);
    if (weightsDocument != null
        && weightsDocument.getDocumentString() != null) {
      return weightsDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeClientsLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    ClientsDocument clientsDocument = this.documentStore.retrieve(
        ClientsDocument.class, false, fingerprint);
    if (clientsDocument != null
        && clientsDocument.getDocumentString() != null) {
      return clientsDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeUptimeLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    UptimeDocument uptimeDocument = this.documentStore.retrieve(
        UptimeDocument.class, false, fingerprint);
    if (uptimeDocument != null
        && uptimeDocument.getDocumentString() != null) {
      return uptimeDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }
}

