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
import java.util.Iterator;
import java.util.Locale;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.BridgeNetworkStatus;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;

import com.maxmind.geoip.Location;
import com.maxmind.geoip.LookupService;
import com.maxmind.geoip.regionName;

/* Store relays and bridges that have been running in the past seven
 * days. */
public class CurrentNodes {

  private File internalRelaySearchDataFile =
      new File("status/summary.csv");

  /* Read the internal relay search data file from disk. */
  public void readRelaySearchDataFile() {
    if (this.internalRelaySearchDataFile.exists() &&
        !this.internalRelaySearchDataFile.isDirectory()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            this.internalRelaySearchDataFile));
        String line;
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            String[] parts = line.split(" ");
            if (parts.length < 9) {
              System.err.println("Line '" + line + "' in '"
                  + this.internalRelaySearchDataFile.getAbsolutePath()
                  + " is invalid.  Exiting.");
              System.exit(1);
            }
            String nickname = parts[1];
            String fingerprint = parts[2];
            String address = parts[3];
            long validAfterMillis = dateTimeFormat.parse(parts[4] + " "
               + parts[5]).getTime();
            int orPort = Integer.parseInt(parts[6]);
            int dirPort = Integer.parseInt(parts[7]);
            SortedSet<String> relayFlags = new TreeSet<String>(
                Arrays.asList(parts[8].split(",")));
            this.addRelay(nickname, fingerprint, address,
                validAfterMillis, orPort, dirPort, relayFlags);
          } else if (line.startsWith("b ")) {
            String[] parts = line.split(" ");
            if (parts.length < 9) {
              System.err.println("Line '" + line + "' in '"
                  + this.internalRelaySearchDataFile.getAbsolutePath()
                  + " is invalid.  Exiting.");
              System.exit(1);
            }
            String hashedFingerprint = parts[2];
            String address = parts[3];
            long publishedMillis = dateTimeFormat.parse(parts[4] + " "
                + parts[5]).getTime();
            int orPort = Integer.parseInt(parts[6]);
            int dirPort = Integer.parseInt(parts[7]);
            SortedSet<String> relayFlags = new TreeSet<String>(
                Arrays.asList(parts[8].split(",")));
            this.addBridge(hashedFingerprint, address, publishedMillis,
                orPort, dirPort, relayFlags);
          }
        }
        br.close();
      } catch (IOException e) {
        System.err.println("Could not read "
            + this.internalRelaySearchDataFile.getAbsolutePath()
            + ".  Exiting.");
        e.printStackTrace();
        System.exit(1);
      } catch (ParseException e) {
        System.err.println("Could not read "
            + this.internalRelaySearchDataFile.getAbsolutePath()
            + ".  Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }
  }

  /* Write the internal relay search data file to disk. */
  public void writeRelaySearchDataFile() {
    try {
      this.internalRelaySearchDataFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.internalRelaySearchDataFile));
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      for (Node entry : this.currentRelays.values()) {
        String nickname = entry.getNickname();
        String fingerprint = entry.getFingerprint();
        String address = entry.getAddress();
        String validAfter = dateTimeFormat.format(
            entry.getLastSeenMillis());
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder sb = new StringBuilder();
        for (String relayFlag : entry.getRelayFlags()) {
          sb.append("," + relayFlag);
        }
        String relayFlags = sb.toString().substring(1);
        bw.write("r " + nickname + " " + fingerprint + " " + address + " "
            + validAfter + " " + orPort + " " + dirPort + " " + relayFlags
            + "\n");
      }
      for (Node entry : this.currentBridges.values()) {
        String fingerprint = entry.getFingerprint();
        String published = dateTimeFormat.format(
            entry.getLastSeenMillis());
        String address = String.valueOf(entry.getAddress());
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder sb = new StringBuilder();
        for (String relayFlag : entry.getRelayFlags()) {
          sb.append("," + relayFlag);
        }
        String relayFlags = sb.toString().substring(1);
        bw.write("b Unnamed " + fingerprint + " " + address + " "
            + published + " " + orPort + " " + dirPort + " " + relayFlags
            + "\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write '"
          + this.internalRelaySearchDataFile.getAbsolutePath()
          + "' to disk.  Exiting.");
      e.printStackTrace();
      System.exit(1);
    }
  }

  private long lastValidAfterMillis = 0L;
  private long lastPublishedMillis = 0L;

  private long cutoff = System.currentTimeMillis()
      - 7L * 24L * 60L * 60L * 1000L;

  public void readRelayNetworkConsensuses() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/relay-descriptors/consensuses"));
    reader.setExcludeFiles(new File("status/relay-consensus-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof RelayNetworkStatusConsensus) {
            updateRelayNetworkStatusConsensus((RelayNetworkStatusConsensus)
                descriptor);
          }
        }
      }
    }
  }

  public void setRelayRunningBits() {
    if (this.lastValidAfterMillis > 0L) {
      for (Node entry : this.currentRelays.values()) {
        entry.setRunning(entry.getLastSeenMillis() ==
            this.lastValidAfterMillis);
      }
    }
  }

  private void updateRelayNetworkStatusConsensus(
      RelayNetworkStatusConsensus consensus) {
    long validAfterMillis = consensus.getValidAfterMillis();
    for (NetworkStatusEntry entry :
        consensus.getStatusEntries().values()) {
      String nickname = entry.getNickname();
      String fingerprint = entry.getFingerprint();
      String address = entry.getAddress();
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> relayFlags = entry.getFlags();
      this.addRelay(nickname, fingerprint, address, validAfterMillis,
          orPort, dirPort, relayFlags);
    }
  }

  public void addRelay(String nickname, String fingerprint,
      String address, long validAfterMillis, int orPort, int dirPort,
      SortedSet<String> relayFlags) {
    if (validAfterMillis >= cutoff &&
        (!this.currentRelays.containsKey(fingerprint) ||
        this.currentRelays.get(fingerprint).getLastSeenMillis() <
        validAfterMillis)) {
      Node entry = new Node(nickname, fingerprint, address,
          validAfterMillis, orPort, dirPort, relayFlags);
      this.currentRelays.put(fingerprint, entry);
      if (validAfterMillis > this.lastValidAfterMillis) {
        this.lastValidAfterMillis = validAfterMillis;
      }
    }
  }

  public void lookUpCountries() {
    File geoLiteCityDatFile = new File("GeoLiteCity.dat");
    if (!geoLiteCityDatFile.exists()) {
      System.err.println("No GeoLiteCity.dat file in /.");
      return;
    }
    try {
      LookupService ls = new LookupService(geoLiteCityDatFile,
          LookupService.GEOIP_MEMORY_CACHE);
      for (Node relay : currentRelays.values()) {
        Location location = ls.getLocation(relay.getAddress());
        if (location != null) {
          relay.setLatitude(String.format(Locale.US, "%.6f",
              location.latitude));
          relay.setLongitude(String.format(Locale.US, "%.6f",
              location.longitude));
          relay.setCountryCode(location.countryCode);
          relay.setCountryName(location.countryName);
          relay.setRegionName(regionName.regionNameByCode(
              location.countryCode, location.region));
          relay.setCityName(location.city);
        }
      }
      ls.close();
    } catch (IOException e) {
      System.err.println("Could not look up countries for relays.");
    }
  }

  public void lookUpASes() {
    File geoIPASNumDatFile = new File("GeoIPASNum.dat");
    if (!geoIPASNumDatFile.exists()) {
      System.err.println("No GeoIPASNum.dat file in /.");
      return;
    }
    try {
      LookupService ls = new LookupService(geoIPASNumDatFile);
      for (Node relay : currentRelays.values()) {
        String org = ls.getOrg(relay.getAddress());
        if (org != null && org.indexOf(" ") > 0 && org.startsWith("AS")) {
          relay.setASNumber(org.substring(0, org.indexOf(" ")));
          relay.setASName(org.substring(org.indexOf(" ") + 1));
        }
      }
      ls.close();
    } catch (IOException e) {
      System.err.println("Could not look up ASes for relays.");
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
      for (Node entry : this.currentBridges.values()) {
        entry.setRunning(entry.getLastSeenMillis() ==
            this.lastPublishedMillis);
      }
    }
  }

  private void updateBridgeNetworkStatus(BridgeNetworkStatus status) {
    long publishedMillis = status.getPublishedMillis();
    for (NetworkStatusEntry entry : status.getStatusEntries().values()) {
      String fingerprint = entry.getFingerprint();
      String address = entry.getAddress();
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      SortedSet<String> relayFlags = entry.getFlags();
      this.addBridge(fingerprint, address, publishedMillis, orPort,
         dirPort, relayFlags);
    }
  }

  public void addBridge(String fingerprint, String address,
      long publishedMillis, int orPort, int dirPort,
      SortedSet<String> relayFlags) {
    if (publishedMillis >= cutoff &&
        (!this.currentBridges.containsKey(fingerprint) ||
        this.currentBridges.get(fingerprint).getLastSeenMillis() <
        publishedMillis)) {
      Node entry = new Node("Unnamed", fingerprint, address,
          publishedMillis, orPort, dirPort, relayFlags);
      this.currentBridges.put(fingerprint, entry);
      if (publishedMillis > this.lastPublishedMillis) {
        this.lastPublishedMillis = publishedMillis;
      }
    }
  }

  private SortedMap<String, Node> currentRelays =
      new TreeMap<String, Node>();
  public SortedMap<String, Node> getCurrentRelays() {
    return new TreeMap<String, Node>(this.currentRelays);
  }

  private SortedMap<String, Node> currentBridges =
      new TreeMap<String, Node>();
  public SortedMap<String, Node> getCurrentBridges() {
    return new TreeMap<String, Node>(this.currentBridges);
  }

  public long getLastValidAfterMillis() {
    return this.lastValidAfterMillis;
  }

  public long getLastPublishedMillis() {
    return this.lastPublishedMillis;
  }
}

