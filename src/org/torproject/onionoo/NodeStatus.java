/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

/* Store search data of a single relay that was running in the past seven
 * days. */
public class NodeStatus extends Document {

  private boolean isRelay;
  public boolean isRelay() {
    return this.isRelay;
  }

  private String fingerprint;
  public String getFingerprint() {
    return this.fingerprint;
  }

  private String hashedFingerprint;
  public String getHashedFingerprint() {
    return this.hashedFingerprint;
  }

  private String nickname;
  public String getNickname() {
    return this.nickname;
  }

  private String address;
  public String getAddress() {
    return this.address;
  }

  private SortedSet<String> orAddresses;
  private SortedSet<String> orAddressesAndPorts;
  public SortedSet<String> getOrAddresses() {
    return new TreeSet<String>(this.orAddresses);
  }
  public void addOrAddressAndPort(String orAddressAndPort) {
    if (orAddressAndPort.contains(":") && orAddressAndPort.length() > 0) {
      String orAddress = orAddressAndPort.substring(0,
          orAddressAndPort.lastIndexOf(':'));
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

  private SortedSet<String> exitAddresses;
  public void addExitAddress(String exitAddress) {
    if (exitAddress.length() > 0 && !this.address.equals(exitAddress) &&
        !this.orAddresses.contains(exitAddress)) {
      this.exitAddresses.add(exitAddress);
    }
  }
  public SortedSet<String> getExitAddresses() {
    return new TreeSet<String>(this.exitAddresses);
  }

  private Float latitude;
  public void setLatitude(Float latitude) {
    this.latitude = latitude;
  }
  public Float getLatitude() {
    return this.latitude;
  }

  private Float longitude;
  public void setLongitude(Float longitude) {
    this.longitude = longitude;
  }
  public Float getLongitude() {
    return this.longitude;
  }

  private String countryCode;
  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }
  public String getCountryCode() {
    return this.countryCode;
  }

  private String countryName;
  public void setCountryName(String countryName) {
    this.countryName = countryName;
  }
  public String getCountryName() {
    return this.countryName;
  }

  private String regionName;
  public void setRegionName(String regionName) {
    this.regionName = regionName;
  }
  public String getRegionName() {
    return this.regionName;
  }

  private String cityName;
  public void setCityName(String cityName) {
    this.cityName = cityName;
  }
  public String getCityName() {
    return this.cityName;
  }

  private String aSName;
  public void setASName(String aSName) {
    this.aSName = aSName;
  }
  public String getASName() {
    return this.aSName;
  }

  private String aSNumber;
  public void setASNumber(String aSNumber) {
    this.aSNumber = aSNumber;
  }
  public String getASNumber() {
    return this.aSNumber;
  }

  private long firstSeenMillis;
  public long getFirstSeenMillis() {
    return this.firstSeenMillis;
  }

  private long lastSeenMillis;
  public long getLastSeenMillis() {
    return this.lastSeenMillis;
  }

  private int orPort;
  public int getOrPort() {
    return this.orPort;
  }

  private int dirPort;
  public int getDirPort() {
    return this.dirPort;
  }

  private SortedSet<String> relayFlags;
  public SortedSet<String> getRelayFlags() {
    return this.relayFlags;
  }

  private long consensusWeight;
  public long getConsensusWeight() {
    return this.consensusWeight;
  }

  private boolean running;
  public void setRunning(boolean running) {
    this.running = running;
  }
  public boolean getRunning() {
    return this.running;
  }

  private String hostName;
  public void setHostName(String hostName) {
    this.hostName = hostName;
  }
  public String getHostName() {
    return this.hostName;
  }

  private long lastRdnsLookup = -1L;
  public void setLastRdnsLookup(long lastRdnsLookup) {
    this.lastRdnsLookup = lastRdnsLookup;
  }
  public long getLastRdnsLookup() {
    return this.lastRdnsLookup;
  }

  private double advertisedBandwidthFraction = -1.0;
  public void setAdvertisedBandwidthFraction(
      double advertisedBandwidthFraction) {
    this.advertisedBandwidthFraction = advertisedBandwidthFraction;
  }
  public double getAdvertisedBandwidthFraction() {
    return this.advertisedBandwidthFraction;
  }

  private double consensusWeightFraction = -1.0;
  public void setConsensusWeightFraction(double consensusWeightFraction) {
    this.consensusWeightFraction = consensusWeightFraction;
  }
  public double getConsensusWeightFraction() {
    return this.consensusWeightFraction;
  }

  private double guardProbability = -1.0;
  public void setGuardProbability(double guardProbability) {
    this.guardProbability = guardProbability;
  }
  public double getGuardProbability() {
    return this.guardProbability;
  }

  private double middleProbability = -1.0;
  public void setMiddleProbability(double middleProbability) {
    this.middleProbability = middleProbability;
  }
  public double getMiddleProbability() {
    return this.middleProbability;
  }

  private double exitProbability = -1.0;
  public void setExitProbability(double exitProbability) {
    this.exitProbability = exitProbability;
  }
  public double getExitProbability() {
    return this.exitProbability;
  }

  private String defaultPolicy;
  public String getDefaultPolicy() {
    return this.defaultPolicy;
  }

  private String portList;
  public String getPortList() {
    return this.portList;
  }

  private SortedMap<Long, Set<String>> lastAddresses;
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

  private String contact;
  public void setContact(String contact) {
    if (contact == null) {
      this.contact = null;
    } else {
      StringBuilder sb = new StringBuilder();
      for (char c : contact.toLowerCase().toCharArray()) {
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

  private Boolean recommendedVersion;
  public Boolean getRecommendedVersion() {
    return this.recommendedVersion;
  }

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
          + "' is not a valid fingerprint.", e);
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
      lastSeenMillis = DateTimeHelper.parse(parts[4] + " " + parts[5]);
      if (lastSeenMillis < 0L) {
        System.err.println("Parse exception while parsing node status "
            + "line '" + documentString + "'.  Skipping.");
        return null;
      }
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
        firstSeenMillis = DateTimeHelper.parse(parts[15] + " "
            + parts[16]);
        if (firstSeenMillis < 0L) {
          System.err.println("Parse exception while parsing node status "
              + "line '" + documentString + "'.  Skipping.");
          return null;
        }
      }
      lastChangedAddresses = lastSeenMillis;
      if (parts.length > 18 && !parts[17].equals("null")) {
        lastChangedAddresses = DateTimeHelper.parse(parts[17] + " "
            + parts[18]);
        if (lastChangedAddresses < 0L) {
          System.err.println("Parse exception while parsing node status "
              + "line '" + documentString + "'.  Skipping.");
          return null;
        }
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
      this.recommendedVersion = newNodeStatus.recommendedVersion;
    }
    if (this.isRelay && newNodeStatus.isRelay) {
      this.lastAddresses.putAll(newNodeStatus.lastAddresses);
    }
    this.firstSeenMillis = Math.min(newNodeStatus.firstSeenMillis,
        this.firstSeenMillis);
  }

  public String toString() {
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
    sb.append("\t" + DateTimeHelper.format(this.lastSeenMillis,
        DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
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
    sb.append("\t" + DateTimeHelper.format(this.firstSeenMillis,
        DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
    if (this.isRelay) {
      sb.append("\t" + DateTimeHelper.format(
          this.getLastChangedOrAddress(),
          DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
      sb.append("\t" + (this.aSNumber != null ? this.aSNumber : "null"));
    } else {
      sb.append("\tnull\tnull\tnull");
    }
    sb.append("\t" + (this.contact != null ? this.contact : ""));
    sb.append("\t" + (this.recommendedVersion == null ? "null" :
        this.recommendedVersion ? "true" : "false"));
    return sb.toString();
  }
}

