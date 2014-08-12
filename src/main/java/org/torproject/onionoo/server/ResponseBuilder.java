/* Copyright 2011--2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.server;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.torproject.onionoo.docs.BandwidthDocument;
import org.torproject.onionoo.docs.ClientsDocument;
import org.torproject.onionoo.docs.DetailsDocument;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.SummaryDocument;
import org.torproject.onionoo.docs.UptimeDocument;
import org.torproject.onionoo.docs.WeightsDocument;
import org.torproject.onionoo.util.ApplicationFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

  private List<SummaryDocument> orderedRelays =
      new ArrayList<SummaryDocument>();
  public void setOrderedRelays(List<SummaryDocument> orderedRelays) {
    this.orderedRelays = orderedRelays;
  }

  private List<SummaryDocument> orderedBridges =
      new ArrayList<SummaryDocument>();
  public void setOrderedBridges(List<SummaryDocument> orderedBridges) {
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

  private int charsWritten = 0;
  public int getCharsWritten() {
    return this.charsWritten;
  }

  private void writeRelays(List<SummaryDocument> relays, PrintWriter pw) {
    String header = "{\"relays_published\":\"" + relaysPublishedString
        + "\",\n\"relays\":[";
    this.charsWritten += header.length();
    pw.write(header);
    int written = 0;
    for (SummaryDocument entry : relays) {
      String lines = this.formatNodeStatus(entry);
      if (lines.length() > 0) {
        this.charsWritten += (written > 0 ? 2 : 1) + lines.length();
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    String footer = "\n],\n";
    this.charsWritten += footer.length();
    pw.print(footer);
  }

  private void writeBridges(List<SummaryDocument> bridges,
      PrintWriter pw) {
    String header = "\"bridges_published\":\"" + bridgesPublishedString
        + "\",\n\"bridges\":[";
    this.charsWritten += header.length();
    pw.write(header);
    int written = 0;
    for (SummaryDocument entry : bridges) {
      String lines = this.formatNodeStatus(entry);
      if (lines.length() > 0) {
        this.charsWritten += (written > 0 ? 2 : 1) + lines.length();
        pw.print((written++ > 0 ? ",\n" : "\n") + lines);
      }
    }
    String footer = "\n]}\n";
    this.charsWritten += footer.length();
    pw.print(footer);
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
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String fingerprint = entry.getFingerprint();
    String running = entry.isRunning() ? "true" : "false";
    List<String> addresses = entry.getAddresses();
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

  private String writeBridgeSummaryLine(SummaryDocument entry) {
    String nickname = !entry.getNickname().equals("Unnamed") ?
        entry.getNickname() : null;
    String hashedFingerprint = entry.getFingerprint();
    String running = entry.isRunning() ? "true" : "false";
    return String.format("{%s\"h\":\"%s\",\"r\":%s}",
         (nickname == null ? "" : "\"n\":\"" + nickname + "\","),
         hashedFingerprint, running);
  }

  private String writeDetailsLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    if (this.fields != null) {
      /* TODO Maybe there's a more elegant way (more maintainable, more
       * efficient, etc.) to implement this? */
      DetailsDocument detailsDocument = documentStore.retrieve(
          DetailsDocument.class, true, fingerprint);
      if (detailsDocument != null) {
        DetailsDocument dd = new DetailsDocument();
        for (String field : this.fields) {
          if (field.equals("nickname")) {
            dd.setNickname(detailsDocument.getNickname());
          } else if (field.equals("fingerprint")) {
            dd.setFingerprint(detailsDocument.getFingerprint());
          } else if (field.equals("hashed_fingerprint")) {
            dd.setHashedFingerprint(
                detailsDocument.getHashedFingerprint());
          } else if (field.equals("or_addresses")) {
            dd.setOrAddresses(detailsDocument.getOrAddresses());
          } else if (field.equals("exit_addresses")) {
            dd.setExitAddresses(detailsDocument.getExitAddresses());
          } else if (field.equals("dir_address")) {
            dd.setDirAddress(detailsDocument.getDirAddress());
          } else if (field.equals("last_seen")) {
            dd.setLastSeen(detailsDocument.getLastSeen());
          } else if (field.equals("last_changed_address_or_port")) {
            dd.setLastChangedAddressOrPort(
                detailsDocument.getLastChangedAddressOrPort());
          } else if (field.equals("first_seen")) {
            dd.setFirstSeen(detailsDocument.getFirstSeen());
          } else if (field.equals("running")) {
            dd.setRunning(detailsDocument.getRunning());
          } else if (field.equals("flags")) {
            dd.setFlags(detailsDocument.getFlags());
          } else if (field.equals("country")) {
            dd.setCountry(detailsDocument.getCountry());
          } else if (field.equals("country_name")) {
            dd.setCountryName(detailsDocument.getCountryName());
          } else if (field.equals("region_name")) {
            dd.setRegionName(detailsDocument.getRegionName());
          } else if (field.equals("city_name")) {
            dd.setCityName(detailsDocument.getCityName());
          } else if (field.equals("latitude")) {
            dd.setLatitude(detailsDocument.getLatitude());
          } else if (field.equals("longitude")) {
            dd.setLongitude(detailsDocument.getLongitude());
          } else if (field.equals("as_number")) {
            dd.setAsNumber(detailsDocument.getAsNumber());
          } else if (field.equals("as_name")) {
            dd.setAsName(detailsDocument.getAsName());
          } else if (field.equals("consensus_weight")) {
            dd.setConsensusWeight(detailsDocument.getConsensusWeight());
          } else if (field.equals("host_name")) {
            dd.setHostName(detailsDocument.getHostName());
          } else if (field.equals("last_restarted")) {
            dd.setLastRestarted(detailsDocument.getLastRestarted());
          } else if (field.equals("bandwidth_rate")) {
            dd.setBandwidthRate(detailsDocument.getBandwidthRate());
          } else if (field.equals("bandwidth_burst")) {
            dd.setBandwidthBurst(detailsDocument.getBandwidthBurst());
          } else if (field.equals("observed_bandwidth")) {
            dd.setObservedBandwidth(
                detailsDocument.getObservedBandwidth());
          } else if (field.equals("advertised_bandwidth")) {
            dd.setAdvertisedBandwidth(
                detailsDocument.getAdvertisedBandwidth());
          } else if (field.equals("exit_policy")) {
            dd.setExitPolicy(detailsDocument.getExitPolicy());
          } else if (field.equals("exit_policy_summary")) {
            dd.setExitPolicySummary(
                detailsDocument.getExitPolicySummary());
          } else if (field.equals("exit_policy_v6_summary")) {
            dd.setExitPolicyV6Summary(
                detailsDocument.getExitPolicyV6Summary());
          } else if (field.equals("contact")) {
            dd.setContact(detailsDocument.getContact());
          } else if (field.equals("platform")) {
            dd.setPlatform(detailsDocument.getPlatform());
          } else if (field.equals("family")) {
            dd.setFamily(detailsDocument.getFamily());
          } else if (field.equals("advertised_bandwidth_fraction")) {
            dd.setAdvertisedBandwidthFraction(
                detailsDocument.getAdvertisedBandwidthFraction());
          } else if (field.equals("consensus_weight_fraction")) {
            dd.setConsensusWeightFraction(
                detailsDocument.getConsensusWeightFraction());
          } else if (field.equals("guard_probability")) {
            dd.setGuardProbability(detailsDocument.getGuardProbability());
          } else if (field.equals("middle_probability")) {
            dd.setMiddleProbability(
                detailsDocument.getMiddleProbability());
          } else if (field.equals("exit_probability")) {
            dd.setExitProbability(detailsDocument.getExitProbability());
          } else if (field.equals("recommended_version")) {
            dd.setRecommendedVersion(
                detailsDocument.getRecommendedVersion());
          } else if (field.equals("hibernating")) {
            dd.setHibernating(detailsDocument.getHibernating());
          } else if (field.equals("pool_assignment")) {
            dd.setPoolAssignment(detailsDocument.getPoolAssignment());
          }
        }
        /* Don't escape HTML characters, like < and >, contained in
         * strings. */
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        /* Whenever we provide Gson with a string containing an escaped
         * non-ASCII character like \u00F2, it escapes the \ to \\, which
         * we need to undo before including the string in a response. */
        return gson.toJson(dd).replaceAll("\\\\\\\\u", "\\\\u");
      } else {
        // TODO We should probably log that we didn't find a details
        // document that we expected to exist.
        return "";
      }
    } else {
      DetailsDocument detailsDocument = documentStore.retrieve(
          DetailsDocument.class, false, fingerprint);
      if (detailsDocument != null) {
        return detailsDocument.getDocumentString();
      } else {
        // TODO We should probably log that we didn't find a details
        // document that we expected to exist.
        return "";
      }
    }
  }

  private String writeBandwidthLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    BandwidthDocument bandwidthDocument = this.documentStore.retrieve(
        BandwidthDocument.class, false, fingerprint);
    if (bandwidthDocument != null &&
        bandwidthDocument.getDocumentString() != null) {
      return bandwidthDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeWeightsLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    WeightsDocument weightsDocument = this.documentStore.retrieve(
        WeightsDocument.class, false, fingerprint);
    if (weightsDocument != null &&
        weightsDocument.getDocumentString() != null) {
      return weightsDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeClientsLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    ClientsDocument clientsDocument = this.documentStore.retrieve(
        ClientsDocument.class, false, fingerprint);
    if (clientsDocument != null &&
        clientsDocument.getDocumentString() != null) {
      return clientsDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }

  private String writeUptimeLines(SummaryDocument entry) {
    String fingerprint = entry.getFingerprint();
    UptimeDocument uptimeDocument = this.documentStore.retrieve(
        UptimeDocument.class, false, fingerprint);
    if (uptimeDocument != null &&
        uptimeDocument.getDocumentString() != null) {
      return uptimeDocument.getDocumentString();
    } else {
      return "{\"fingerprint\":\"" + fingerprint.toUpperCase() + "\"}";
    }
  }
}
