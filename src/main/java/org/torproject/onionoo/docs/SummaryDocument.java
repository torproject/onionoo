/* Copyright 2013--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

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

@SuppressWarnings("checkstyle:membername")
public class SummaryDocument extends Document {

  private boolean t;

  public void setRelay(boolean isRelay) {
    this.t = isRelay;
  }

  public boolean isRelay() {
    return this.t;
  }

  private String f;

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
    this.f = fingerprint;
    this.hashedFingerprint = null;
    this.base64Fingerprint = null;
    this.fingerprintSortedHexBlocks = null;
  }

  public String getFingerprint() {
    return this.f;
  }

  private transient String hashedFingerprint = null;

  /** Returns the SHA1-hashed fingerprint, or <code>null</code> if no
   * fingerprint is set. */
  public String getHashedFingerprint() {
    if (this.hashedFingerprint == null && this.f != null) {
      try {
        this.hashedFingerprint = DigestUtils.sha1Hex(Hex.decodeHex(
            this.f.toCharArray())).toUpperCase();
      } catch (DecoderException e) {
        /* Format tested in setFingerprint(). */
      }
    }
    return this.hashedFingerprint;
  }

  private transient String base64Fingerprint = null;

  /** Returns the base64-encoded fingerprint, or <code>null</code> if no
   * fingerprint is set. */
  public String getBase64Fingerprint() {
    if (this.base64Fingerprint == null && this.f != null) {
      try {
        this.base64Fingerprint = Base64.encodeBase64String(Hex.decodeHex(
            this.f.toCharArray())).replaceAll("=", "");
      } catch (DecoderException e) {
        /* Format tested in setFingerprint(). */
      }
    }
    return this.base64Fingerprint;
  }

  private transient String[] fingerprintSortedHexBlocks = null;

  /** Returns a sorted array containing blocks of 4 upper-case hex
   * characters from the fingerprint, or <code>null</code> if no
   * fingerprint is set. */
  public String[] getFingerprintSortedHexBlocks() {
    if (this.fingerprintSortedHexBlocks == null && this.f != null) {
      String fingerprint = this.f.toUpperCase();
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

  private String n;

  @SuppressWarnings("checkstyle:javadocmethod")
  public void setNickname(String nickname) {
    if (nickname == null || nickname.equals("Unnamed")) {
      this.n = null;
    } else {
      this.n = nickname;
    }
  }

  public String getNickname() {
    return this.n == null ? "Unnamed" : this.n;
  }

  private String[] ad;

  public void setAddresses(List<String> addresses) {
    this.ad = this.collectionToStringArray(addresses);
  }

  public List<String> getAddresses() {
    return this.stringArrayToList(this.ad);
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
      list = new ArrayList<String>();
    } else {
      list = Arrays.asList(stringArray);
    }
    return list;
  }

  private SortedSet<String> stringArrayToSortedSet(String[] stringArray) {
    SortedSet<String> sortedSet = new TreeSet<String>();
    if (stringArray != null) {
      sortedSet.addAll(Arrays.asList(stringArray));
    }
    return sortedSet;
  }

  private String cc;

  public void setCountryCode(String countryCode) {
    this.cc = countryCode;
  }

  public String getCountryCode() {
    return this.cc;
  }

  private String as;

  public void setAsNumber(String asNumber) {
    this.as = asNumber;
  }

  public String getAsNumber() {
    return this.as;
  }

  private String fs;

  public void setFirstSeenMillis(long firstSeenMillis) {
    this.fs = DateTimeHelper.format(firstSeenMillis);
  }

  public long getFirstSeenMillis() {
    return DateTimeHelper.parse(this.fs);
  }

  private String ls;

  public void setLastSeenMillis(long lastSeenMillis) {
    this.ls = DateTimeHelper.format(lastSeenMillis);
  }

  public long getLastSeenMillis() {
    return DateTimeHelper.parse(this.ls);
  }

  private String[] rf;

  public void setRelayFlags(SortedSet<String> relayFlags) {
    this.rf = this.collectionToStringArray(relayFlags);
  }

  public SortedSet<String> getRelayFlags() {
    return this.stringArrayToSortedSet(this.rf);
  }

  private long cw;

  public void setConsensusWeight(long consensusWeight) {
    this.cw = consensusWeight;
  }

  public long getConsensusWeight() {
    return this.cw;
  }

  private boolean r;

  public void setRunning(boolean isRunning) {
    this.r = isRunning;
  }

  public boolean isRunning() {
    return this.r;
  }

  private String c;

  @SuppressWarnings("checkstyle:javadocmethod")
  public void setContact(String contact) {
    if (contact != null && contact.length() == 0) {
      this.c = null;
    } else {
      this.c = contact;
    }
  }

  public String getContact() {
    return this.c;
  }

  /* This attribute can go away once all Onionoo services had their hourly
   * updater write effective families to summary documents at least once.
   * Remove this code after September 8, 2015. */
  private String[] ff;

  public void setFamilyFingerprints(
      SortedSet<String> familyFingerprints) {
    this.ff = this.collectionToStringArray(familyFingerprints);
  }

  public SortedSet<String> getFamilyFingerprints() {
    return this.stringArrayToSortedSet(this.ff);
  }

  private String[] ef;

  public void setEffectiveFamily(SortedSet<String> effectiveFamily) {
    this.ef = this.collectionToStringArray(effectiveFamily);
  }

  public SortedSet<String> getEffectiveFamily() {
    return this.stringArrayToSortedSet(this.ef);
  }

  /* The familyFingerprints parameter can go away after September 8, 2015.
   * See above. */
  /** Instantiates a summary document with all given properties. */
  public SummaryDocument(boolean isRelay, String nickname,
      String fingerprint, List<String> addresses, long lastSeenMillis,
      boolean running, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode, long firstSeenMillis, String asNumber,
      String contact, SortedSet<String> familyFingerprints,
      SortedSet<String> effectiveFamily) {
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
  }
}

