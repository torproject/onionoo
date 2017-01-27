/* Copyright 2011--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.torproject.onionoo.docs.BandwidthDocument;
import org.torproject.onionoo.docs.ClientsDocument;
import org.torproject.onionoo.docs.DetailsDocument;
import org.torproject.onionoo.docs.DetailsDocumentFields;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.SummaryDocument;
import org.torproject.onionoo.docs.UptimeDocument;
import org.torproject.onionoo.docs.WeightsDocument;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class ResponseBuilder {

  private DocumentStore documentStore;

  public ResponseBuilder() {
    this.documentStore = DocumentStoreFactory.getDocumentStore();
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

  private static final String PROTOCOL_VERSION = "3.2";

  private static final String NEXT_MAJOR_VERSION_SCHEDULED = "2017-02-27";

  private void writeRelays(List<SummaryDocument> relays, PrintWriter pw) {
    this.write(pw, "{\"version\":\"%s\",\n", PROTOCOL_VERSION);
    if (null != NEXT_MAJOR_VERSION_SCHEDULED) {
      this.write(pw, "\"next_major_version_scheduled\":\"%s\",\n",
          NEXT_MAJOR_VERSION_SCHEDULED);
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
    String nickname = !entry.getNickname().equals("Unnamed")
        ? entry.getNickname() : null;
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
    String nickname = !entry.getNickname().equals("Unnamed")
        ? entry.getNickname() : null;
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
          } else if (field.equals(DetailsDocumentFields.FIRST_SEEN)) {
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
          } else if (field.equals(DetailsDocumentFields.CONSENSUS_WEIGHT)) {
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
          } else if (field.equals("transports")) {
            dd.setTransports(detailsDocument.getTransports());
          } else if (field.equals("effective_family")) {
            dd.setEffectiveFamily(detailsDocument.getEffectiveFamily());
          } else if (field.equals("measured")) {
            dd.setMeasured(detailsDocument.getMeasured());
          } else if (field.equals("alleged_family")) {
            dd.setAllegedFamily(detailsDocument.getAllegedFamily());
          } else if (field.equals("indirect_family")) {
            dd.setIndirectFamily(detailsDocument.getIndirectFamily());
          }
        }
        /* Don't escape HTML characters, like < and >, contained in
         * strings. */
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        /* Whenever we provide Gson with a string containing an escaped
         * non-ASCII character like \u00F2, it escapes the \ to \\, which
         * we need to undo before including the string in a response. */
        return StringUtils.replace(gson.toJson(dd), "\\\\u", "\\u");
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

