/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang.StringEscapeUtils;

import org.torproject.descriptor.BridgePoolAssignment;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ExitList;
import org.torproject.descriptor.ExitListEntry;
import org.torproject.descriptor.ServerDescriptor;

/* Write updated detail data files to disk and delete files of relays or
 * bridges that fell out of the summary list.
 *
 * The parts of details files coming from server descriptors always come
 * from the last known descriptor of a relay or bridge, not from the
 * descriptor that was last referenced in a network status. */
public class DetailDataWriter {

  private SortedMap<String, Node> relays;
  public void setCurrentRelays(SortedMap<String, Node> currentRelays) {
    this.relays = currentRelays;
  }

  private SortedMap<String, Node> bridges;
  public void setCurrentBridges(SortedMap<String, Node> currentBridges) {
    this.bridges = currentBridges;
  }

  private static final long RDNS_LOOKUP_MAX_REQUEST_MILLIS = 10L * 1000L;
  private static final long RDNS_LOOKUP_MAX_DURATION_MILLIS = 5L * 60L
      * 1000L;
  private static final long RDNS_LOOKUP_MAX_AGE_MILLIS = 12L * 60L * 60L
      * 1000L;
  private static final int RDNS_LOOKUP_WORKERS_NUM = 5;
  private Set<String> rdnsLookupJobs;
  private Map<String, String> rdnsLookupResults;
  private long startedRdnsLookups;
  private List<RdnsLookupWorker> rdnsLookupWorkers;
  public void startReverseDomainNameLookups() {
    this.startedRdnsLookups = System.currentTimeMillis();
    this.rdnsLookupJobs = new HashSet<String>();
    for (Node relay : relays.values()) {
      if (relay.getLastRdnsLookup() < this.startedRdnsLookups
          - RDNS_LOOKUP_MAX_AGE_MILLIS) {
        this.rdnsLookupJobs.add(relay.getAddress());
      }
    }
    this.rdnsLookupResults = new HashMap<String, String>();
    this.rdnsLookupWorkers = new ArrayList<RdnsLookupWorker>();
    for (int i = 0; i < RDNS_LOOKUP_WORKERS_NUM; i++) {
      RdnsLookupWorker rdnsLookupWorker = new RdnsLookupWorker();
      this.rdnsLookupWorkers.add(rdnsLookupWorker);
      rdnsLookupWorker.setDaemon(true);
      rdnsLookupWorker.start();
    }
  }

  public void finishReverseDomainNameLookups() {
    for (RdnsLookupWorker rdnsLookupWorker : this.rdnsLookupWorkers) {
      try {
        rdnsLookupWorker.join();
      } catch (InterruptedException e) {
        /* This is not something that we can take care of.  Just leave the
         * worker thread alone. */
      }
    }
    synchronized (this.rdnsLookupResults) {
      for (Node relay : relays.values()) {
        if (this.rdnsLookupResults.containsKey(relay.getAddress())) {
          relay.setHostName(this.rdnsLookupResults.get(
              relay.getAddress()));
          relay.setLastRdnsLookup(this.startedRdnsLookups);
        }
      }
    }
  }

  private class RdnsLookupWorker extends Thread {
    public void run() {
      while (System.currentTimeMillis() - RDNS_LOOKUP_MAX_DURATION_MILLIS
          <= startedRdnsLookups) {
        String rdnsLookupJob = null;
        synchronized (rdnsLookupJobs) {
          for (String job : rdnsLookupJobs) {
            rdnsLookupJob = job;
            rdnsLookupJobs.remove(job);
            break;
          }
        }
        if (rdnsLookupJob == null) {
          break;
        }
        RdnsLookupRequest request = new RdnsLookupRequest(this,
            rdnsLookupJob);
        request.setDaemon(true);
        request.start();
        try {
          Thread.sleep(RDNS_LOOKUP_MAX_REQUEST_MILLIS);
        } catch (InterruptedException e) {
          /* Getting interrupted should be the default case. */
        }
        String hostName = request.getHostName();
        if (hostName != null) {
          synchronized (rdnsLookupResults) {
            rdnsLookupResults.put(rdnsLookupJob, hostName);
          }
        }
      }
    }
  }

