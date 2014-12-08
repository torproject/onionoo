/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeStatus extends Document {

  private final static Logger log = LoggerFactory.getLogger(
      NodeStatus.class);

  /* From most recently published server descriptor: */

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

  private String[] familyFingerprints;
  public void setFamilyFingerprints(
      SortedSet<String> familyFingerprints) {
    this.familyFingerprints = collectionToStringArray(familyFingerprints);
  }
  public SortedSet<String> getFamilyFingerprints() {
    return stringArrayToSortedSet(this.familyFingerprints);
  }

  private static String[] collectionToStringArray(
      Collection<String> collection) {
    String[] stringArray = null;
    if (collection != null && !collection.isEmpty()) {
      stringArray = new String[collection.size()];
      int i = 0;
      for (String string : collection) {
        stringArray[i++] = string;
      }
    }
    return stringArray;
  }
  private SortedSet<String> stringArrayToSortedSet(String[] stringArray) {
    SortedSet<String> sortedSet = new TreeSet<String>();
    if (stringArray != null) {
      sortedSet.addAll(Arrays.asList(stringArray));
    }
    return sortedSet;
  }

  /* From network status entries: */

  private boolean isRelay;
  public void setRelay(boolean isRelay) {
    this.isRelay = isRelay;
  }
  public boolean isRelay() {
    return this.isRelay;
  }

  private final String fingerprint;
  public String getFingerprint() {
    return this.fingerprint;
  }

  private String nickname;
  public void setNickname(String nickname) {
    this.nickname = nickname;
  }
  public String getNickname() {
    return this.nickname;
  }

  private String address;
  public void setAddress(String address) {
    this.address = address;
  }
  public String getAddress() {
    return this.address;
  }

  private SortedSet<String> orAddressesAndPorts;
  public void setOrAddressesAndPorts(
      SortedSet<String> orAddressesAndPorts) {
    this.orAddressesAndPorts = orAddressesAndPorts;
  }
  public SortedSet<String> getOrAddressesAndPorts() {
    return this.orAddressesAndPorts == null ? new TreeSet<String>() :
        this.orAddressesAndPorts;
  }
  public SortedSet<String> getOrAddresses() {
    SortedSet<String> orAddresses = new TreeSet<String>();
    if (this.address != null) {
      orAddresses.add(this.address);
    }
    if (this.orAddressesAndPorts != null) {
      for (String orAddressAndPort : this.orAddressesAndPorts) {
        if (orAddressAndPort.contains(":")) {
          String orAddress = orAddressAndPort.substring(0,
              orAddressAndPort.lastIndexOf(':'));
          orAddresses.add(orAddress);
        }
      }
    }
    return orAddresses;
  }

  private long firstSeenMillis;
  public void setFirstSeenMillis(long firstSeenMillis) {
    this.firstSeenMillis = firstSeenMillis;
  }
  public long getFirstSeenMillis() {
    return this.firstSeenMillis;
  }

  private long lastSeenMillis;
  public void setLastSeenMillis(long lastSeenMillis) {
    this.lastSeenMillis = lastSeenMillis;
  }
  public long getLastSeenMillis() {
    return this.lastSeenMillis;
  }

  private int orPort;
  public void setOrPort(int orPort) {
    this.orPort = orPort;
  }
  public int getOrPort() {
    return this.orPort;
  }

  private int dirPort;
  public void setDirPort(int dirPort) {
    this.dirPort = dirPort;
  }
  public int getDirPort() {
    return this.dirPort;
  }

  private static Map<String, Integer> relayFlagIndexes =
      new HashMap<String, Integer>();
  private static Map<Integer, String> relayFlagStrings =
      new HashMap<Integer, String>();

  private BitSet relayFlags;
  public void setRelayFlags(SortedSet<String> relayFlags) {
    BitSet newRelayFlags = new BitSet(relayFlagIndexes.size());
    for (String relayFlag : relayFlags) {
      if (!relayFlagIndexes.containsKey(relayFlag)) {
        relayFlagStrings.put(relayFlagIndexes.size(), relayFlag);
        relayFlagIndexes.put(relayFlag, relayFlagIndexes.size());
      }
      newRelayFlags.set(relayFlagIndexes.get(relayFlag));
    }
    this.relayFlags = newRelayFlags;
  }
  public SortedSet<String> getRelayFlags() {
    SortedSet<String> result = new TreeSet<String>();
    if (this.relayFlags != null) {
      for (int i = this.relayFlags.nextSetBit(0); i >= 0;
          i = this.relayFlags.nextSetBit(i + 1)) {
        result.add(relayFlagStrings.get(i));
      }
    }
    return result;
  }

  private long consensusWeight;
  public void setConsensusWeight(long consensusWeight) {
    this.consensusWeight = consensusWeight;
  }
  public long getConsensusWeight() {
    return this.consensusWeight;
  }

  private String defaultPolicy;
  public void setDefaultPolicy(String defaultPolicy) {
    this.defaultPolicy = defaultPolicy;
  }
  public String getDefaultPolicy() {
    return this.defaultPolicy;
  }

  private String portList;
  public void setPortList(String portList) {
    this.portList = portList;
  }
  public String getPortList() {
    return this.portList;
  }

  private SortedMap<Long, Set<String>> lastAddresses =
      new TreeMap<Long, Set<String>>(Collections.reverseOrder());
  public SortedMap<Long, Set<String>> getLastAddresses() {
    return new TreeMap<Long, Set<String>>(this.lastAddresses);
  }
  public void addLastAddresses(long lastSeenMillis, String address,
      int orPort, int dirPort, SortedSet<String> orAddressesAndPorts) {
    Set<String> addressesAndPorts = new HashSet<String>();
    addressesAndPorts.add(address + ":" + orPort);
    if (dirPort > 0) {
      addressesAndPorts.add(address + ":" + dirPort);
    }
    addressesAndPorts.addAll(orAddressesAndPorts);
    if (this.lastAddresses.containsKey(lastSeenMillis)) {
      this.lastAddresses.get(lastSeenMillis).addAll(addressesAndPorts);
    } else {
      this.lastAddresses.put(lastSeenMillis, addressesAndPorts);
    }
  }
  public long getLastChangedOrAddressOrPort() {
    long lastChangedAddressesMillis = -1L;
    if (this.lastAddresses != null) {
      Set<String> lastAddresses = null;
      for (Map.Entry<Long, Set<String>> e :
          this.lastAddresses.entrySet()) {
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

  private Boolean recommendedVersion;
  public void setRecommendedVersion(Boolean recommendedVersion) {
    this.recommendedVersion = recommendedVersion;
  }
  public Boolean getRecommendedVersion() {
    return this.recommendedVersion;
  }

  /* From exit lists: */

  private SortedSet<String> exitAddresses;
  public void setExitAddresses(SortedSet<String> exitAddresses) {
    this.exitAddresses = exitAddresses;
  }
  public SortedSet<String> getExitAddresses() {
    return new TreeSet<String>(this.exitAddresses);
  }

  /* GeoIP lookup results: */

  private String countryCode;
  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }
  public String getCountryCode() {
    return this.countryCode;
  }

  private String aSNumber;
  public void setASNumber(String aSNumber) {
    this.aSNumber = aSNumber;
  }
  public String getASNumber() {
    return this.aSNumber;
  }

  /* Reverse DNS lookup result */

  private long lastRdnsLookup = -1L;
  public void setLastRdnsLookup(long lastRdnsLookup) {
    this.lastRdnsLookup = lastRdnsLookup;
  }
  public long getLastRdnsLookup() {
    return this.lastRdnsLookup;
  }

  /* Constructor and (de-)serialization methods: */

  public NodeStatus(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public static NodeStatus fromString(String documentString) {
    try {
      String[] parts = documentString.trim().split("\t");
      if (parts.length < 23) {
        log.error("Too few space-separated values in line '"
            + documentString.trim() + "'.  Skipping.");
        return null;
      }
      String fingerprint = parts[2];
      NodeStatus nodeStatus = new NodeStatus(fingerprint);
      nodeStatus.setRelay(parts[0].equals("r"));
      nodeStatus.setNickname(parts[1]);
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>();
      SortedSet<String> exitAddresses = new TreeSet<String>();
      String addresses = parts[3], address = null;
      if (addresses.contains(";")) {
        String[] addressParts = addresses.split(";", -1);
        if (addressParts.length != 3) {
          log.error("Invalid addresses entry in line '"
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
      nodeStatus.setAddress(address);
      nodeStatus.setOrAddressesAndPorts(orAddressesAndPorts);
      nodeStatus.setExitAddresses(exitAddresses);
      long lastSeenMillis = DateTimeHelper.parse(parts[4] + " "
          + parts[5]);
      if (lastSeenMillis < 0L) {
        log.error("Parse exception while parsing node status "
            + "line '" + documentString + "'.  Skipping.");
        return null;
      }
      nodeStatus.setLastSeenMillis(lastSeenMillis);
      int orPort = Integer.parseInt(parts[6]),
          dirPort = Integer.parseInt(parts[7]);
      nodeStatus.setOrPort(orPort);
      nodeStatus.setDirPort(dirPort);
      nodeStatus.setRelayFlags(new TreeSet<String>(
          Arrays.asList(parts[8].split(","))));
      nodeStatus.setConsensusWeight(Long.parseLong(parts[9]));
      nodeStatus.setCountryCode(parts[10]);
      if (!parts[11].equals("")) {
        /* This is a (possibly surprising) hack that is part of moving the
         * host name field from node status to details status.  As part of
         * that move we ignore all previously looked up host names trigger
         * a new lookup by setting the last lookup time to 1969-12-31
         * 23:59:59.999.  This hack may be removed after it has been run
         * at least once. */
        parts[12] = "-1";
      }
      nodeStatus.setLastRdnsLookup(Long.parseLong(parts[12]));
      if (!parts[13].equals("null")) {
        nodeStatus.setDefaultPolicy(parts[13]);
      }
      if (!parts[14].equals("null")) {
        nodeStatus.setPortList(parts[14]);
      }
      long firstSeenMillis = lastSeenMillis;
      firstSeenMillis = DateTimeHelper.parse(parts[15] + " " + parts[16]);
      if (firstSeenMillis < 0L) {
        log.error("Parse exception while parsing node status "
            + "line '" + documentString + "'.  Skipping.");
        return null;
      }
      nodeStatus.setFirstSeenMillis(firstSeenMillis);
      long lastChangedAddresses = lastSeenMillis;
      if (!parts[17].equals("null")) {
        lastChangedAddresses = DateTimeHelper.parse(parts[17] + " "
            + parts[18]);
        if (lastChangedAddresses < 0L) {
          log.error("Parse exception while parsing node status "
              + "line '" + documentString + "'.  Skipping.");
          return null;
        }
      }
      nodeStatus.addLastAddresses(lastChangedAddresses, address, orPort,
          dirPort, orAddressesAndPorts);
      nodeStatus.setASNumber(parts[19]);
      nodeStatus.setContact(parts[20]);
      if (!parts[21].equals("null")) {
        nodeStatus.setRecommendedVersion(parts[21].equals("true"));
      }
      if (!parts[22].equals("null")) {
        nodeStatus.setFamilyFingerprints(new TreeSet<String>(
            Arrays.asList(parts[22].split(";"))));
      }
      return nodeStatus;
    } catch (NumberFormatException e) {
      log.error("Number format exception while parsing node "
          + "status line '" + documentString + "': " + e.getMessage()
          + ".  Skipping.");
      return null;
    } catch (Exception e) {
      /* This catch block is only here to handle yet unknown errors.  It
       * should go away once we're sure what kind of errors can occur. */
      log.error("Unknown exception while parsing node status "
          + "line '" + documentString + "': " + e.getMessage() + ".  "
          + "Skipping.");
      return null;
    }
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.isRelay ? "r" : "b");
    sb.append("\t" + this.nickname);
    sb.append("\t" + this.fingerprint);
    sb.append("\t" + this.address + ";");
    int written = 0;
    if (this.orAddressesAndPorts != null) {
      for (String orAddressAndPort : this.orAddressesAndPorts) {
        sb.append((written++ > 0 ? "+" : "") + orAddressAndPort);
      }
    }
    sb.append(";");
    if (this.isRelay) {
      written = 0;
      for (String exitAddress : this.exitAddresses) {
        sb.append((written++ > 0 ? "+" : "") + exitAddress);
      }
    }
    sb.append("\t" + DateTimeHelper.format(this.lastSeenMillis,
        DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
    sb.append("\t" + this.orPort);
    sb.append("\t" + this.dirPort + "\t");
    written = 0;
    for (String relayFlag : this.getRelayFlags()) {
      sb.append((written++ > 0 ? "," : "") + relayFlag);
    }
    if (this.isRelay) {
      sb.append("\t" + String.valueOf(this.consensusWeight));
      sb.append("\t"
          + (this.countryCode != null ? this.countryCode : "??"));
      sb.append("\t"); /* formerly used for storing host names */
      sb.append("\t" + String.valueOf(this.lastRdnsLookup));
      sb.append("\t" + (this.defaultPolicy != null ? this.defaultPolicy
          : "null"));
      sb.append("\t" + (this.portList != null ? this.portList : "null"));
    } else {
      sb.append("\t-1\t??\t\t-1\tnull\tnull");
    }
    sb.append("\t" + DateTimeHelper.format(this.firstSeenMillis,
        DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
    if (this.isRelay) {
      sb.append("\t" + DateTimeHelper.format(
          this.getLastChangedOrAddressOrPort(),
          DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
      sb.append("\t" + (this.aSNumber != null ? this.aSNumber : "null"));
    } else {
      sb.append("\tnull\tnull\tnull");
    }
    sb.append("\t" + (this.contact != null ? this.contact : ""));
    sb.append("\t" + (this.recommendedVersion == null ? "null" :
        this.recommendedVersion ? "true" : "false"));
    if (this.familyFingerprints == null) {
      sb.append("\tnull");
    } else {
      sb.append("\t");
      written = 0;
      for (String familyFingerprint : this.familyFingerprints) {
        sb.append((written++ > 0 ? ";" : "") + familyFingerprint);
      }
    }
    return sb.toString();
  }
}

