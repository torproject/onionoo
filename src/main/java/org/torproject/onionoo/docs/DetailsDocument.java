/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public class DetailsDocument extends Document {

  /* We must ensure that details files only contain ASCII characters
   * and no UTF-8 characters.  While UTF-8 characters are perfectly
   * valid in JSON, this would break compatibility with existing files
   * pretty badly.  We do this by escaping non-ASCII characters, e.g.,
   * \u00F2.  Gson won't treat this as UTF-8, but will think that we want
   * to write six characters '\', 'u', '0', '0', 'F', '2'.  The only thing
   * we'll have to do is to change back the '\\' that Gson writes for the
   * '\'. */
  private static String escapeJson(String stringToEscape) {
    return StringEscapeUtils.escapeJava(stringToEscape);
  }

  private static String unescapeJson(String stringToUnescape) {
    return StringEscapeUtils.unescapeJava(stringToUnescape);
  }

  private String nickname;

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getNickname() {
    return this.nickname;
  }

  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public String getFingerprint() {
    return this.fingerprint;
  }

  private String hashedFingerprint;

  public void setHashedFingerprint(String hashedFingerprint) {
    this.hashedFingerprint = hashedFingerprint;
  }

  public String getHashedFingerprint() {
    return this.hashedFingerprint;
  }

  private List<String> orAddresses;

  public void setOrAddresses(List<String> orAddresses) {
    this.orAddresses = orAddresses;
  }

  public List<String> getOrAddresses() {
    return this.orAddresses;
  }

  private List<String> exitAddresses;

  public void setExitAddresses(List<String> exitAddresses) {
    this.exitAddresses = !exitAddresses.isEmpty() ? exitAddresses : null;
  }

  public List<String> getExitAddresses() {
    return this.exitAddresses == null ? new ArrayList<String>()
        : this.exitAddresses;
  }

  private String dirAddress;

  public void setDirAddress(String dirAddress) {
    this.dirAddress = dirAddress;
  }

  public String getDirAddress() {
    return this.dirAddress;
  }

  private String lastSeen;

  public void setLastSeen(long lastSeen) {
    this.lastSeen = DateTimeHelper.format(lastSeen);
  }

  public long getLastSeen() {
    return DateTimeHelper.parse(this.lastSeen);
  }

  private String lastChangedAddressOrPort;

  public void setLastChangedAddressOrPort(
      long lastChangedAddressOrPort) {
    this.lastChangedAddressOrPort = DateTimeHelper.format(
        lastChangedAddressOrPort);
  }

  public long getLastChangedAddressOrPort() {
    return DateTimeHelper.parse(this.lastChangedAddressOrPort);
  }

  private String firstSeen;

  public void setFirstSeen(long firstSeen) {
    this.firstSeen = DateTimeHelper.format(firstSeen);
  }

  public long getFirstSeen() {
    return DateTimeHelper.parse(this.firstSeen);
  }

  private Boolean running;

  public void setRunning(Boolean running) {
    this.running = running;
  }

  public Boolean getRunning() {
    return this.running;
  }

  private SortedSet<String> flags;

  public void setFlags(SortedSet<String> flags) {
    this.flags = flags;
  }

  public SortedSet<String> getFlags() {
    return this.flags;
  }

  private String country;

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCountry() {
    return this.country;
  }

  private String countryName;

  public void setCountryName(String countryName) {
    this.countryName = escapeJson(countryName);
  }

  public String getCountryName() {
    return unescapeJson(this.countryName);
  }

  private String regionName;

  public void setRegionName(String regionName) {
    this.regionName = escapeJson(regionName);
  }

  public String getRegionName() {
    return unescapeJson(this.regionName);
  }

  private String cityName;

  public void setCityName(String cityName) {
    this.cityName = escapeJson(cityName);
  }

  public String getCityName() {
    return unescapeJson(this.cityName);
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

  private String asNumber;

  public void setAsNumber(String asNumber) {
    this.asNumber = escapeJson(asNumber);
  }

  public String getAsNumber() {
    return unescapeJson(this.asNumber);
  }

  private String asName;

  public void setAsName(String asName) {
    this.asName = escapeJson(asName);
  }

  public String getAsName() {
    return unescapeJson(this.asName);
  }

  private Long consensusWeight;

  public void setConsensusWeight(Long consensusWeight) {
    this.consensusWeight = consensusWeight;
  }

  public Long getConsensusWeight() {
    return this.consensusWeight;
  }

  private String hostName;

  public void setHostName(String hostName) {
    this.hostName = escapeJson(hostName);
  }

  public String getHostName() {
    return unescapeJson(this.hostName);
  }

  private List<String> verifiedHostNames;

  /**
   * Creates a copy of the list with each string escaped for JSON compatibility
   * and sets this as the verified host names, unless the argument was null in
   * which case the verified host names are just set to null.
   */
  public void setVerifiedHostNames(List<String> verifiedHostNames) {
    if (null == verifiedHostNames) {
      this.verifiedHostNames = null;
      return;
    }
    this.verifiedHostNames = new ArrayList<>();
    for (String hostName : verifiedHostNames) {
      this.verifiedHostNames.add(escapeJson(hostName));
    }
  }

  /**
   * Creates a copy of the list with each string having its escaping for JSON
   * compatibility reversed and returns the copy, unless the held reference was
   * null in which case null is returned.
   */
  public List<String> getVerifiedHostNames() {
    if (null == this.verifiedHostNames) {
      return null;
    }
    List<String> verifiedHostNames = new ArrayList<>();
    for (String escapedHostName : this.verifiedHostNames) {
      verifiedHostNames.add(unescapeJson(escapedHostName));
    }
    return verifiedHostNames;
  }

  private List<String> unverifiedHostNames;

  /**
   * Creates a copy of the list with each string escaped for JSON compatibility
   * and sets this as the unverified host names, unless the argument was null in
   * which case the unverified host names are just set to null.
   */
  public void setUnverifiedHostNames(List<String> unverifiedHostNames) {
    if (null == unverifiedHostNames) {
      this.unverifiedHostNames = null;
      return;
    }
    this.unverifiedHostNames = new ArrayList<>();
    for (String hostName : unverifiedHostNames) {
      this.unverifiedHostNames.add(escapeJson(hostName));
    }
  }

  /**
   * Creates a copy of the list with each string having its escaping for JSON
   * compatibility reversed and returns the copy, unless the held reference was
   * null in which case null is returned.
   */
  public List<String> getUnverifiedHostNames() {
    if (null == this.unverifiedHostNames) {
      return null;
    }
    List<String> unverifiedHostNames = new ArrayList<>();
    for (String escapedHostName : this.unverifiedHostNames) {
      unverifiedHostNames.add(unescapeJson(escapedHostName));
    }
    return unverifiedHostNames;
  }

  private String lastRestarted;

  public void setLastRestarted(Long lastRestarted) {
    this.lastRestarted = (lastRestarted == null ? null
        : DateTimeHelper.format(lastRestarted));
  }

  public Long getLastRestarted() {
    return this.lastRestarted == null ? null :
        DateTimeHelper.parse(this.lastRestarted);
  }

  private Integer bandwidthRate;

  public void setBandwidthRate(Integer bandwidthRate) {
    this.bandwidthRate = bandwidthRate;
  }

  public Integer getBandwidthRate() {
    return this.bandwidthRate;
  }

  private Integer bandwidthBurst;

  public void setBandwidthBurst(Integer bandwidthBurst) {
    this.bandwidthBurst = bandwidthBurst;
  }

  public Integer getBandwidthBurst() {
    return this.bandwidthBurst;
  }

  private Integer observedBandwidth;

  public void setObservedBandwidth(Integer observedBandwidth) {
    this.observedBandwidth = observedBandwidth;
  }

  public Integer getObservedBandwidth() {
    return this.observedBandwidth;
  }

  private Integer advertisedBandwidth;

  public void setAdvertisedBandwidth(Integer advertisedBandwidth) {
    this.advertisedBandwidth = advertisedBandwidth;
  }

  public Integer getAdvertisedBandwidth() {
    return this.advertisedBandwidth;
  }

  private List<String> exitPolicy;

  public void setExitPolicy(List<String> exitPolicy) {
    this.exitPolicy = exitPolicy;
  }

  public List<String> getExitPolicy() {
    return this.exitPolicy;
  }

  private Map<String, List<String>> exitPolicySummary;

  public void setExitPolicySummary(
      Map<String, List<String>> exitPolicySummary) {
    this.exitPolicySummary = exitPolicySummary;
  }

  public Map<String, List<String>> getExitPolicySummary() {
    return this.exitPolicySummary;
  }

  private Map<String, List<String>> exitPolicyV6Summary;

  public void setExitPolicyV6Summary(
      Map<String, List<String>> exitPolicyV6Summary) {
    this.exitPolicyV6Summary = exitPolicyV6Summary;
  }

  public Map<String, List<String>> getExitPolicyV6Summary() {
    return this.exitPolicyV6Summary;
  }

  private String contact;

  public void setContact(String contact) {
    this.contact = escapeJson(contact);
  }

  public String getContact() {
    return unescapeJson(this.contact);
  }

  private String platform;

  public void setPlatform(String platform) {
    this.platform = escapeJson(platform);
  }

  public String getPlatform() {
    return unescapeJson(this.platform);
  }

  private String version;

  public void setVersion(String version) {
    this.version = version;
  }

  public String getVersion() {
    return this.version;
  }

  private String versionStatus;

  public void setVersionStatus(String versionStatus) {
    this.versionStatus = versionStatus;
  }

  public String getVersionStatus() {
    return this.versionStatus;
  }

  private SortedSet<String> allegedFamily;

  public void setAllegedFamily(SortedSet<String> allegedFamily) {
    this.allegedFamily = allegedFamily;
  }

  public SortedSet<String> getAllegedFamily() {
    return this.allegedFamily;
  }

  private SortedSet<String> effectiveFamily;

  public void setEffectiveFamily(SortedSet<String> effectiveFamily) {
    this.effectiveFamily = effectiveFamily;
  }

  public SortedSet<String> getEffectiveFamily() {
    return this.effectiveFamily;
  }

  private SortedSet<String> indirectFamily;

  public void setIndirectFamily(SortedSet<String> indirectFamily) {
    this.indirectFamily = indirectFamily;
  }

  public SortedSet<String> getIndirectFamily() {
    return this.indirectFamily;
  }

  private Float consensusWeightFraction;

  /** Sets the consensus weight fraction to the given value, but only if
   * that value is neither null nor negative. */
  public void setConsensusWeightFraction(Float consensusWeightFraction) {
    if (consensusWeightFraction == null
        || consensusWeightFraction >= 0.0) {
      this.consensusWeightFraction = consensusWeightFraction;
    }
  }

  public Float getConsensusWeightFraction() {
    return this.consensusWeightFraction;
  }

  private Float guardProbability;

  /** Sets the guard probability to the given value, but only if that
   * value is neither null nor negative. */
  public void setGuardProbability(Float guardProbability) {
    if (guardProbability == null || guardProbability >= 0.0) {
      this.guardProbability = guardProbability;
    }
  }

  public Float getGuardProbability() {
    return this.guardProbability;
  }

  private Float middleProbability;

  /** Sets the middle probability to the given value, but only if that
   * value is neither null nor negative. */
  public void setMiddleProbability(Float middleProbability) {
    if (middleProbability == null || middleProbability >= 0.0) {
      this.middleProbability = middleProbability;
    }
  }

  public Float getMiddleProbability() {
    return this.middleProbability;
  }

  private Float exitProbability;

  /** Sets the exit probability to the given value, but only if that
   * value is neither null nor negative. */
  public void setExitProbability(Float exitProbability) {
    if (exitProbability == null || exitProbability >= 0.0) {
      this.exitProbability = exitProbability;
    }
  }

  public Float getExitProbability() {
    return this.exitProbability;
  }

  private Boolean recommendedVersion;

  public void setRecommendedVersion(Boolean recommendedVersion) {
    this.recommendedVersion = recommendedVersion;
  }

  public Boolean getRecommendedVersion() {
    return this.recommendedVersion;
  }

  private Boolean hibernating;

  public void setHibernating(Boolean hibernating) {
    this.hibernating = hibernating;
  }

  public Boolean getHibernating() {
    return this.hibernating;
  }

  private List<String> transports;

  public void setTransports(List<String> transports) {
    this.transports = (transports != null && !transports.isEmpty())
        ? transports : null;
  }

  public List<String> getTransports() {
    return this.transports;
  }

  private Boolean measured;

  public void setMeasured(Boolean measured) {
    this.measured = measured;
  }

  public Boolean getMeasured() {
    return this.measured;
  }

  private List<String> unreachableOrAddresses;

  public void setUnreachableOrAddresses(List<String> unreachableOrAddresses) {
    this.unreachableOrAddresses = unreachableOrAddresses;
  }

  public List<String> getUnreachableOrAddresses() {
    return this.unreachableOrAddresses;
  }
}

