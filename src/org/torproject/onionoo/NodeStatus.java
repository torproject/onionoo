/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.TreeMap;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

/* Store search data of a single relay that was running in the past seven
 * days. */
public class NodeStatus extends Document {
  private boolean isRelay;
  private String fingerprint;
  private String hashedFingerprint;
  private String nickname;
  private String address;
  private SortedSet<String> orAddresses;
  private SortedSet<String> orAddressesAndPorts;
  private SortedSet<String> exitAddresses;
  private String latitude;
  private String longitude;
  private String countryCode;
  private String countryName;
  private String regionName;
  private String cityName;
  private String aSName;
  private String aSNumber;
  private long firstSeenMillis;
  private long lastSeenMillis;
  private int orPort;
  private int dirPort;
  private SortedSet<String> relayFlags;
  private long consensusWeight;
  private boolean running;
  private String hostName;
  private long lastRdnsLookup = -1L;
  private double advertisedBandwidthFraction = -1.0;
  private double consensusWeightFraction = -1.0;
  private double guardProbability = -1.0;
  private double middleProbability = -1.0;
  private double exitProbability = -1.0;
  private String defaultPolicy;
  private String portList;
  private SortedMap<Long, Set<String>> lastAddresses;
  private String contact;
  private Boolean recommendedVersion;
  public NodeStatus(boolean isRelay, String nickname, String fingerprint,
      String address, SortedSet<String> orAddressesAndPorts,
      SortedSet<String> exitAddresses, long lastSeenMillis, int orPort,
      int dirPort, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode, String hostName, long lastRdnsLookup,
      String defaultPolicy, String portList, long firstSeenMillis,
      long lastChangedAddresses, String aSNumber, String contact,
      Boolean recommendedVersion) {
    this.isRelay = isRelay;
    this.nickname = nickname;
    this.fingerprint = fingerprint;
    try {
      this.hashedFingerprint = DigestUtils.shaHex(Hex.decodeHex(
          this.fingerprint.toCharArray())).toUpperCase();
    } catch (DecoderException e) {
      throw new IllegalArgumentException("Fingerprint '" + fingerprint
          + "' is not a valid fingerprint.");
    }
    this.address = address;
    this.exitAddresses = new TreeSet<String>();
    if (exitAddresses != null) {
      this.exitAddresses.addAll(exitAddresses);
    }
    this.exitAddresses.remove(this.address);
    this.orAddresses = new TreeSet<String>();
    this.orAddressesAndPorts = new TreeSet<String>();
    if (orAddressesAndPorts != null) {
      for (String orAddressAndPort : orAddressesAndPorts) {
        this.addOrAddressAndPort(orAddressAndPort);
      }
    }
    this.lastSeenMillis = lastSeenMillis;
    this.orPort = orPort;
    this.dirPort = dirPort;
    this.relayFlags = relayFlags;
    this.consensusWeight = consensusWeight;
    this.countryCode = countryCode;
    this.hostName = hostName;
    this.lastRdnsLookup = lastRdnsLookup;
    this.defaultPolicy = defaultPolicy;
    this.portList = portList;
    this.firstSeenMillis = firstSeenMillis;
    this.lastAddresses =
        new TreeMap<Long, Set<String>>(Collections.reverseOrder());
    Set<String> addresses = new HashSet<String>();
    addresses.add(address + ":" + orPort);
    if (dirPort > 0) {
      addresses.add(address + ":" + dirPort);
    }
    addresses.addAll(orAddressesAndPorts);
    this.lastAddresses.put(lastChangedAddresses, addresses);
    this.aSNumber = aSNumber;
    this.contact = contact;
    this.recommendedVersion = recommendedVersion;
  }

