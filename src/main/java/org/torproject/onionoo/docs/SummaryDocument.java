/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class SummaryDocument extends Document {

  @JsonProperty("t")
  private boolean isRelay;

  public void setRelay(boolean isRelay) {
    this.isRelay = isRelay;
  }

  public boolean isRelay() {
    return this.isRelay;
  }

  @JsonProperty("f")
  private String fingerprint;

  /** Sets the fingerprint to the given 40 hex characters and clears
   * SHA1-hashed and base64 fingerprints, so that they are re-computed at
   * next request. */
  public void setFingerprint(String fingerprint) {
    if (fingerprint != null) {
      Pattern fingerprintPattern = Pattern.compile("^[0-9a-fA-F]{40}$");
      if (!fingerprintPattern.matcher(fingerprint).matches()) {
        throw new IllegalArgumentException("Fingerprint '" + fingerprint
            + "' is not a valid fingerprint.");
      }
    }
    this.fingerprint = fingerprint;
    this.hashedFingerprint = null;
    this.base64Fingerprint = null;
    this.fingerprintSortedHexBlocks = null;
  }

  public String getFingerprint() {
    return this.fingerprint;
  }

  @JsonIgnore
  private transient String hashedFingerprint = null;

  /** Returns the SHA1-hashed fingerprint, or <code>null</code> if no
   * fingerprint is set. */
  public String getHashedFingerprint() {
    if (this.hashedFingerprint == null && this.fingerprint != null) {
      try {
        this.hashedFingerprint = DigestUtils.sha1Hex(Hex.decodeHex(
            this.fingerprint.toCharArray())).toUpperCase();
      } catch (DecoderException e) {
        /* Format tested in setFingerprint(). */
      }
    }
    return this.hashedFingerprint;
  }

  @JsonIgnore
  private transient String base64Fingerprint = null;

  /** Returns the base64-encoded fingerprint, or <code>null</code> if no
   * fingerprint is set. */
  public String getBase64Fingerprint() {
    if (this.base64Fingerprint == null && this.fingerprint != null) {
      try {
        this.base64Fingerprint = Base64.encodeBase64String(Hex.decodeHex(
            this.fingerprint.toCharArray())).replaceAll("=", "");
      } catch (DecoderException e) {
        /* Format tested in setFingerprint(). */
      }
    }
    return this.base64Fingerprint;
  }

  @JsonIgnore
  private transient String[] fingerprintSortedHexBlocks = null;

  /** Returns a sorted array containing blocks of 4 upper-case hex
   * characters from the fingerprint, or <code>null</code> if no
   * fingerprint is set. */
  public String[] getFingerprintSortedHexBlocks() {
    if (this.fingerprintSortedHexBlocks == null && this.fingerprint != null) {
      String fingerprint = this.fingerprint.toUpperCase();
      String[] fingerprintSortedHexBlocks =
          new String[fingerprint.length() / 4];
      for (int i = 0; i < fingerprint.length(); i += 4) {
        fingerprintSortedHexBlocks[i / 4] = fingerprint.substring(
            i, Math.min(i + 4, fingerprint.length()));
      }
      Arrays.sort(fingerprintSortedHexBlocks);
      this.fingerprintSortedHexBlocks = fingerprintSortedHexBlocks;
    }
    return this.fingerprintSortedHexBlocks;
  }

  @JsonProperty("n")
  private String nickname;

  @SuppressWarnings("checkstyle:javadocmethod")
  public void setNickname(String nickname) {
    if (nickname == null || nickname.equals("Unnamed")) {
      this.nickname = null;
    } else {
      this.nickname = nickname;
    }
  }

  public String getNickname() {
    return this.nickname == null ? "Unnamed" : this.nickname;
  }

  @JsonProperty("ad")
  private String[] addresses;

  public void setAddresses(List<String> addresses) {
    this.addresses = this.collectionToStringArray(addresses);
  }

  public List<String> getAddresses() {
    return this.stringArrayToList(this.addresses);
  }

  private String[] collectionToStringArray(
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

  private List<String> stringArrayToList(String[] stringArray) {
    List<String> list;
    if (stringArray == null) {
      list = new ArrayList<>();
    } else {
      list = Arrays.asList(stringArray);
    }
    return list;
  }

  private SortedSet<String> stringArrayToSortedSet(String[] stringArray) {
    SortedSet<String> sortedSet = new TreeSet<>();
    if (stringArray != null) {
      sortedSet.addAll(Arrays.asList(stringArray));
    }
    return sortedSet;
  }

  @JsonProperty("cc")
  private String countryCode;

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getCountryCode() {
    return this.countryCode;
  }

  @JsonProperty("as")
  private String asNumber;

  public void setAsNumber(String asNumber) {
    this.asNumber = asNumber;
  }

  public String getAsNumber() {
    return this.asNumber;
  }

  @JsonProperty("fs")
  private String firstSeenMillis;

  public void setFirstSeenMillis(long firstSeenMillis) {
    this.firstSeenMillis = DateTimeHelper.format(firstSeenMillis);
  }

  public long getFirstSeenMillis() {
    return DateTimeHelper.parse(this.firstSeenMillis);
  }

  @JsonProperty("ls")
  private String lastSeenMillis;

  public void setLastSeenMillis(long lastSeenMillis) {
    this.lastSeenMillis = DateTimeHelper.format(lastSeenMillis);
  }

  public long getLastSeenMillis() {
    return DateTimeHelper.parse(this.lastSeenMillis);
  }

  @JsonProperty("rf")
  private String[] relayFlags;

  public void setRelayFlags(SortedSet<String> relayFlags) {
    this.relayFlags = this.collectionToStringArray(relayFlags);
  }

  public SortedSet<String> getRelayFlags() {
    return this.stringArrayToSortedSet(this.relayFlags);
  }

  @JsonProperty("cw")
  private long consensusWeight;

  public void setConsensusWeight(long consensusWeight) {
    this.consensusWeight = consensusWeight;
  }

  public long getConsensusWeight() {
    return this.consensusWeight;
  }

  @JsonProperty("r")
  private boolean running;

  public void setRunning(boolean isRunning) {
    this.running = isRunning;
  }

  public boolean isRunning() {
    return this.running;
  }

  @JsonProperty("c")
  private String contact;

  @SuppressWarnings("checkstyle:javadocmethod")
  public void setContact(String contact) {
    if (contact != null && contact.length() == 0) {
      this.contact = null;
    } else {
      this.contact = contact;
    }
  }

  public String getContact() {
    return this.contact;
  }

  /* This attribute can go away once all Onionoo services had their hourly
   * updater write effective families to summary documents at least once.
   * Remove this code after September 8, 2015. */
  @JsonProperty("ff")
  private String[] familyFingerprints;

  public void setFamilyFingerprints(
      SortedSet<String> familyFingerprints) {
    this.familyFingerprints = this.collectionToStringArray(familyFingerprints);
  }

  public SortedSet<String> getFamilyFingerprints() {
    return this.stringArrayToSortedSet(this.familyFingerprints);
  }

  @JsonProperty("ef")
  private String[] effectiveFamily;

  public void setEffectiveFamily(SortedSet<String> effectiveFamily) {
    this.effectiveFamily = this.collectionToStringArray(effectiveFamily);
  }

  public SortedSet<String> getEffectiveFamily() {
    return this.stringArrayToSortedSet(this.effectiveFamily);
  }

  @JsonProperty("v")
  private String version;

  public void setVersion(String version) {
    this.version = version;
  }

  public String getVersion() {
    return this.version;
  }

  @JsonProperty("o")
  private String operatingSystem;

  public void setOperatingSystem(String operatingSystem) {
    this.operatingSystem = operatingSystem;
  }

  public String getOperatingSystem() {
    return this.operatingSystem;
  }

  @JsonProperty("h")
  private String hostName;

  public void setHostName(String hostName) {
    this.hostName = hostName;
  }

  public String getHostName() {
    return this.hostName;
  }

  @JsonProperty("vh")
  private List<String> verifiedHostNames;

  public void setVerifiedHostNames(List<String> verifiedHostNames) {
    this.verifiedHostNames = verifiedHostNames;
  }

  public List<String> getVerifiedHostNames() {
    return this.verifiedHostNames;
  }

  @JsonProperty("uh")
  private List<String> unverifiedHostNames;

  public void setUnverifiedHostNames(List<String> unverifiedHostNames) {
    this.unverifiedHostNames = unverifiedHostNames;
  }

  public List<String> getUnverifiedHostNames() {
    return this.unverifiedHostNames;
  }

  @JsonProperty("rv")
  private Boolean recommendedVersion;

  public void setRecommendedVersion(Boolean recommendedVersion) {
    this.recommendedVersion = recommendedVersion;
  }

  public Boolean getRecommendedVersion() {
    return this.recommendedVersion;
  }

  /** Instantiate an empty summary document. */
  public SummaryDocument() {
    /* empty */
  }

  /* The familyFingerprints parameter can go away after September 8, 2015.
   * See above. */
  /** Instantiates a summary document with all given properties. */
  public SummaryDocument(boolean isRelay, String nickname,
      String fingerprint, List<String> addresses, long lastSeenMillis,
      boolean running, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode, long firstSeenMillis, String asNumber,
      String contact, SortedSet<String> familyFingerprints,
      SortedSet<String> effectiveFamily, String version, String operatingSystem,
      String hostName, List<String> verifiedHostNames,
      List<String> unverifiedHostNames, Boolean recommendedVersion) {
    this.setRelay(isRelay);
    this.setNickname(nickname);
    this.setFingerprint(fingerprint);
    this.setAddresses(addresses);
    this.setLastSeenMillis(lastSeenMillis);
    this.setRunning(running);
    this.setRelayFlags(relayFlags);
    this.setConsensusWeight(consensusWeight);
    this.setCountryCode(countryCode);
    this.setFirstSeenMillis(firstSeenMillis);
    this.setAsNumber(asNumber);
    this.setContact(contact);
    this.setFamilyFingerprints(familyFingerprints);
    this.setEffectiveFamily(effectiveFamily);
    this.setVersion(version);
    this.setOperatingSystem(operatingSystem);
    this.setHostName(hostName);
    this.setVerifiedHostNames(verifiedHostNames);
    this.setUnverifiedHostNames(unverifiedHostNames);
    this.setRecommendedVersion(recommendedVersion);
  }
}

