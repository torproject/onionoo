/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

/* Store search data of a single relay that was running in the past seven
 * days. */
public class Node {
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
  private long lastSeenMillis;
  private int orPort;
  private int dirPort;
  private SortedSet<String> relayFlags;
  private long consensusWeight;
  private boolean running;
  public Node(String nickname, String fingerprint, String address,
      SortedSet<String> orAddressesAndPorts,
      SortedSet<String> exitAddresses, long lastSeenMillis, int orPort,
      int dirPort, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode) {
    this.nickname = nickname;
    this.fingerprint = fingerprint;
    try {
      this.hashedFingerprint = DigestUtils.shaHex(Hex.decodeHex(
          fingerprint.toCharArray())).toUpperCase();
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
}

