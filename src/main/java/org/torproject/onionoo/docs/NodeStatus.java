/* Copyright 2011--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class NodeStatus extends Document {

  private static final Logger log = LoggerFactory.getLogger(
      NodeStatus.class);

  /* From most recently published server descriptor: */

  private String contact;

  /** Sets the contact to a lower-cased variant of the given string with
   * all non-printable characters outside of ASCII code 32 (space) to 126
   * (dash) replaced with spaces. */
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

  private String[] declaredFamily;

  public void setDeclaredFamily(SortedSet<String> declaredFamily) {
    this.declaredFamily = collectionToStringArray(declaredFamily);
  }

  public SortedSet<String> getDeclaredFamily() {
    return stringArrayToSortedSet(this.declaredFamily);
  }

  private static String[] collectionToStringArray(
      Collection<String> collection) {
    String[] stringArray = null;
    if (collection != null && !collection.isEmpty()) {
      stringArray = new String[collection.size()];
      int index = 0;
      for (String string : collection) {
        stringArray[index++] = string;
      }
    }
    return stringArray;
  }

  private SortedSet<String> stringArrayToSortedSet(String[] stringArray) {
    SortedSet<String> sortedSet = new TreeSet<>();
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
    return this.orAddressesAndPorts == null ? new TreeSet<String>()
        : this.orAddressesAndPorts;
  }

  /** Returns all addresses used for the onion-routing protocol which
   * includes the primary address and all additionally configured
   * onion-routing addresses. */
  public SortedSet<String> getOrAddresses() {
    SortedSet<String> orAddresses = new TreeSet<>();
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

  private static Map<String, Integer> relayFlagIndexes = new HashMap<>();

  private static Map<Integer, String> relayFlagStrings = new HashMap<>();

  private BitSet relayFlags;

  @SuppressWarnings("checkstyle:javadocmethod")
  public void setRelayFlags(SortedSet<String> relayFlags) {
    BitSet newRelayFlags = new BitSet(relayFlagIndexes.size());
    for (String relayFlag : relayFlags) {
      if (relayFlag.length() == 0) {
        /* Workaround to handle cases when nodes have no relay flags at
         * all.  The problem is that we cannot distinguish an empty relay
         * flags set from a set with a single flag being the empty string.
         * But given that the empty string is not a valid flag, we can
         * just skip flags being the empty string and return an empty
         * set below.  Without this workaround, we'd return a set with one
         * flag in it: "". */
        continue;
      }
      if (!relayFlagIndexes.containsKey(relayFlag)) {
        relayFlagStrings.put(relayFlagIndexes.size(), relayFlag);
        relayFlagIndexes.put(relayFlag, relayFlagIndexes.size());
      }
      newRelayFlags.set(relayFlagIndexes.get(relayFlag));
    }
    this.relayFlags = newRelayFlags;
  }

  @SuppressWarnings("checkstyle:javadocmethod")
  public SortedSet<String> getRelayFlags() {
    SortedSet<String> result = new TreeSet<>();
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
      new TreeMap<>(Collections.reverseOrder());

  public SortedMap<Long, Set<String>> getLastAddresses() {
    return new TreeMap<>(this.lastAddresses);
  }

  /** Adds addresses and ports together with the time in milliseconds
   * since the epoch when they were last seen to the history of last seen
   * addresses and ports. */
  public void addLastAddresses(long lastSeenMillis, String address,
      int orPort, int dirPort, SortedSet<String> orAddressesAndPorts) {
    Set<String> addressesAndPorts = new HashSet<>();
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

  /** Returns the time in milliseconds since the epoch when addresses or
   * ports were last changed. */
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

  private String version;

  public void setVersion(String version) {
    this.version = null == version ? null
        : version.substring(version.lastIndexOf(" ") + 1);
  }

  public String getVersion() {
    return this.version;
  }

  /* From exit lists: */

  private SortedSet<String> exitAddresses;

  public void setExitAddresses(SortedSet<String> exitAddresses) {
    this.exitAddresses = exitAddresses;
  }

  public SortedSet<String> getExitAddresses() {
    return new TreeSet<>(this.exitAddresses);
  }

  /* GeoIP lookup results: */

  private String countryCode;

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getCountryCode() {
    return this.countryCode;
  }

  private String asNumber;

  public void setAsNumber(String asNumber) {
    this.asNumber = asNumber;
  }

  public String getAsNumber() {
    return this.asNumber;
  }

  /* Reverse DNS lookup result */

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

  /* Computed effective and extended family and derived subsets alleged
   * and indirect family */

  private String[] effectiveFamily;

  public void setEffectiveFamily(SortedSet<String> effectiveFamily) {
    this.effectiveFamily = collectionToStringArray(effectiveFamily);
  }

  public SortedSet<String> getEffectiveFamily() {
    return stringArrayToSortedSet(this.effectiveFamily);
  }

  private String[] extendedFamily;

  public void setExtendedFamily(SortedSet<String> extendedFamily) {
    this.extendedFamily = collectionToStringArray(extendedFamily);
  }

  public SortedSet<String> getExtendedFamily() {
    return stringArrayToSortedSet(this.extendedFamily);
  }

  /** Returns the alleged family consisting of all relays in this relay's
   * declared family that are not in a mutual family relationship with
   * this relay. */
  public SortedSet<String> getAllegedFamily() {
    SortedSet<String> allegedFamily = new TreeSet<>(
        stringArrayToSortedSet(this.declaredFamily));
    allegedFamily.removeAll(stringArrayToSortedSet(this.effectiveFamily));
    return allegedFamily;
  }

  /** Returns the indirect family consisting of all relays that can be
   * reached via mutual family relationships except for those that can be
   * reached directly via such a relationship. */
  public SortedSet<String> getIndirectFamily() {
    SortedSet<String> indirectFamily = new TreeSet<>(
        stringArrayToSortedSet(this.extendedFamily));
    indirectFamily.removeAll(stringArrayToSortedSet(this.effectiveFamily));
    return indirectFamily;
  }

  /* Constructor and (de-)serialization methods: */

  /** Instantiates a new node status object from the given fingerprint. */
  public NodeStatus(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  /** Instantiates a new node status object from the given string that may
   * have been produced by {@link #toString()}. */
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
      SortedSet<String> orAddressesAndPorts = new TreeSet<>();
      SortedSet<String> exitAddresses = new TreeSet<>();
      String addresses = parts[3];
      String address = null;
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
      } else if (lastSeenMillis == 0L) {
        log.debug("Skipping node status with fingerprint {} that has so far "
            + "never been seen in a network status.", fingerprint);
        return null;
      }
      nodeStatus.setLastSeenMillis(lastSeenMillis);
      int orPort = Integer.parseInt(parts[6]);
      int dirPort = Integer.parseInt(parts[7]);
      nodeStatus.setOrPort(orPort);
      nodeStatus.setDirPort(dirPort);
      nodeStatus.setRelayFlags(new TreeSet<>(
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
      nodeStatus.setAsNumber(parts[19]);
      nodeStatus.setContact(parts[20]);
      if (!parts[21].equals("null")) {
        nodeStatus.setRecommendedVersion(parts[21].equals("true"));
      }
      if (!parts[22].equals("null")) {
        /* The relay's family is encoded in three ':'-separated groups:
         *  0. declared, not-mutually agreed family members,
         *  1. effective, mutually agreed family members, and
         *  2. indirect members that can be reached via others only.
         * Each group contains zero or more ';'-separated fingerprints. */
        String[] groups = parts[22].split(":", -1);
        SortedSet<String> allegedFamily = new TreeSet<>();
        SortedSet<String> effectiveFamily = new TreeSet<>();
        SortedSet<String> indirectFamily = new TreeSet<>();
        if (groups[0].length() > 0) {
          allegedFamily.addAll(Arrays.asList(groups[0].split(";")));
        }
        if (groups.length > 1 && groups[1].length() > 0) {
          effectiveFamily.addAll(Arrays.asList(groups[1].split(";")));
        }
        if (groups.length > 2 && groups[2].length() > 0) {
          indirectFamily.addAll(Arrays.asList(groups[2].split(";")));
        }
        SortedSet<String> declaredFamily = new TreeSet<>();
        declaredFamily.addAll(allegedFamily);
        declaredFamily.addAll(effectiveFamily);
        nodeStatus.setDeclaredFamily(declaredFamily);
        nodeStatus.setEffectiveFamily(effectiveFamily);
        SortedSet<String> extendedFamily = new TreeSet<>();
        extendedFamily.addAll(effectiveFamily);
        extendedFamily.addAll(indirectFamily);
        nodeStatus.setExtendedFamily(extendedFamily);
      }
      if (parts.length >= 24 && !parts[23].isEmpty()) {
        nodeStatus.setVersion(parts[23]);
      }
      if (parts.length >= 25 && !parts[24].isEmpty()) {
        nodeStatus.setHostName(parts[24]);
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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.isRelay ? "r" : "b");
    sb.append("\t").append(this.nickname);
    sb.append("\t").append(this.fingerprint);
    sb.append("\t").append(this.address).append(";");
    if (this.orAddressesAndPorts != null) {
      sb.append(StringUtils.join(this.orAddressesAndPorts, "+"));
    }
    sb.append(";");
    if (this.isRelay) {
      sb.append(StringUtils.join(this.exitAddresses, "+"));
    }
    sb.append("\t").append(DateTimeHelper.format(this.lastSeenMillis,
        DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
    sb.append("\t").append(this.orPort);
    sb.append("\t").append(this.dirPort).append("\t");
    sb.append(StringUtils.join(this.getRelayFlags(), ","));
    if (this.isRelay) {
      sb.append("\t").append(String.valueOf(this.consensusWeight));
      sb.append("\t")
          .append((this.countryCode != null ? this.countryCode : "??"));
      sb.append("\t"); /* formerly used for storing host names */
      sb.append("\t").append(String.valueOf(this.lastRdnsLookup));
      sb.append("\t").append((this.defaultPolicy != null
          ? this.defaultPolicy : "null"));
      sb.append("\t").append((this.portList != null ? this.portList : "null"));
    } else {
      sb.append("\t-1\t??\t\t-1\tnull\tnull");
    }
    sb.append("\t").append(DateTimeHelper.format(this.firstSeenMillis,
        DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
    if (this.isRelay) {
      sb.append("\t").append(DateTimeHelper.format(
          this.getLastChangedOrAddressOrPort(),
          DateTimeHelper.ISO_DATETIME_TAB_FORMAT));
      sb.append("\t").append((this.asNumber != null ? this.asNumber : "null"));
    } else {
      sb.append("\tnull\tnull\tnull");
    }
    sb.append("\t").append((this.contact != null ? this.contact : ""));
    sb.append("\t").append((this.recommendedVersion == null ? "null" :
        this.recommendedVersion ? "true" : "false"));
    sb.append("\t").append(StringUtils.join(this.getAllegedFamily(), ";"))
        .append(":")
        .append(StringUtils.join(this.getEffectiveFamily(), ";")).append(":")
        .append(StringUtils.join(this.getIndirectFamily(), ";"));
    sb.append("\t")
        .append((this.getVersion() != null ? this.getVersion() : ""));
    sb.append("\t")
        .append((this.getHostName() != null ? this.getHostName() : ""));
    return sb.toString();
  }
}