  public static NodeStatus fromString(String documentString) {
    boolean isRelay = false;
    String nickname = null, fingerprint = null, address = null,
        countryCode = null, hostName = null, defaultPolicy = null,
        portList = null, aSNumber = null, contact = null;
    SortedSet<String> orAddressesAndPorts = null, exitAddresses = null,
        relayFlags = null;
    long lastSeenMillis = -1L, consensusWeight = -1L,
        lastRdnsLookup = -1L, firstSeenMillis = -1L,
        lastChangedAddresses = -1L;
    int orPort = -1, dirPort = -1;
    Boolean recommendedVersion = null;
    try {
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      String separator = documentString.contains("\t") ? "\t" : " ";
      String[] parts = documentString.trim().split(separator);
      isRelay = parts[0].equals("r");
      if (parts.length < 9) {
        System.err.println("Too few space-separated values in line '"
            + documentString.trim() + "'.  Skipping.");
        return null;
      }
      nickname = parts[1];
      fingerprint = parts[2];
      orAddressesAndPorts = new TreeSet<String>();
      exitAddresses = new TreeSet<String>();
      String addresses = parts[3];
      if (addresses.contains(";")) {
        String[] addressParts = addresses.split(";", -1);
        if (addressParts.length != 3) {
          System.err.println("Invalid addresses entry in line '"
              + documentString.trim() + "'.  Skipping.");
          return null;
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
      lastSeenMillis = dateTimeFormat.parse(parts[4] + " " + parts[5]).
          getTime();
      orPort = Integer.parseInt(parts[6]);
      dirPort = Integer.parseInt(parts[7]);
      relayFlags = new TreeSet<String>();
      if (parts[8].length() > 0) {
        relayFlags.addAll(Arrays.asList(parts[8].split(",")));
      }
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
      firstSeenMillis = lastSeenMillis;
      if (parts.length > 16) {
        firstSeenMillis = dateTimeFormat.parse(parts[15] + " "
            + parts[16]).getTime();
      }
      lastChangedAddresses = lastSeenMillis;
      if (parts.length > 18 && !parts[17].equals("null")) {
        lastChangedAddresses = dateTimeFormat.parse(parts[17] + " "
            + parts[18]).getTime();
      }
      if (parts.length > 19) {
        aSNumber = parts[19];
      }
      if (parts.length > 20) {
        contact = parts[20];
      }
      if (parts.length > 21) {
        recommendedVersion = parts[21].equals("null") ? null :
            parts[21].equals("true");
      }
    } catch (NumberFormatException e) {
      System.err.println("Number format exception while parsing node "
          + "status line '" + documentString + "': " + e.getMessage()
          + ".  Skipping.");
      return null;
    } catch (ParseException e) {
      System.err.println("Parse exception while parsing node status "
          + "line '" + documentString + "': " + e.getMessage() + ".  "
          + "Skipping.");
      return null;
    } catch (Exception e) {
      /* This catch block is only here to handle yet unknown errors.  It
       * should go away once we're sure what kind of errors can occur. */
      System.err.println("Unknown exception while parsing node status "
          + "line '" + documentString + "': " + e.getMessage() + ".  "
          + "Skipping.");
      return null;
    }
    NodeStatus newNodeStatus = new NodeStatus(isRelay, nickname,
        fingerprint, address, orAddressesAndPorts, exitAddresses,
        lastSeenMillis, orPort, dirPort, relayFlags, consensusWeight,
        countryCode, hostName, lastRdnsLookup, defaultPolicy, portList,
        firstSeenMillis, lastChangedAddresses, aSNumber, contact,
        recommendedVersion);
    return newNodeStatus;
  }

  public void update(NodeStatus newNodeStatus) {
    if (newNodeStatus.lastSeenMillis > this.lastSeenMillis) {
      this.nickname = newNodeStatus.nickname;
      this.address = newNodeStatus.address;
      this.orAddressesAndPorts = newNodeStatus.orAddressesAndPorts;
      this.exitAddresses = newNodeStatus.exitAddresses;
      this.lastSeenMillis = newNodeStatus.lastSeenMillis;
      this.orPort = newNodeStatus.orPort;
      this.dirPort = newNodeStatus.dirPort;
      this.relayFlags = newNodeStatus.relayFlags;
      this.consensusWeight = newNodeStatus.consensusWeight;
      this.countryCode = newNodeStatus.countryCode;
      this.defaultPolicy = newNodeStatus.defaultPolicy;
      this.portList = newNodeStatus.portList;
      this.aSNumber = newNodeStatus.aSNumber;
      this.contact = newNodeStatus.contact;
      this.recommendedVersion = newNodeStatus.recommendedVersion;
    }
    if (this.isRelay && newNodeStatus.isRelay) {
      this.lastAddresses.putAll(newNodeStatus.lastAddresses);
    }
    this.firstSeenMillis = Math.min(newNodeStatus.firstSeenMillis,
        this.getFirstSeenMillis());
  }

  public String toString() {
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd\tHH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    StringBuilder sb = new StringBuilder();
    sb.append(this.isRelay ? "r" : "b");
    sb.append("\t" + this.nickname);
    sb.append("\t" + this.fingerprint);
    sb.append("\t" + this.address + ";");
    int written = 0;
    for (String orAddressAndPort : this.orAddressesAndPorts) {
      sb.append((written++ > 0 ? "+" : "") + orAddressAndPort);
    }
    sb.append(";");
    if (this.isRelay) {
      written = 0;
      for (String exitAddress : this.exitAddresses) {
        sb.append((written++ > 0 ? "+" : "")
            + exitAddress);
      }
    }
    sb.append("\t" + dateTimeFormat.format(this.lastSeenMillis));
    sb.append("\t" + this.orPort);
    sb.append("\t" + this.dirPort + "\t");
    written = 0;
    for (String relayFlag : this.relayFlags) {
      sb.append((written++ > 0 ? "," : "") + relayFlag);
    }
    if (this.isRelay) {
      sb.append("\t" + String.valueOf(this.consensusWeight));
      sb.append("\t"
          + (this.countryCode != null ? this.countryCode : "??"));
      sb.append("\t" + (this.hostName != null ? this.hostName : "null"));
      sb.append("\t" + String.valueOf(this.lastRdnsLookup));
      sb.append("\t" + (this.defaultPolicy != null ? this.defaultPolicy
          : "null"));
      sb.append("\t" + (this.portList != null ? this.portList : "null"));
    } else {
      sb.append("\t-1\t??\tnull\t-1\tnull\tnull");
    }
    sb.append("\t" + dateTimeFormat.format(this.firstSeenMillis));
    if (this.isRelay) {
      sb.append("\t" + dateTimeFormat.format(
          this.getLastChangedOrAddress()));
      sb.append("\t" + (this.aSNumber != null ? this.aSNumber : "null"));
    } else {
      sb.append("\tnull\tnull\tnull");
    }
    sb.append("\t" + (this.contact != null ? this.contact : ""));
    sb.append("\t" + (this.recommendedVersion == null ? "null" :
        this.recommendedVersion ? "true" : "false"));
    return sb.toString();
  }

  public boolean isRelay() {
    return this.isRelay;
  }
  public String getFingerprint() {
    return this.fingerprint;
  }
  public String getHashedFingerprint() {
    return this.hashedFingerprint;
  }
  public String getNickname() {
    return this.nickname;
  }
  public String getAddress() {
    return this.address;
  }
  public SortedSet<String> getOrAddresses() {
    return new TreeSet<String>(this.orAddresses);
  }
  public void addOrAddressAndPort(String orAddressAndPort) {
    if (!orAddressAndPort.contains(":")) {
      System.err.println("Illegal OR address:port '" + orAddressAndPort
          + "'.  Exiting.");
      System.exit(1);
    } else if (orAddressAndPort.length() > 0) {
      String orAddress = orAddressAndPort.substring(0,
          orAddressAndPort.lastIndexOf(":"));
      if (this.exitAddresses.contains(orAddress)) {
        this.exitAddresses.remove(orAddress);
      }
      this.orAddresses.add(orAddress);
      this.orAddressesAndPorts.add(orAddressAndPort);
    }
  }
  public SortedSet<String> getOrAddressesAndPorts() {
    return new TreeSet<String>(this.orAddressesAndPorts);
  }
  public void addExitAddress(String exitAddress) {
    if (exitAddress.length() > 0 && !this.address.equals(exitAddress) &&
        !this.orAddresses.contains(exitAddress)) {
      this.exitAddresses.add(exitAddress);
    }
  }
  public SortedSet<String> getExitAddresses() {
    return new TreeSet<String>(this.exitAddresses);
  }
  public void setLatitude(String latitude) {
    this.latitude = latitude;
  }
  public String getLatitude() {
    return this.latitude;
  }
  public void setLongitude(String longitude) {
    this.longitude = longitude;
  }
  public String getLongitude() {
    return this.longitude;
  }
  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }
  public String getCountryCode() {
    return this.countryCode;
  }
  public void setCountryName(String countryName) {
    this.countryName = countryName;
  }
  public String getCountryName() {
    return this.countryName;
  }
  public void setRegionName(String regionName) {
    this.regionName = regionName;
  }
  public String getRegionName() {
    return this.regionName;
  }
  public void setCityName(String cityName) {
    this.cityName = cityName;
  }
  public String getCityName() {
    return this.cityName;
  }
  public void setASNumber(String aSNumber) {
    this.aSNumber = aSNumber;
  }
  public String getASNumber() {
    return this.aSNumber;
  }
  public void setASName(String aSName) {
    this.aSName = aSName;
  }
  public String getASName() {
    return this.aSName;
  }
  public long getFirstSeenMillis() {
    return this.firstSeenMillis;
  }
  public long getLastSeenMillis() {
    return this.lastSeenMillis;
  }
  public int getOrPort() {
    return this.orPort;
  }
  public int getDirPort() {
    return this.dirPort;
  }
  public SortedSet<String> getRelayFlags() {
    return this.relayFlags;
  }
  public long getConsensusWeight() {
    return this.consensusWeight;
  }
  public void setRunning(boolean running) {
    this.running = running;
  }
  public boolean getRunning() {
    return this.running;
  }
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }
  public String getHostName() {
    return this.hostName;
  }
  public void setLastRdnsLookup(long lastRdnsLookup) {
    this.lastRdnsLookup = lastRdnsLookup;
  }
  public long getLastRdnsLookup() {
    return this.lastRdnsLookup;
  }
  public void setAdvertisedBandwidthFraction(
      double advertisedBandwidthFraction) {
    this.advertisedBandwidthFraction = advertisedBandwidthFraction;
  }
  public double getAdvertisedBandwidthFraction() {
    return this.advertisedBandwidthFraction;
  }
  public void setConsensusWeightFraction(double consensusWeightFraction) {
    this.consensusWeightFraction = consensusWeightFraction;
  }
  public double getConsensusWeightFraction() {
    return this.consensusWeightFraction;
  }
  public void setGuardProbability(double guardProbability) {
    this.guardProbability = guardProbability;
  }
  public double getGuardProbability() {
    return this.guardProbability;
  }
  public void setMiddleProbability(double middleProbability) {
    this.middleProbability = middleProbability;
  }
  public double getMiddleProbability() {
    return this.middleProbability;
  }
  public void setExitProbability(double exitProbability) {
    this.exitProbability = exitProbability;
  }
  public double getExitProbability() {
    return this.exitProbability;
  }
  public String getDefaultPolicy() {
    return this.defaultPolicy;
  }
  public String getPortList() {
    return this.portList;
  }
  public SortedMap<Long, Set<String>> getLastAddresses() {
    return this.lastAddresses == null ? null :
        new TreeMap<Long, Set<String>>(this.lastAddresses);
  }
  public long getLastChangedOrAddress() {
    long lastChangedAddressesMillis = -1L;
    if (this.lastAddresses != null) {
      Set<String> lastAddresses = null;
      for (Map.Entry<Long, Set<String>> e : this.lastAddresses.entrySet()) {
        if (lastAddresses != null) {
          for (String address : e.getValue()) {
            if (!lastAddresses.contains(address)) {
              return lastChangedAddressesMillis;
            }
          }
        }
        lastChangedAddressesMillis = e.getKey();
        lastAddresses = e.getValue();
      }
    }
    return lastChangedAddressesMillis;
  }
  public void setContact(String contact) {
    if (contact == null) {
      this.contact = null;
    } else {
      contact = contact.toLowerCase();
      StringBuilder sb = new StringBuilder();
      for (char c : contact.toCharArray()) {
        if (c >= 32 && c < 127) {
          sb.append(c);
        } else {
          sb.append(" ");
        }
      }
      this.contact = sb.toString();
    }
  }
  public String getContact() {
    return this.contact;
  }
  public Boolean getRecommendedVersion() {
    return this.recommendedVersion;
  }
}

