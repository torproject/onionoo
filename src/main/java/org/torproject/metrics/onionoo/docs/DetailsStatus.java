/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

public class DetailsStatus extends Document {

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

  /* From most recently published server descriptor: */

  private String descPublished;

  public void setDescPublished(Long descPublished) {
    this.descPublished = null == descPublished ? null
        : DateTimeHelper.format(descPublished);
  }

  public Long getDescPublished() {
    return DateTimeHelper.parse(this.descPublished);
  }

  private String lastRestarted;

  public void setLastRestarted(Long lastRestarted) {
    this.lastRestarted = null == lastRestarted ? null
        : DateTimeHelper.format(lastRestarted);
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

  private Map<String, List<String>> exitPolicyV6Summary;

  public void setExitPolicyV6Summary(
      Map<String, List<String>> exitPolicyV6Summary) {
    this.exitPolicyV6Summary = exitPolicyV6Summary;
  }

  public Map<String, List<String>> getExitPolicyV6Summary() {
    return this.exitPolicyV6Summary;
  }

  private Boolean hibernating;

  public void setHibernating(Boolean hibernating) {
    this.hibernating = hibernating;
  }

  public Boolean isHibernating() {
    return this.hibernating;
  }

  /* From most recently published extra-info descriptor: */

  private Long extraInfoDescPublished;

  public void setExtraInfoDescPublished(Long extraInfoDescPublished) {
    this.extraInfoDescPublished = extraInfoDescPublished;
  }

  public Long getExtraInfoDescPublished() {
    return this.extraInfoDescPublished;
  }

  private List<String> transports;

  public void setTransports(List<String> transports) {
    this.transports = (transports != null && !transports.isEmpty())
        ? transports : null;
  }

  public List<String> getTransports() {
    return this.transports;
  }

  /* From network status entries: */

  private boolean isRelay;

  public void setRelay(boolean isRelay) {
    this.isRelay = isRelay;
  }

  public boolean isRelay() {
    return this.isRelay;
  }

  private boolean running;

  public void setRunning(boolean isRunning) {
    this.running = isRunning;
  }

  public boolean isRunning() {
    return this.running;
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
    return this.orAddressesAndPorts == null ? new TreeSet<>() :
        this.orAddressesAndPorts;
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

  private SortedSet<String> relayFlags;

  public void setRelayFlags(SortedSet<String> relayFlags) {
    this.relayFlags = relayFlags;
  }

  public SortedSet<String> getRelayFlags() {
    return this.relayFlags;
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

  private long lastChangedOrAddressOrPort;

  public void setLastChangedOrAddressOrPort(
      long lastChangedOrAddressOrPort) {
    this.lastChangedOrAddressOrPort = lastChangedOrAddressOrPort;
  }

  public long getLastChangedOrAddressOrPort() {
    return this.lastChangedOrAddressOrPort;
  }

  private Boolean recommendedVersion;

  public void setRecommendedVersion(Boolean recommendedVersion) {
    this.recommendedVersion = recommendedVersion;
  }

  public Boolean isRecommendedVersion() {
    return this.recommendedVersion;
  }

  private Boolean measured;

  public void setMeasured(Boolean measured) {
    this.measured = measured;
  }

  public Boolean isMeasured() {
    return this.measured;
  }

  /* From exit lists: */

  private Map<String, Long> exitAddresses;

  public void setExitAddresses(Map<String, Long> exitAddresses) {
    this.exitAddresses = exitAddresses;
  }

  public Map<String, Long> getExitAddresses() {
    return this.exitAddresses;
  }

  /* Calculated path-selection probabilities: */

  private Float consensusWeightFraction;

  public void setConsensusWeightFraction(Float consensusWeightFraction) {
    this.consensusWeightFraction = consensusWeightFraction;
  }

  public Float getConsensusWeightFraction() {
    return this.consensusWeightFraction;
  }

  private Float guardProbability;

  public void setGuardProbability(Float guardProbability) {
    this.guardProbability = guardProbability;
  }

  public Float getGuardProbability() {
    return this.guardProbability;
  }

  private Float middleProbability;

  public void setMiddleProbability(Float middleProbability) {
    this.middleProbability = middleProbability;
  }

  public Float getMiddleProbability() {
    return this.middleProbability;
  }

  private Float exitProbability;

  public void setExitProbability(Float exitProbability) {
    this.exitProbability = exitProbability;
  }

  public Float getExitProbability() {
    return this.exitProbability;
  }

  /* GeoIP lookup results: */

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

  private String asName;

  public void setAsName(String asName) {
    this.asName = escapeJson(asName);
  }

  public String getAsName() {
    return unescapeJson(this.asName);
  }

  private String asNumber;

  public void setAsNumber(String asNumber) {
    this.asNumber = escapeJson(asNumber);
  }

  public String getAsNumber() {
    return unescapeJson(this.asNumber);
  }

  /* Reverse DNS lookup result: */

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
  public void setVerifiedHostNames(SortedSet<String> verifiedHostNames) {
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
  public SortedSet<String> getVerifiedHostNames() {
    if (null == this.verifiedHostNames) {
      return null;
    }
    SortedSet<String> verifiedHostNames = new TreeSet<>();
    for (String escapedHostName : this.verifiedHostNames) {
      verifiedHostNames.add(unescapeJson(escapedHostName));
    }
    return verifiedHostNames;
  }

  private SortedSet<String> unverifiedHostNames;

  /**
   * Creates a copy of the list with each string escaped for JSON compatibility
   * and sets this as the unverified host names, unless the argument was null in
   * which case the unverified host names are just set to null.
   */
  public void setUnverifiedHostNames(SortedSet<String> unverifiedHostNames) {
    if (null == unverifiedHostNames) {
      this.unverifiedHostNames = null;
      return;
    }
    this.unverifiedHostNames = new TreeSet<>();
    for (String hostName : unverifiedHostNames) {
      this.unverifiedHostNames.add(escapeJson(hostName));
    }
  }

  /**
   * Creates a copy of the list with each string having its escaping for JSON
   * compatibility reversed and returns the copy, unless the held reference was
   * null in which case null is returned.
   */
  public SortedSet<String> getUnverifiedHostNames() {
    if (null == this.unverifiedHostNames) {
      return null;
    }
    SortedSet<String> unverifiedHostNames = new TreeSet<>();
    for (String escapedHostName : this.unverifiedHostNames) {
      unverifiedHostNames.add(unescapeJson(escapedHostName));
    }
    return unverifiedHostNames;
  }

  private List<String> advertisedOrAddresses;

  public void setAdvertisedOrAddresses(List<String> advertisedOrAddresses) {
    this.advertisedOrAddresses = advertisedOrAddresses;
  }

  public List<String> getAdvertisedOrAddresses() {
    return this.advertisedOrAddresses;
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
}

