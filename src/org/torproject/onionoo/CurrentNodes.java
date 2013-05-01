/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

/* Store relays and bridges that have been running in the past seven
 * days. */
public class CurrentNodes {

  /* Read the internal relay search data file from disk. */
  public void readRelaySearchDataFile(File summaryFile) {
    if (summaryFile.exists() && !summaryFile.isDirectory()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            summaryFile));
        String line;
        while ((line = br.readLine()) != null) {
          this.parseSummaryFileLine(line);
        }
        br.close();
      } catch (IOException e) {
        System.err.println("I/O error while reading "
            + summaryFile.getAbsolutePath() + ": " + e.getMessage()
            + ".  Ignoring.");
      }
    }
  }

  private void parseSummaryFileLine(String line) {
    boolean isRelay;
    String nickname, fingerprint, address, countryCode = "??",
        hostName = null, defaultPolicy = null, portList = null,
        aSNumber = null;
    SortedSet<String> orAddressesAndPorts, exitAddresses, relayFlags;
    long publishedOrValidAfterMillis, consensusWeight = -1L,
        lastRdnsLookup = -1L, firstSeenMillis, lastChangedAddresses;
    int orPort, dirPort;
    try {
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      String[] parts = line.split(" ");
      isRelay = parts[0].equals("r");
      if (parts.length < 9) {
        System.err.println("Too few space-separated values in line '"
            + line + "'.  Skipping.");
        return;
      }
      nickname = parts[1];
      fingerprint = parts[2];
      String addresses = parts[3];
      orAddressesAndPorts = new TreeSet<String>();
      exitAddresses = new TreeSet<String>();
      if (addresses.contains(";")) {
        String[] addressParts = addresses.split(";", -1);
        if (addressParts.length != 3) {
          System.err.println("Invalid addresses entry in line '" + line
              + "'.  Skipping.");
          return;
        }
        address = addressParts[0];
        if (addressParts[1].length() > 0) {
          orAddressesAndPorts.addAll(Arrays.asList(
              addressParts[1].split("\\+")));
        }
        if (addressParts[2].length() > 0) {
          exitAddresses.addAll(Arrays.asList(
              addressParts[2].split("\\+")));
        }
      } else {
        address = addresses;
      }
      publishedOrValidAfterMillis = dateTimeFormat.parse(
          parts[4] + " " + parts[5]).getTime();
      orPort = Integer.parseInt(parts[6]);
      dirPort = Integer.parseInt(parts[7]);
      relayFlags = new TreeSet<String>(
          Arrays.asList(parts[8].split(",")));
      if (parts.length > 9) {
        consensusWeight = Long.parseLong(parts[9]);
      }
      if (parts.length > 10) {
        countryCode = parts[10];
      }
      if (parts.length > 12) {
        hostName = parts[11].equals("null") ? null : parts[11];
        lastRdnsLookup = Long.parseLong(parts[12]);
      }
      if (parts.length > 14) {
        if (!parts[13].equals("null")) {
          defaultPolicy = parts[13];
        }
        if (!parts[14].equals("null")) {
          portList = parts[14];
        }
      }
      firstSeenMillis = publishedOrValidAfterMillis;
      if (parts.length > 16) {
        firstSeenMillis = dateTimeFormat.parse(parts[15] + " "
            + parts[16]).getTime();
      }
      lastChangedAddresses = publishedOrValidAfterMillis;
      if (parts.length > 18 && !parts[17].equals("null")) {
        lastChangedAddresses = dateTimeFormat.parse(parts[17] + " "
            + parts[18]).getTime();
      }
      if (parts.length > 19) {
        aSNumber = parts[19];
      }
    } catch (NumberFormatException e) {
      System.err.println("Number format exception while parsing line '"
          + line + "': " + e.getMessage() + ".  Skipping.");
      return;
    } catch (ParseException e) {
      System.err.println("Parse exception while parsing line '" + line
          + "': " + e.getMessage() + ".  Skipping.");
      return;
    } catch (Exception e) {
      /* This catch block is only here to handle yet unknown errors.  It
       * should go away once we're sure what kind of errors can occur. */
      System.err.println("Unknown exception while parsing line '" + line
          + "': " + e.getMessage() + ".  Skipping.");
      return;
    }
    if (isRelay) {
      this.addRelay(nickname, fingerprint, address,
          orAddressesAndPorts, exitAddresses,
          publishedOrValidAfterMillis, orPort, dirPort, relayFlags,
          consensusWeight, countryCode, hostName, lastRdnsLookup,
          defaultPolicy, portList, firstSeenMillis,
          lastChangedAddresses, aSNumber);
    } else {
      this.addBridge(nickname, fingerprint, address,
          orAddressesAndPorts, exitAddresses,
          publishedOrValidAfterMillis, orPort, dirPort, relayFlags,
          consensusWeight, countryCode, hostName, lastRdnsLookup,
          defaultPolicy, portList, firstSeenMillis,
          lastChangedAddresses, aSNumber);
    }
  }

  /* Write the internal relay search data file to disk. */
  public void writeRelaySearchDataFile(File summaryFile,
      boolean includeOldNodes) {
    try {
      summaryFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          summaryFile));
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      Collection<Node> relays = includeOldNodes
          ? this.knownRelays.values() : this.getCurrentRelays().values();
      for (Node entry : relays) {
        String nickname = entry.getNickname();
        String fingerprint = entry.getFingerprint();
        String address = entry.getAddress();
        StringBuilder addressesBuilder = new StringBuilder();
        addressesBuilder.append(address + ";");
        int written = 0;
        for (String orAddressAndPort : entry.getOrAddressesAndPorts()) {
          addressesBuilder.append((written++ > 0 ? "+" : "") +
              orAddressAndPort);
        }
        addressesBuilder.append(";");
        written = 0;
        for (String exitAddress : entry.getExitAddresses()) {
          addressesBuilder.append((written++ > 0 ? "+" : "")
              + exitAddress);
        }
        String lastSeen = dateTimeFormat.format(
            entry.getLastSeenMillis());
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder flagsBuilder = new StringBuilder();
        written = 0;
        for (String relayFlag : entry.getRelayFlags()) {
          flagsBuilder.append((written++ > 0 ? "," : "") + relayFlag);
        }
        String consensusWeight = String.valueOf(
            entry.getConsensusWeight());
        String countryCode = entry.getCountryCode() != null
            ? entry.getCountryCode() : "??";
        String hostName = entry.getHostName() != null
            ? entry.getHostName() : "null";
        long lastRdnsLookup = entry.getLastRdnsLookup();
        String defaultPolicy = entry.getDefaultPolicy() != null
            ? entry.getDefaultPolicy() : "null";
        String portList = entry.getPortList() != null
            ? entry.getPortList() : "null";
        String firstSeen = dateTimeFormat.format(
            entry.getFirstSeenMillis());
        String lastChangedAddresses = dateTimeFormat.format(
            entry.getLastChangedOrAddress());
        String aSNumber = entry.getASNumber() != null
            ? entry.getASNumber() : "null";
        bw.write("r " + nickname + " " + fingerprint + " "
            + addressesBuilder.toString() + " " + lastSeen + " "
            + orPort + " " + dirPort + " " + flagsBuilder.toString() + " "
            + consensusWeight + " " + countryCode + " " + hostName + " "
            + String.valueOf(lastRdnsLookup) + " " + defaultPolicy + " "
            + portList + " " + firstSeen + " " + lastChangedAddresses
            + " " + aSNumber + "\n");
      }
      Collection<Node> bridges = includeOldNodes
          ? this.knownBridges.values()
          : this.getCurrentBridges().values();
      for (Node entry : bridges) {
        String nickname = entry.getNickname();
        String fingerprint = entry.getFingerprint();
        String published = dateTimeFormat.format(
            entry.getLastSeenMillis());
        String address = entry.getAddress();
        StringBuilder addressesBuilder = new StringBuilder();
        addressesBuilder.append(address + ";");
        int written = 0;
        for (String orAddressAndPort : entry.getOrAddressesAndPorts()) {
          addressesBuilder.append((written++ > 0 ? "+" : "") +
              orAddressAndPort);
        }
        addressesBuilder.append(";");
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder flagsBuilder = new StringBuilder();
        written = 0;
        for (String relayFlag : entry.getRelayFlags()) {
          flagsBuilder.append((written++ > 0 ? "," : "") + relayFlag);
        }
        String firstSeen = dateTimeFormat.format(
            entry.getFirstSeenMillis());
        bw.write("b " + nickname + " " + fingerprint + " "
            + addressesBuilder.toString() + " " + published + " " + orPort
            + " " + dirPort + " " + flagsBuilder.toString()
            + " -1 ?? null -1 null null " + firstSeen + " null null "
            + "null\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write '"
          + summaryFile.getAbsolutePath() + "' to disk.  Exiting.");
      e.printStackTrace();
      System.exit(1);
    }
  }

  private long lastValidAfterMillis = 0L;
  private long lastPublishedMillis = 0L;

  public void readRelayNetworkConsensuses() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/relay-descriptors/consensuses"));
    reader.setExcludeFiles(new File("status/relay-consensus-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        System.out.println("Could not parse "
            + descriptorFile.getFileName());
        descriptorFile.getException().printStackTrace();
      }
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof RelayNetworkStatusConsensus) {
            updateRelayNetworkStatusConsensus(
                (RelayNetworkStatusConsensus) descriptor);
          }
        }
      }
    }
  }

  public void setRelayRunningBits() {
    if (this.lastValidAfterMillis > 0L) {
      for (Node entry : this.knownRelays.values()) {
        entry.setRunning(entry.getLastSeenMillis() ==
            this.lastValidAfterMillis);
      }
    }
  }

  SortedMap<String, Integer> lastBandwidthWeights = null;
  public SortedMap<String, Integer> getLastBandwidthWeights() {
    return this.lastBandwidthWeights;
  }
  private void updateRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    long validAfterMillis = consensus.getValidAfterMillis();
    for (NetworkStatusEntry entry :
        consensus.getStatusEntries().values()) {
      String nickname = entry.getNickname();
      String fingerprint = entry.getFingerprint();
      String address = entry.getAddress();
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>(
          entry.getOrAddresses());
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> relayFlags = entry.getFlags();
      long consensusWeight = entry.getBandwidth();
      String defaultPolicy = entry.getDefaultPolicy();
      String portList = entry.getPortList();
      this.addRelay(nickname, fingerprint, address, orAddressesAndPorts,
          null, validAfterMillis, orPort, dirPort, relayFlags,
          consensusWeight, null, null, -1L, defaultPolicy, portList,
          validAfterMillis, validAfterMillis, null);
    }
    if (this.lastValidAfterMillis == validAfterMillis) {
      this.lastBandwidthWeights = consensus.getBandwidthWeights();
    }
  }

  public void addRelay(String nickname, String fingerprint,
      String address, SortedSet<String> orAddressesAndPorts,
      SortedSet<String> exitAddresses, long lastSeenMillis, int orPort,
      int dirPort, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode, String hostName, long lastRdnsLookup,
      String defaultPolicy, String portList, long firstSeenMillis,
      long lastChangedAddresses, String aSNumber) {
    /* Remember addresses and OR/dir ports that the relay advertised at
     * the given time. */
    SortedMap<Long, Set<String>> lastAddresses =
        new TreeMap<Long, Set<String>>(Collections.reverseOrder());
    Set<String> addresses = new HashSet<String>();
    addresses.add(address + ":" + orPort);
    if (dirPort > 0) {
      addresses.add(address + ":" + dirPort);
    }
    addresses.addAll(orAddressesAndPorts);
    lastAddresses.put(lastChangedAddresses, addresses);
    /* See if there's already an entry for this relay. */
    if (this.knownRelays.containsKey(fingerprint)) {
      Node existingEntry = this.knownRelays.get(fingerprint);
      if (lastSeenMillis < existingEntry.getLastSeenMillis()) {
        /* Use latest information for nickname, current addresses, etc. */
        nickname = existingEntry.getNickname();
        address = existingEntry.getAddress();
        orAddressesAndPorts = existingEntry.getOrAddressesAndPorts();
        exitAddresses = existingEntry.getExitAddresses();
        lastSeenMillis = existingEntry.getLastSeenMillis();
        orPort = existingEntry.getOrPort();
        dirPort = existingEntry.getDirPort();
        relayFlags = existingEntry.getRelayFlags();
        consensusWeight = existingEntry.getConsensusWeight();
        countryCode = existingEntry.getCountryCode();
        defaultPolicy = existingEntry.getDefaultPolicy();
        portList = existingEntry.getPortList();
      }
      if (hostName == null &&
          existingEntry.getAddress().equals(address)) {
        /* Re-use reverse DNS lookup results if available. */
        hostName = existingEntry.getHostName();
        lastRdnsLookup = existingEntry.getLastRdnsLookup();
      }
      /* Update relay-history fields. */
      firstSeenMillis = Math.min(firstSeenMillis,
          existingEntry.getFirstSeenMillis());
      lastAddresses.putAll(existingEntry.getLastAddresses());
    }
    /* Add or update entry. */
    Node entry = new Node(nickname, fingerprint, address,
        orAddressesAndPorts, exitAddresses, lastSeenMillis, orPort,
        dirPort, relayFlags, consensusWeight, countryCode, hostName,
        lastRdnsLookup, defaultPolicy, portList, firstSeenMillis,
        lastAddresses, aSNumber);
    this.knownRelays.put(fingerprint, entry);
    /* If this entry comes from a new consensus, update our global last
     * valid-after time. */
    if (lastSeenMillis > this.lastValidAfterMillis) {
      this.lastValidAfterMillis = lastSeenMillis;
    }
  }

  public void lookUpCitiesAndASes() {

    /* Make sure we have all required .csv files. */
    File[] geoLiteCityBlocksCsvFiles = new File[] {
        new File("geoip/Manual-GeoLiteCity-Blocks.csv"),
        new File("geoip/Automatic-GeoLiteCity-Blocks.csv"),
        new File("geoip/GeoLiteCity-Blocks.csv")
    };
    File geoLiteCityBlocksCsvFile = null;
    for (File file : geoLiteCityBlocksCsvFiles) {
      if (file.exists()) {
        geoLiteCityBlocksCsvFile = file;
        break;
      }
    }
    if (geoLiteCityBlocksCsvFile == null) {
      System.err.println("No *GeoLiteCity-Blocks.csv file in geoip/.");
      return;
    }
    File geoLiteCityLocationCsvFile =
        new File("geoip/GeoLiteCity-Location.csv");
    if (!geoLiteCityLocationCsvFile.exists()) {
      System.err.println("No GeoLiteCity-Location.csv file in geoip/.");
      return;
    }
    File iso3166CsvFile = new File("geoip/iso3166.csv");
    if (!iso3166CsvFile.exists()) {
      System.err.println("No iso3166.csv file in geoip/.");
      return;
    }
    File regionCsvFile = new File("geoip/region.csv");
    if (!regionCsvFile.exists()) {
      System.err.println("No region.csv file in geoip/.");
      return;
    }
    File geoIPASNum2CsvFile = new File("geoip/GeoIPASNum2.csv");
    if (!geoIPASNum2CsvFile.exists()) {
      System.err.println("No GeoIPASNum2.csv file in geoip/.");
      return;
    }

    /* Obtain a map from relay IP address strings to numbers. */
    Map<String, Long> addressStringNumbers = new HashMap<String, Long>();
    Pattern ipv4Pattern = Pattern.compile("^[0-9\\.]{7,15}$");
    for (Node relay : this.knownRelays.values()) {
      String addressString = relay.getAddress();
      long addressNumber = -1L;
      if (ipv4Pattern.matcher(addressString).matches()) {
        String[] parts = addressString.split("\\.", 4);
        if (parts.length == 4) {
          addressNumber = 0L;
          for (int i = 0; i < 4; i++) {
            addressNumber *= 256L;
            int octetValue = -1;
            try {
              octetValue = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
            }
            if (octetValue < 0 || octetValue > 255) {
              addressNumber = -1L;
              break;
            }
            addressNumber += octetValue;
          }
        }
      }
      if (addressNumber >= 0L) {
        addressStringNumbers.put(addressString, addressNumber);
      }
    }
    if (addressStringNumbers.isEmpty()) {
      System.err.println("No relay IP addresses to resolve to cities or "
          + "ASN.");
      return;
    }

    /* Obtain a map from IP address numbers to blocks. */
    Map<Long, Long> addressNumberBlocks = new HashMap<Long, Long>();
    try {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      BufferedReader br = new BufferedReader(new FileReader(
          geoLiteCityBlocksCsvFile));
      String line;
      long previousStartIpNum = -1L;
      while ((line = br.readLine()) != null) {
        if (!line.startsWith("\"")) {
          continue;
        }
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          System.err.println("Illegal line '" + line + "' in "
              + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
        try {
          long startIpNum = Long.parseLong(parts[0]);
          if (startIpNum <= previousStartIpNum) {
            System.err.println("Line '" + line + "' not sorted in "
                + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
            br.close();
            return;
          }
          previousStartIpNum = startIpNum;
          while (firstAddressNumber < startIpNum &&
              firstAddressNumber != -1L) {
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          long endIpNum = Long.parseLong(parts[1]);
          while (firstAddressNumber <= endIpNum &&
              firstAddressNumber != -1L) {
            long blockNumber = Long.parseLong(parts[2]);
            addressNumberBlocks.put(firstAddressNumber, blockNumber);
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          if (firstAddressNumber == -1L) {
            break;
          }
        }
        catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
      return;
    }

    /* Obtain a map from relevant blocks to location lines. */
    Map<Long, String> blockLocations = new HashMap<Long, String>();
    try {
      Set<Long> blockNumbers = new HashSet<Long>(
          addressNumberBlocks.values());
      BufferedReader br = new BufferedReader(new FileReader(
          geoLiteCityLocationCsvFile));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("C") || line.startsWith("l")) {
          continue;
        }
        String[] parts = line.replaceAll("\"", "").split(",", 9);
        if (parts.length != 9) {
          System.err.println("Illegal line '" + line + "' in "
              + geoLiteCityLocationCsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
        try {
          long locId = Long.parseLong(parts[0]);
          if (blockNumbers.contains(locId)) {
            blockLocations.put(locId, line);
          }
        }
        catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoLiteCityLocationCsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoLiteCityLocationCsvFile.getAbsolutePath() + ".");
      return;
    }

    /* Read country names to memory. */
    Map<String, String> countryNames = new HashMap<String, String>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(
          iso3166CsvFile));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 2);
        if (parts.length != 2) {
          System.err.println("Illegal line '" + line + "' in "
              + iso3166CsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
        countryNames.put(parts[0].toLowerCase(), parts[1]);
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + iso3166CsvFile.getAbsolutePath() + ".");
      return;
    }

    /* Read region names to memory. */
    Map<String, String> regionNames = new HashMap<String, String>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(
          regionCsvFile));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          System.err.println("Illegal line '" + line + "' in "
              + regionCsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
        regionNames.put(parts[0].toLowerCase() + ","
            + parts[1].toLowerCase(), parts[2]);
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + regionCsvFile.getAbsolutePath() + ".");
      return;
    }

    /* Obtain a map from IP address numbers to ASN. */
    Map<Long, String> addressNumberASN = new HashMap<Long, String>();
    try {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      BufferedReader br = new BufferedReader(new FileReader(
          geoIPASNum2CsvFile));
      String line;
      long previousStartIpNum = -1L;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          System.err.println("Illegal line '" + line + "' in "
              + geoIPASNum2CsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
        try {
          long startIpNum = Long.parseLong(parts[0]);
          if (startIpNum <= previousStartIpNum) {
            System.err.println("Line '" + line + "' not sorted in "
                + geoIPASNum2CsvFile.getAbsolutePath() + ".");
            br.close();
            return;
          }
          previousStartIpNum = startIpNum;
          while (firstAddressNumber < startIpNum &&
              firstAddressNumber != -1L) {
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          long endIpNum = Long.parseLong(parts[1]);
          while (firstAddressNumber <= endIpNum &&
              firstAddressNumber != -1L) {
            if (parts[2].startsWith("AS") &&
                parts[2].split(" ", 2).length == 2) {
              addressNumberASN.put(firstAddressNumber, parts[2]);
            }
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          if (firstAddressNumber == -1L) {
            break;
          }
        }
        catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoIPASNum2CsvFile.getAbsolutePath() + ".");
          br.close();
          return;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoIPASNum2CsvFile.getAbsolutePath() + ".");
      return;
    }

    /* Finally, set relays' city and ASN information. */
    for (Node relay : knownRelays.values()) {
      String addressString = relay.getAddress();
      if (addressStringNumbers.containsKey(addressString)) {
        long addressNumber = addressStringNumbers.get(addressString);
        if (addressNumberBlocks.containsKey(addressNumber)) {
          long blockNumber = addressNumberBlocks.get(addressNumber);
          if (blockLocations.containsKey(blockNumber)) {
            String[] parts = blockLocations.get(blockNumber).
                replaceAll("\"", "").split(",", -1);
            String countryCode = parts[1].toLowerCase();
            relay.setCountryCode(countryCode);
            if (countryNames.containsKey(countryCode)) {
              relay.setCountryName(countryNames.get(countryCode));
            }
            String regionCode = countryCode + ","
                + parts[2].toLowerCase();
            if (regionNames.containsKey(regionCode)) {
              relay.setRegionName(regionNames.get(regionCode));
            }
            if (parts[3].length() > 0) {
              relay.setCityName(parts[3]);
            }
            relay.setLatitude(parts[5]);
            relay.setLongitude(parts[6]);
          }
        }
        if (addressNumberASN.containsKey(addressNumber)) {
          String[] parts = addressNumberASN.get(addressNumber).split(" ",
              2);
          relay.setASNumber(parts[0]);
          relay.setASName(parts[1]);
        }
      }
    }
  }

  public void readBridgeNetworkStatuses() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/bridge-descriptors/statuses"));
    reader.setExcludeFiles(new File("status/bridge-status-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        System.out.println("Could not parse "
            + descriptorFile.getFileName());
        descriptorFile.getException().printStackTrace();
      }
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof BridgeNetworkStatus) {
            updateBridgeNetworkStatus((BridgeNetworkStatus) descriptor);
          }
        }
      }
    }
  }

  public void setBridgeRunningBits() {
    if (this.lastPublishedMillis > 0L) {
      for (Node entry : this.knownBridges.values()) {
        entry.setRunning(entry.getRelayFlags().contains("Running") &&
            entry.getLastSeenMillis() == this.lastPublishedMillis);
      }
    }
  }

  private void updateBridgeNetworkStatus(BridgeNetworkStatus status) {
    long publishedMillis = status.getPublishedMillis();
    for (NetworkStatusEntry entry : status.getStatusEntries().values()) {
      String nickname = entry.getNickname();
      String fingerprint = entry.getFingerprint();
      String address = entry.getAddress();
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>(
          entry.getOrAddresses());
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> relayFlags = entry.getFlags();
      this.addBridge(nickname, fingerprint, address, orAddressesAndPorts,
          null, publishedMillis, orPort, dirPort, relayFlags, -1, "??",
          null, -1L, null, null, publishedMillis, -1L, null);
    }
  }

  public void addBridge(String nickname, String fingerprint,
      String address, SortedSet<String> orAddressesAndPorts,
      SortedSet<String> exitAddresses, long lastSeenMillis, int orPort,
      int dirPort, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode, String hostname, long lastRdnsLookup,
      String defaultPolicy, String portList, long firstSeenMillis,
      long lastChangedAddresses, String aSNumber) {
    /* See if there's already an entry for this bridge. */
    if (this.knownBridges.containsKey(fingerprint)) {
      Node existingEntry = this.knownBridges.get(fingerprint);
      if (lastSeenMillis < existingEntry.getLastSeenMillis()) {
        /* Use latest information for nickname, current addresses, etc. */
        nickname = existingEntry.getNickname();
        address = existingEntry.getAddress();
        orAddressesAndPorts = existingEntry.getOrAddressesAndPorts();
        exitAddresses = existingEntry.getExitAddresses();
        lastSeenMillis = existingEntry.getLastSeenMillis();
        orPort = existingEntry.getOrPort();
        dirPort = existingEntry.getDirPort();
        relayFlags = existingEntry.getRelayFlags();
        consensusWeight = existingEntry.getConsensusWeight();
        countryCode = existingEntry.getCountryCode();
        defaultPolicy = existingEntry.getDefaultPolicy();
        portList = existingEntry.getPortList();
        aSNumber = existingEntry.getASNumber();
      }
      /* Update relay-history fields. */
      firstSeenMillis = Math.min(firstSeenMillis,
          existingEntry.getFirstSeenMillis());
    }
    /* Add or update entry. */
    Node entry = new Node(nickname, fingerprint, address,
        orAddressesAndPorts, exitAddresses, lastSeenMillis, orPort,
        dirPort, relayFlags, consensusWeight, countryCode, hostname,
        lastRdnsLookup, defaultPolicy, portList, firstSeenMillis, null,
        aSNumber);
    this.knownBridges.put(fingerprint, entry);
    /* If this entry comes from a new status, update our global last
     * published time. */
    if (lastSeenMillis > this.lastPublishedMillis) {
      this.lastPublishedMillis = lastSeenMillis;
    }
  }

  private SortedMap<String, Node> knownRelays =
      new TreeMap<String, Node>();
  public SortedMap<String, Node> getCurrentRelays() {
    long cutoff = this.lastValidAfterMillis
        - 7L * 24L * 60L * 60L * 1000L;
    SortedMap<String, Node> currentRelays = new TreeMap<String, Node>();
    for (Map.Entry<String, Node> e : this.knownRelays.entrySet()) {
      if (e.getValue().getLastSeenMillis() >= cutoff) {
        currentRelays.put(e.getKey(), e.getValue());
      }
    }
    return currentRelays;
  }

  private SortedMap<String, Node> knownBridges =
      new TreeMap<String, Node>();
  public SortedMap<String, Node> getCurrentBridges() {
    long cutoff = this.lastPublishedMillis - 7L * 24L * 60L * 60L * 1000L;
    SortedMap<String, Node> currentBridges = new TreeMap<String, Node>();
    for (Map.Entry<String, Node> e : this.knownBridges.entrySet()) {
      if (e.getValue().getLastSeenMillis() >= cutoff) {
        currentBridges.put(e.getKey(), e.getValue());
      }
    }
    return currentBridges;
  }

  public long getLastValidAfterMillis() {
    return this.lastValidAfterMillis;
  }

  public long getLastPublishedMillis() {
    return this.lastPublishedMillis;
  }
}

