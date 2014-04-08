package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang.StringEscapeUtils;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;

public class DetailsDocumentWriter implements DescriptorListener,
    FingerprintListener, DocumentWriter {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public DetailsDocumentWriter(DescriptorSource descriptorSource,
      DocumentStore documentStore, Time time) {
    this.descriptorSource = descriptorSource;
    this.documentStore = documentStore;
    this.now = time.currentTimeMillis();
    this.registerDescriptorListeners();
    this.registerFingerprintListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.EXIT_LISTS);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof ExitList) {
      this.processExitList((ExitList) descriptor);
    }
  }

  private Map<String, Set<ExitListEntry>> exitListEntries =
      new HashMap<String, Set<ExitListEntry>>();

  /* TODO Processing descriptors should really be done in
   * NodeDetailsStatusUpdater, not here.  This is also a bug, because
   * we're only considering newly published exit lists. */
  private void processExitList(ExitList exitList) {
    for (ExitListEntry exitListEntry : exitList.getExitListEntries()) {
      if (exitListEntry.getScanMillis() <
          this.now - 24L * 60L * 60L * 1000L) {
        continue;
      }
      String fingerprint = exitListEntry.getFingerprint();
      if (!this.exitListEntries.containsKey(fingerprint)) {
        this.exitListEntries.put(fingerprint,
            new HashSet<ExitListEntry>());
      }
      this.exitListEntries.get(fingerprint).add(exitListEntry);
    }
  }

  private void registerFingerprintListeners() {
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.RELAY_SERVER_DESCRIPTORS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_STATUSES);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_SERVER_DESCRIPTORS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.BRIDGE_POOL_ASSIGNMENTS);
    this.descriptorSource.registerFingerprintListener(this,
        DescriptorType.EXIT_LISTS);
  }

  private SortedSet<String> newRelays = new TreeSet<String>(),
      newBridges = new TreeSet<String>();

  public void processFingerprints(SortedSet<String> fingerprints,
      boolean relay) {
    if (relay) {
      this.newRelays.addAll(fingerprints);
    } else {
      this.newBridges.addAll(fingerprints);
    }
  }

  public void writeDocuments() {
    this.updateRelayDetailsFiles();
    this.updateBridgeDetailsFiles();
    Logger.printStatusTime("Wrote details document files");
  }

  private void updateRelayDetailsFiles() {
    for (String fingerprint : this.newRelays) {

      /* Generate network-status-specific part. */
      NodeStatus entry = this.documentStore.retrieve(NodeStatus.class,
          true, fingerprint);
      if (entry == null) {
        continue;
      }
      String nickname = entry.getNickname();
      String address = entry.getAddress();
      List<String> orAddresses = new ArrayList<String>();
      orAddresses.add(address + ":" + entry.getOrPort());
      orAddresses.addAll(entry.getOrAddressesAndPorts());
      StringBuilder orAddressesAndPortsBuilder = new StringBuilder();
      int addressesWritten = 0;
      for (String orAddress : orAddresses) {
        orAddressesAndPortsBuilder.append(
            (addressesWritten++ > 0 ? "," : "") + "\""
            + orAddress.toLowerCase() + "\"");
      }
      String lastSeen = DateTimeHelper.format(entry.getLastSeenMillis());
      String firstSeen = DateTimeHelper.format(
          entry.getFirstSeenMillis());
      String lastChangedOrAddress = DateTimeHelper.format(
          entry.getLastChangedOrAddress());
      String running = entry.getRunning() ? "true" : "false";
      int dirPort = entry.getDirPort();
      String countryCode = entry.getCountryCode();
      String latitude = entry.getLatitude();
      String longitude = entry.getLongitude();
      String countryName = entry.getCountryName();
      String regionName = entry.getRegionName();
      String cityName = entry.getCityName();
      String aSNumber = entry.getASNumber();
      String aSName = entry.getASName();
      long consensusWeight = entry.getConsensusWeight();
      String hostName = entry.getHostName();
      double advertisedBandwidthFraction =
          entry.getAdvertisedBandwidthFraction();
      double consensusWeightFraction = entry.getConsensusWeightFraction();
      double guardProbability = entry.getGuardProbability();
      double middleProbability = entry.getMiddleProbability();
      double exitProbability = entry.getExitProbability();
      String defaultPolicy = entry.getDefaultPolicy();
      String portList = entry.getPortList();
      Boolean recommendedVersion = entry.getRecommendedVersion();
      StringBuilder sb = new StringBuilder();
      sb.append("{\"version\":1,\n"
          + "\"nickname\":\"" + nickname + "\",\n"
          + "\"fingerprint\":\"" + fingerprint + "\",\n"
          + "\"or_addresses\":[" + orAddressesAndPortsBuilder.toString()
          + "]");
      if (dirPort != 0) {
        sb.append(",\n\"dir_address\":\"" + address + ":" + dirPort
            + "\"");
      }
      sb.append(",\n\"last_seen\":\"" + lastSeen + "\"");
      sb.append(",\n\"first_seen\":\"" + firstSeen + "\"");
      sb.append(",\n\"last_changed_address_or_port\":\""
          + lastChangedOrAddress + "\"");
      sb.append(",\n\"running\":" + running);
      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append(",\n\"flags\":[");
        int written = 0;
        for (String relayFlag : relayFlags) {
          sb.append((written++ > 0 ? "," : "") + "\"" + relayFlag + "\"");
        }
        sb.append("]");
      }
      if (countryCode != null) {
        sb.append(",\n\"country\":\"" + countryCode + "\"");
      }
      if (latitude != null) {
        sb.append(",\n\"latitude\":" + latitude);
      }
      if (longitude != null) {
        sb.append(",\n\"longitude\":" + longitude);
      }
      if (countryName != null) {
        sb.append(",\n\"country_name\":\""
            + escapeJSON(countryName) + "\"");
      }
      if (regionName != null) {
        sb.append(",\n\"region_name\":\""
            + escapeJSON(regionName) + "\"");
      }
      if (cityName != null) {
        sb.append(",\n\"city_name\":\""
            + escapeJSON(cityName) + "\"");
      }
      if (aSNumber != null) {
        sb.append(",\n\"as_number\":\""
            + escapeJSON(aSNumber) + "\"");
      }
      if (aSName != null) {
        sb.append(",\n\"as_name\":\""
            + escapeJSON(aSName) + "\"");
      }
      if (consensusWeight >= 0L) {
        sb.append(",\n\"consensus_weight\":"
            + String.valueOf(consensusWeight));
      }
      if (hostName != null) {
        sb.append(",\n\"host_name\":\""
            + escapeJSON(hostName) + "\"");
      }
      if (advertisedBandwidthFraction >= 0.0) {
        sb.append(String.format(
            ",\n\"advertised_bandwidth_fraction\":%.9f",
            advertisedBandwidthFraction));
      }
      if (consensusWeightFraction >= 0.0) {
        sb.append(String.format(",\n\"consensus_weight_fraction\":%.9f",
            consensusWeightFraction));
      }
      if (guardProbability >= 0.0) {
        sb.append(String.format(",\n\"guard_probability\":%.9f",
            guardProbability));
      }
      if (middleProbability >= 0.0) {
        sb.append(String.format(",\n\"middle_probability\":%.9f",
            middleProbability));
      }
      if (exitProbability >= 0.0) {
        sb.append(String.format(",\n\"exit_probability\":%.9f",
            exitProbability));
      }
      if (defaultPolicy != null && (defaultPolicy.equals("accept") ||
          defaultPolicy.equals("reject")) && portList != null) {
        sb.append(",\n\"exit_policy_summary\":{\"" + defaultPolicy
            + "\":[");
        int portsWritten = 0;
        for (String portOrPortRange : portList.split(",")) {
          sb.append((portsWritten++ > 0 ? "," : "")
              + "\"" + portOrPortRange + "\"");
        }
        sb.append("]}");
      }
      if (recommendedVersion != null) {
        sb.append(",\n\"recommended_version\":" + (recommendedVersion ?
            "true" : "false"));
      }

      /* Add exit addresses if at least one of them is distinct from the
       * onion-routing addresses. */
      if (exitListEntries.containsKey(fingerprint)) {
        for (ExitListEntry exitListEntry :
            exitListEntries.get(fingerprint)) {
          entry.addExitAddress(exitListEntry.getExitAddress());
        }
      }
      if (!entry.getExitAddresses().isEmpty()) {
        sb.append(",\n\"exit_addresses\":[");
        int written = 0;
        for (String exitAddress : entry.getExitAddresses()) {
          sb.append((written++ > 0 ? "," : "") + "\""
              + exitAddress.toLowerCase() + "\"");
        }
        sb.append("]");
      }

      /* Append descriptor-specific part from details status file, and
       * update contact in node status. */
      /* TODO Updating the contact here seems like a pretty bad hack. */
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, false, fingerprint);
      if (detailsStatus != null &&
          detailsStatus.getDocumentString().length() > 0) {
        sb.append(",\n" + detailsStatus.getDocumentString());
        String contact = null;
        Scanner s = new Scanner(detailsStatus.getDocumentString());
        while (s.hasNextLine()) {
          String line = s.nextLine();
          if (!line.startsWith("\"contact\":")) {
            continue;
          }
          int start = "\"contact\":\"".length(), end = line.length() - 1;
          if (line.endsWith(",")) {
            end--;
          }
          contact = unescapeJSON(line.substring(start, end));
          break;
        }
        s.close();
        entry.setContact(contact);
      }

      /* Finish details string. */
      sb.append("\n}\n");

      /* Write details file to disk. */
      DetailsDocument detailsDocument = new DetailsDocument();
      detailsDocument.setDocumentString(sb.toString());
      this.documentStore.store(detailsDocument, fingerprint);
    }
  }

  private void updateBridgeDetailsFiles() {
    for (String fingerprint : this.newBridges) {

      /* Generate network-status-specific part. */
      NodeStatus entry = this.documentStore.retrieve(NodeStatus.class,
          true, fingerprint);
      if (entry == null) {
        continue;
      }
      String nickname = entry.getNickname();
      String lastSeen = DateTimeHelper.format(entry.getLastSeenMillis());
      String firstSeen = DateTimeHelper.format(
          entry.getFirstSeenMillis());
      String running = entry.getRunning() ? "true" : "false";
      String address = entry.getAddress();
      List<String> orAddresses = new ArrayList<String>();
      orAddresses.add(address + ":" + entry.getOrPort());
      orAddresses.addAll(entry.getOrAddressesAndPorts());
      StringBuilder orAddressesAndPortsBuilder = new StringBuilder();
      int addressesWritten = 0;
      for (String orAddress : orAddresses) {
        orAddressesAndPortsBuilder.append(
            (addressesWritten++ > 0 ? "," : "") + "\""
            + orAddress.toLowerCase() + "\"");
      }
      StringBuilder sb = new StringBuilder();
      sb.append("{\"version\":1,\n"
          + "\"nickname\":\"" + nickname + "\",\n"
          + "\"hashed_fingerprint\":\"" + fingerprint + "\",\n"
          + "\"or_addresses\":[" + orAddressesAndPortsBuilder.toString()
          + "],\n\"last_seen\":\"" + lastSeen + "\",\n\"first_seen\":\""
          + firstSeen + "\",\n\"running\":" + running);

      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append(",\n\"flags\":[");
        int written = 0;
        for (String relayFlag : relayFlags) {
          sb.append((written++ > 0 ? "," : "") + "\"" + relayFlag + "\"");
        }
        sb.append("]");
      }

      /* Append descriptor-specific part from details status file. */
      DetailsStatus detailsStatus = this.documentStore.retrieve(
          DetailsStatus.class, false, fingerprint);
      if (detailsStatus != null &&
          detailsStatus.getDocumentString().length() > 0) {
        sb.append(",\n" + detailsStatus.getDocumentString());
      }

      /* Finish details string. */
      sb.append("\n}\n");

      /* Write details file to disk. */
      DetailsDocument detailsDocument = new DetailsDocument();
      detailsDocument.setDocumentString(sb.toString());
      this.documentStore.store(detailsDocument, fingerprint);
    }
  }

  private static String escapeJSON(String s) {
    return StringEscapeUtils.escapeJavaScript(s).replaceAll("\\\\'", "'");
  }

  private static String unescapeJSON(String s) {
    return StringEscapeUtils.unescapeJavaScript(s.replaceAll("'", "\\'"));
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}