  private class RdnsLookupRequest extends Thread {
    RdnsLookupWorker parent;
    String address, hostName;
    public RdnsLookupRequest(RdnsLookupWorker parent, String address) {
      this.parent = parent;
      this.address = address;
    }
    public void run() {
      try {
        String result = InetAddress.getByName(this.address).getHostName();
        synchronized (this) {
          this.hostName = result;
        }
      } catch (UnknownHostException e) {
        /* We'll try again the next time. */
      }
      this.parent.interrupt();
    }
    public synchronized String getHostName() {
      return hostName;
    }
  }

  private Map<String, ServerDescriptor> relayServerDescriptors =
      new HashMap<String, ServerDescriptor>();
  public void readRelayServerDescriptors() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File(
        "in/relay-descriptors/server-descriptors"));
    /* Don't remember which server descriptors we already parsed.  If we
     * parse a server descriptor now and first learn about the relay in a
     * later consensus, we'll never write the descriptor content anywhere.
     * The result would be details files containing no descriptor parts
     * until the relay publishes the next descriptor. */
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ServerDescriptor) {
            ServerDescriptor serverDescriptor =
                (ServerDescriptor) descriptor;
            String fingerprint = serverDescriptor.getFingerprint();
            if (!this.relayServerDescriptors.containsKey(fingerprint) ||
                this.relayServerDescriptors.get(fingerprint).
                getPublishedMillis()
                < serverDescriptor.getPublishedMillis()) {
              this.relayServerDescriptors.put(fingerprint,
                  serverDescriptor);
            }
          }
        }
      }
    }
  }

  public void calculatePathSelectionProbabilities(
      SortedMap<String, Integer> bandwidthWeights) {
    boolean consensusContainsBandwidthWeights = false;
    double wgg = 0.0, wgd = 0.0, wmg = 0.0, wmm = 0.0, wme = 0.0,
        wmd = 0.0, wee = 0.0, wed = 0.0;
    if (bandwidthWeights != null) {
      SortedSet<String> weightKeys = new TreeSet<String>(Arrays.asList(
          "Wgg,Wgd,Wmg,Wmm,Wme,Wmd,Wee,Wed".split(",")));
      weightKeys.removeAll(bandwidthWeights.keySet());
      if (weightKeys.isEmpty()) {
        consensusContainsBandwidthWeights = true;
        wgg = ((double) bandwidthWeights.get("Wgg")) / 10000.0;
        wgd = ((double) bandwidthWeights.get("Wgd")) / 10000.0;
        wmg = ((double) bandwidthWeights.get("Wmg")) / 10000.0;
        wmm = ((double) bandwidthWeights.get("Wmm")) / 10000.0;
        wme = ((double) bandwidthWeights.get("Wme")) / 10000.0;
        wmd = ((double) bandwidthWeights.get("Wmd")) / 10000.0;
        wee = ((double) bandwidthWeights.get("Wee")) / 10000.0;
        wed = ((double) bandwidthWeights.get("Wed")) / 10000.0;
      }
    }
    SortedMap<String, Double>
        advertisedBandwidths = new TreeMap<String, Double>(),
        consensusWeights = new TreeMap<String, Double>(),
        guardWeights = new TreeMap<String, Double>(),
        middleWeights = new TreeMap<String, Double>(),
        exitWeights = new TreeMap<String, Double>();
    double totalAdvertisedBandwidth = 0.0;
    double totalConsensusWeight = 0.0;
    double totalGuardWeight = 0.0;
    double totalMiddleWeight = 0.0;
    double totalExitWeight = 0.0;
    for (Map.Entry<String, Node> e : this.relays.entrySet()) {
      String fingerprint = e.getKey();
      Node relay = e.getValue();
      if (!relay.getRunning()) {
        continue;
      }
      boolean isExit = relay.getRelayFlags().contains("Exit") &&
          !relay.getRelayFlags().contains("BadExit");
      boolean isGuard = relay.getRelayFlags().contains("Guard");
      if (this.relayServerDescriptors.containsKey(fingerprint)) {
        ServerDescriptor serverDescriptor =
            this.relayServerDescriptors.get(fingerprint);
        double advertisedBandwidth = (double) Math.min(Math.min(
            serverDescriptor.getBandwidthBurst(),
            serverDescriptor.getBandwidthObserved()),
            serverDescriptor.getBandwidthRate());
        advertisedBandwidths.put(fingerprint, advertisedBandwidth);
        totalAdvertisedBandwidth += advertisedBandwidth;
      }
      double consensusWeight = (double) relay.getConsensusWeight();
      consensusWeights.put(fingerprint, consensusWeight);
      totalConsensusWeight += consensusWeight;
      if (consensusContainsBandwidthWeights) {
        double guardWeight = consensusWeight,
            middleWeight = consensusWeight,
            exitWeight = consensusWeight;
        if (isGuard && isExit) {
          guardWeight *= wgd;
          middleWeight *= wmd;
          exitWeight *= wed;
        } else if (isGuard) {
          guardWeight *= wgg;
          middleWeight *= wmg;
          exitWeight = 0.0;
        } else if (isExit) {
          guardWeight = 0.0;
          middleWeight *= wme;
          exitWeight *= wee;
        } else {
          guardWeight = 0.0;
          middleWeight *= wmm;
          exitWeight = 0.0;
        }
        guardWeights.put(fingerprint, guardWeight);
        middleWeights.put(fingerprint, middleWeight);
        exitWeights.put(fingerprint, exitWeight);
        totalGuardWeight += guardWeight;
        totalMiddleWeight += middleWeight;
        totalExitWeight += exitWeight;
      }
    }
    for (Map.Entry<String, Node> e : this.relays.entrySet()) {
      String fingerprint = e.getKey();
      Node relay = e.getValue();
      if (advertisedBandwidths.containsKey(fingerprint)) {
        relay.setAdvertisedBandwidthFraction(advertisedBandwidths.get(
            fingerprint) / totalAdvertisedBandwidth);
      }
      if (consensusWeights.containsKey(fingerprint)) {
        relay.setConsensusWeightFraction(consensusWeights.get(fingerprint)
            / totalConsensusWeight);
      }
      if (guardWeights.containsKey(fingerprint)) {
        relay.setGuardProbability(guardWeights.get(fingerprint)
            / totalGuardWeight);
      }
      if (middleWeights.containsKey(fingerprint)) {
        relay.setMiddleProbability(middleWeights.get(fingerprint)
            / totalMiddleWeight);
      }
      if (exitWeights.containsKey(fingerprint)) {
        relay.setExitProbability(exitWeights.get(fingerprint)
            / totalExitWeight);
      }
    }
  }

  private long now = System.currentTimeMillis();
  private Map<String, Set<ExitListEntry>> exitListEntries =
      new HashMap<String, Set<ExitListEntry>>();
  public void readExitLists() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File(
        "in/exit-lists"));
    reader.setExcludeFiles(new File("status/exit-list-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ExitList) {
            ExitList exitList = (ExitList) descriptor;
            for (ExitListEntry exitListEntry :
                exitList.getExitListEntries()) {
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
        }
      }
    }
  }

  private Map<String, ServerDescriptor> bridgeServerDescriptors =
      new HashMap<String, ServerDescriptor>();
  public void readBridgeServerDescriptors() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File(
        "in/bridge-descriptors/server-descriptors"));
    /* Don't remember which server descriptors we already parsed.  If we
     * parse a server descriptor now and first learn about the relay in a
     * later status, we'll never write the descriptor content anywhere.
     * The result would be details files containing no descriptor parts
     * until the bridge publishes the next descriptor. */
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ServerDescriptor) {
            ServerDescriptor serverDescriptor =
                (ServerDescriptor) descriptor;
            String fingerprint = serverDescriptor.getFingerprint();
            if (!this.bridgeServerDescriptors.containsKey(fingerprint) ||
                this.bridgeServerDescriptors.get(fingerprint).
                getPublishedMillis()
                < serverDescriptor.getPublishedMillis()) {
              this.bridgeServerDescriptors.put(fingerprint,
                  serverDescriptor);
            }
          }
        }
      }
    }
  }

  private Map<String, String> bridgePoolAssignments =
      new HashMap<String, String>();
  public void readBridgePoolAssignments() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/bridge-pool-assignments"));
    reader.setExcludeFiles(new File("status/bridge-poolassign-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof BridgePoolAssignment) {
            BridgePoolAssignment bridgePoolAssignment =
                (BridgePoolAssignment) descriptor;
            for (Map.Entry<String, String> e :
                bridgePoolAssignment.getEntries().entrySet()) {
              String fingerprint = e.getKey();
              String details = e.getValue();
              this.bridgePoolAssignments.put(fingerprint, details);
            }
          }
        }
      }
    }
  }

  public void writeDetailDataFiles() {
    SortedMap<String, File> remainingDetailsFiles =
        this.listAllDetailsFiles();
    remainingDetailsFiles = this.updateRelayDetailsFiles(
        remainingDetailsFiles);
    remainingDetailsFiles = this.updateBridgeDetailsFiles(
        remainingDetailsFiles);
    this.deleteDetailsFiles(remainingDetailsFiles);
  }

  private File detailsFileDirectory = new File("out/details");
  private SortedMap<String, File> listAllDetailsFiles() {
    SortedMap<String, File> result = new TreeMap<String, File>();
    if (detailsFileDirectory.exists() &&
        detailsFileDirectory.isDirectory()) {
      for (File file : detailsFileDirectory.listFiles()) {
        if (file.getName().length() == 40) {
          result.put(file.getName(), file);
        }
      }
    }
    return result;
  }

  private static String escapeJSON(String s) {
    return StringEscapeUtils.escapeJavaScript(s).replaceAll("\\\\'", "'");
  }

  private SortedMap<String, File> updateRelayDetailsFiles(
      SortedMap<String, File> remainingDetailsFiles) {
    SortedMap<String, File> result =
        new TreeMap<String, File>(remainingDetailsFiles);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (Map.Entry<String, Node> relay : this.relays.entrySet()) {
      String fingerprint = relay.getKey();

      /* Read details file for this relay if it exists. */
      String descriptorParts = null;
      long publishedMillis = -1L;
      if (result.containsKey(fingerprint)) {
        File detailsFile = result.remove(fingerprint);
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              detailsFile));
          String line;
          boolean copyDescriptorParts = false;
          StringBuilder sb = new StringBuilder();
          while ((line = br.readLine()) != null) {
            if (line.startsWith("\"desc_published\":")) {
              String published = line.substring(
                  "\"desc_published\":\"".length(),
                  "\"desc_published\":\"1970-01-01 00:00:00".length());
              publishedMillis = dateTimeFormat.parse(published).getTime();
              copyDescriptorParts = true;
            }
            if (copyDescriptorParts) {
              sb.append(line + "\n");
            }
          }
          br.close();
          if (sb.length() > 0) {
            descriptorParts = sb.toString();
          }
        } catch (IOException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        } catch (ParseException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        }
      }

      /* Generate new descriptor-specific part if we have a more recent
       * descriptor or if the part we read didn't contain a last_restarted
       * line. */
      if (this.relayServerDescriptors.containsKey(fingerprint) &&
          (this.relayServerDescriptors.get(fingerprint).
          getPublishedMillis() > publishedMillis)) {
        ServerDescriptor descriptor = this.relayServerDescriptors.get(
            fingerprint);
        StringBuilder sb = new StringBuilder();
        String publishedDateTime = dateTimeFormat.format(
            descriptor.getPublishedMillis());
        String lastRestartedString = dateTimeFormat.format(
            descriptor.getPublishedMillis()
            - descriptor.getUptime() * 1000L);
        int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
            Math.min(descriptor.getBandwidthBurst(),
            descriptor.getBandwidthObserved()));
        sb.append("\"desc_published\":\"" + publishedDateTime + "\",\n"
            + "\"last_restarted\":\"" + lastRestartedString + "\",\n"
            + "\"advertised_bandwidth\":" + advertisedBandwidth + ",\n"
            + "\"exit_policy\":[");
        int written = 0;
        for (String exitPolicyLine : descriptor.getExitPolicyLines()) {
          sb.append((written++ > 0 ? "," : "") + "\n  \"" + exitPolicyLine
              + "\"");
        }
        sb.append("\n]");
        if (descriptor.getContact() != null) {
          sb.append(",\n\"contact\":\""
              + escapeJSON(descriptor.getContact()) + "\"");
        }
        if (descriptor.getPlatform() != null) {
          sb.append(",\n\"platform\":\""
              + escapeJSON(descriptor.getPlatform()) + "\"");
        }
        if (descriptor.getFamilyEntries() != null) {
          sb.append(",\n\"family\":[");
          written = 0;
          for (String familyEntry : descriptor.getFamilyEntries()) {
            sb.append((written++ > 0 ? "," : "") + "\n  \"" + familyEntry
                + "\"");
          }
          sb.append("\n]");
        }
        sb.append("\n}\n");
        descriptorParts = sb.toString();
      }

      /* Generate network-status-specific part. */
      Node entry = relay.getValue();
      String nickname = entry.getNickname();
      String address = entry.getAddress();
      SortedSet<String> orAddresses = new TreeSet<String>(
          entry.getOrAddressesAndPorts());
      orAddresses.add(address + ":" + entry.getOrPort());
      StringBuilder orAddressesAndPortsBuilder = new StringBuilder();
      int addressesWritten = 0;
      for (String orAddress : orAddresses) {
        orAddressesAndPortsBuilder.append(
            (addressesWritten++ > 0 ? "," : "") + "\"" + orAddress
            + "\"");
      }
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
      sb.append(",\n\"running\":" + running + ",\n");
      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append("\"flags\":[");
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
          sb.append((written++ > 0 ? "," : "") + "\"" + exitAddress
              + "\"");
        }
        sb.append("]");
      }
      String statusParts = sb.toString();

      /* Write details file to disk. */
      File detailsFile = new File(detailsFileDirectory, fingerprint);
      try {
        detailsFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            detailsFile));
        bw.write(statusParts);
        if (descriptorParts != null) {
          bw.write(",\n" + descriptorParts);
        } else {
          bw.write("\n}\n");
        }
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not write details file '"
            + detailsFile.getAbsolutePath() + "'.  This file may now be "
            + "broken.  Ignoring.");
        e.printStackTrace();
      }
    }

    /* Return the files that we didn't update. */
    return result;
  }

  private SortedMap<String, File> updateBridgeDetailsFiles(
      SortedMap<String, File> remainingDetailsFiles) {
    SortedMap<String, File> result =
        new TreeMap<String, File>(remainingDetailsFiles);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (Map.Entry<String, Node> bridge : this.bridges.entrySet()) {
      String fingerprint = bridge.getKey();

      /* Read details file for this bridge if it exists. */
      String descriptorParts = null, bridgePoolAssignment = null;
      long publishedMillis = -1L;
      if (result.containsKey(fingerprint)) {
        File detailsFile = result.remove(fingerprint);
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              detailsFile));
          String line;
          boolean copyDescriptorParts = false;
          StringBuilder sb = new StringBuilder();
          while ((line = br.readLine()) != null) {
            if (line.startsWith("\"desc_published\":")) {
              String published = line.substring(
                  "\"desc_published\":\"".length(),
                  "\"desc_published\":\"1970-01-01 00:00:00".length());
              publishedMillis = dateTimeFormat.parse(published).getTime();
              copyDescriptorParts = true;
            } else if (line.startsWith("\"pool_assignment\":")) {
              bridgePoolAssignment = line;
              copyDescriptorParts = false;
            } else if (line.equals("}")) {
              copyDescriptorParts = false;
            }
            if (copyDescriptorParts) {
              sb.append(line + "\n");
            }
          }
          br.close();
          descriptorParts = sb.toString();
          if (descriptorParts.endsWith(",\n")) {
            descriptorParts = descriptorParts.substring(0,
                descriptorParts.length() - 2);
          } else if (descriptorParts.endsWith("\n")) {
            descriptorParts = descriptorParts.substring(0,
                descriptorParts.length() - 1);
          }
        } catch (IOException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        } catch (ParseException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        }
      }

      /* Generate new descriptor-specific part if we have a more recent
       * descriptor. */
      if (this.bridgeServerDescriptors.containsKey(fingerprint) &&
          this.bridgeServerDescriptors.get(fingerprint).
          getPublishedMillis() > publishedMillis) {
        ServerDescriptor descriptor = this.bridgeServerDescriptors.get(
            fingerprint);
        StringBuilder sb = new StringBuilder();
        String publishedDateTime = dateTimeFormat.format(
            descriptor.getPublishedMillis());
        String lastRestartedString = dateTimeFormat.format(
            descriptor.getPublishedMillis()
            - descriptor.getUptime() * 1000L);
        int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
            Math.min(descriptor.getBandwidthBurst(),
            descriptor.getBandwidthObserved()));
        sb.append("\"desc_published\":\"" + publishedDateTime + "\",\n"
            + "\"last_restarted\":\"" + lastRestartedString + "\",\n"
            + "\"advertised_bandwidth\":" + advertisedBandwidth + ",\n"
            + "\"platform\":\"" + escapeJSON(descriptor.getPlatform())
            + "\"");
        descriptorParts = sb.toString();
      }

      /* Look up bridge pool assignment. */
      if (this.bridgePoolAssignments.containsKey(fingerprint)) {
        bridgePoolAssignment = "\"pool_assignment\":\""
            + this.bridgePoolAssignments.get(fingerprint) + "\"";
      }

      /* Generate network-status-specific part. */
      Node entry = bridge.getValue();
      String nickname = entry.getNickname();
      String running = entry.getRunning() ? "true" : "false";
      String address = entry.getAddress();
      SortedSet<String> orAddresses = new TreeSet<String>(
          entry.getOrAddressesAndPorts());
      orAddresses.add(address + ":" + entry.getOrPort());
      StringBuilder orAddressesAndPortsBuilder = new StringBuilder();
      int addressesWritten = 0;
      for (String orAddress : orAddresses) {
        orAddressesAndPortsBuilder.append(
            (addressesWritten++ > 0 ? "," : "") + "\"" + orAddress
            + "\"");
      }
      StringBuilder sb = new StringBuilder();
      sb.append("{\"version\":1,\n"
          + "\"nickname\":\"" + nickname + "\",\n"
          + "\"hashed_fingerprint\":\"" + fingerprint + "\",\n"
          + "\"or_addresses\":[" + orAddressesAndPortsBuilder.toString()
          + "],\n"
          + "\"running\":" + running + ",");
      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append("\n\"flags\":[");
        int written = 0;
        for (String relayFlag : relayFlags) {
          sb.append((written++ > 0 ? "," : "") + "\"" + relayFlag + "\"");
        }
        sb.append("]");
      }

      /* Append descriptor and bridge pool assignment parts. */
      if (descriptorParts != null) {
        sb.append(",\n" + descriptorParts);
      }
      if (bridgePoolAssignment != null) {
        sb.append(",\n" + bridgePoolAssignment);
      }
      sb.append("\n}\n");
      String detailsLines = sb.toString();

      /* Write details file to disk. */
      File detailsFile = new File(detailsFileDirectory, fingerprint);
      try {
        detailsFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            detailsFile));
        bw.write(detailsLines);
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not write details file '"
            + detailsFile.getAbsolutePath() + "'.  This file may now be "
            + "broken.  Ignoring.");
        e.printStackTrace();
      }
    }

    /* Return the files that we didn't update. */
    return result;
  }

  private void deleteDetailsFiles(
      SortedMap<String, File> remainingDetailsFiles) {
    for (File detailsFile : remainingDetailsFiles.values()) {
      detailsFile.delete();
    }
  }
}

