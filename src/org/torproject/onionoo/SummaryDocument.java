/* Copyright 2013--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

class SummaryDocument extends Document {

  private boolean t;
  public void setRelay(boolean isRelay) {
    this.t = isRelay;
  }
  public boolean isRelay() {
    return this.t;
  }

  private String f;
  public void setFingerprint(String fingerprint) {
    if (fingerprint != null) {
      Pattern fingerprintPattern = Pattern.compile("^[0-9a-fA-F]{40}$");
      if (!fingerprintPattern.matcher(fingerprint).matches()) {
        throw new IllegalArgumentException("Fingerprint '" + fingerprint
            + "' is not a valid fingerprint.");
      }
    }
    this.f = fingerprint;
  }
  public String getFingerprint() {
    return this.f;
  }

  public String getHashedFingerprint() {
    String hashedFingerprint = null;
    if (this.f != null) {
      try {
        hashedFingerprint = DigestUtils.shaHex(Hex.decodeHex(
            this.f.toCharArray())).toUpperCase();
      } catch (DecoderException e) {
        /* Format tested in setFingerprint(). */
      }
    }
    return hashedFingerprint;
  }

  private String n;
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
      int i = 0;
      for (String string : collection) {
        stringArray[i++] = string;
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
  public void setASNumber(String aSNumber) {
    this.as = aSNumber;
  }
  public String getASNumber() {
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

  private String[] ff;
  public void setFamilyFingerprints(
      SortedSet<String> familyFingerprints) {
    this.ff = this.collectionToStringArray(familyFingerprints);
  }
  public SortedSet<String> getFamilyFingerprints() {
    return this.stringArrayToSortedSet(this.ff);
  }

  public SummaryDocument(boolean isRelay, String nickname,
      String fingerprint, List<String> addresses, long lastSeenMillis,
      boolean running, SortedSet<String> relayFlags, long consensusWeight,
      String countryCode, long firstSeenMillis, String aSNumber,
      String contact, SortedSet<String> familyFingerprints) {
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
    this.setASNumber(aSNumber);
    this.setContact(contact);
    this.setFamilyFingerprints(familyFingerprints);
  }
}

