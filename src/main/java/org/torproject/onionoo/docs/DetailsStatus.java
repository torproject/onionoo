/* Copyright 2013--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

public class DetailsStatus extends Document {

  /* We must ensure that details files only contain ASCII characters
   * and no UTF-8 characters.  While UTF-8 characters are perfectly
   * valid in JSON, this would break compatibility with existing files
   * pretty badly.  We do this by escaping non-ASCII characters, e.g.,
   * \u00F2.  Gson won't treat this as UTF-8, but will think that we want
   * to write six characters '\', 'u', '0', '0', 'F', '2'.  The only thing
   * we'll have to do is to change back the '\\' that Gson writes for the
   * '\'. */
  private static String escapeJSON(String s) {
    return StringUtils.replaceEach(StringEscapeUtils.escapeEcmaScript(s),
        new String[] { "\\\\'" }, new String[] { "'" });
  }
  private static String unescapeJSON(String s) {
    return StringEscapeUtils.unescapeEcmaScript(StringUtils.replaceEach(s,
        new String[] { "'" }, new String[] { "\\'" }));
  }

  /* From most recently published server descriptor: */

  private String desc_published;
  public void setDescPublished(Long descPublished) {
    this.desc_published = DateTimeHelper.format(descPublished);
  }
  public Long getDescPublished() {
    return this.desc_published == null ? null :
        DateTimeHelper.parse(this.desc_published);
  }

  private String last_restarted;
  public void setLastRestarted(Long lastRestarted) {
    this.last_restarted = DateTimeHelper.format(lastRestarted);
  }
  public Long getLastRestarted() {
    return this.last_restarted == null ? null :
        DateTimeHelper.parse(this.last_restarted);
  }

  private Integer bandwidth_rate;
  public void setBandwidthRate(Integer bandwidthRate) {
    this.bandwidth_rate = bandwidthRate;
  }
  public Integer getBandwidthRate() {
    return this.bandwidth_rate;
  }

  private Integer bandwidth_burst;
  public void setBandwidthBurst(Integer bandwidthBurst) {
    this.bandwidth_burst = bandwidthBurst;
  }
  public Integer getBandwidthBurst() {
    return this.bandwidth_burst;
  }

  private Integer observed_bandwidth;
  public void setObservedBandwidth(Integer observedBandwidth) {
    this.observed_bandwidth = observedBandwidth;
  }
  public Integer getObservedBandwidth() {
    return this.observed_bandwidth;
  }

  private Integer advertised_bandwidth;
  public void setAdvertisedBandwidth(Integer advertisedBandwidth) {
    this.advertised_bandwidth = advertisedBandwidth;
  }
  public Integer getAdvertisedBandwidth() {
    return this.advertised_bandwidth;
  }

  private List<String> exit_policy;
  public void setExitPolicy(List<String> exitPolicy) {
    this.exit_policy = exitPolicy;
  }
  public List<String> getExitPolicy() {
    return this.exit_policy;
  }

  private String contact;
  public void setContact(String contact) {
    this.contact = escapeJSON(contact);
  }
  public String getContact() {
    return unescapeJSON(this.contact);
  }

  private String platform;
  public void setPlatform(String platform) {
    this.platform = escapeJSON(platform);
  }
  public String getPlatform() {
    return unescapeJSON(this.platform);
  }

  private List<String> family;
  public void setFamily(List<String> family) {
    this.family = family;
  }
  public List<String> getFamily() {
    return this.family;
  }

  private Map<String, List<String>> exit_policy_v6_summary;
  public void setExitPolicyV6Summary(
      Map<String, List<String>> exitPolicyV6Summary) {
    this.exit_policy_v6_summary = exitPolicyV6Summary;
  }
  public Map<String, List<String>> getExitPolicyV6Summary() {
    return this.exit_policy_v6_summary;
  }

  private Boolean hibernating;
  public void setHibernating(Boolean hibernating) {
    this.hibernating = hibernating;
  }
  public Boolean getHibernating() {
    return this.hibernating;
  }

  /* From most recently published extra-info descriptor: */

  private Long extra_info_desc_published;
  public void setExtraInfoDescPublished(Long extraInfoDescPublished) {
    this.extra_info_desc_published = extraInfoDescPublished;
  }
  public Long getExtraInfoDescPublished() {
    return this.extra_info_desc_published;
  }

  private List<String> transports;
  public void setTransports(List<String> transports) {
    this.transports = (transports != null && !transports.isEmpty()) ?
        transports : null;
  }
  public List<String> getTransports() {
    return this.transports;
  }

  /* From network status entries: */

  private boolean is_relay;
  public void setRelay(boolean isRelay) {
    this.is_relay = isRelay;
  }
  public boolean isRelay() {
    return this.is_relay;
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

  private SortedSet<String> or_addresses_and_ports;
  public void setOrAddressesAndPorts(
      SortedSet<String> orAddressesAndPorts) {
    this.or_addresses_and_ports = orAddressesAndPorts;
  }
  public SortedSet<String> getOrAddressesAndPorts() {
    return this.or_addresses_and_ports == null ? new TreeSet<String>() :
        this.or_addresses_and_ports;
  }
  public SortedSet<String> getOrAddresses() {
    SortedSet<String> orAddresses = new TreeSet<String>();
    if (this.address != null) {
      orAddresses.add(this.address);
    }
    if (this.or_addresses_and_ports != null) {
      for (String orAddressAndPort : this.or_addresses_and_ports) {
        if (orAddressAndPort.contains(":")) {
          String orAddress = orAddressAndPort.substring(0,
              orAddressAndPort.lastIndexOf(':'));
          orAddresses.add(orAddress);
        }
      }
    }
    return orAddresses;
  }

  private long first_seen_millis;
  public void setFirstSeenMillis(long firstSeenMillis) {
    this.first_seen_millis = firstSeenMillis;
  }
  public long getFirstSeenMillis() {
    return this.first_seen_millis;
  }

  private long last_seen_millis;
  public void setLastSeenMillis(long lastSeenMillis) {
    this.last_seen_millis = lastSeenMillis;
  }
  public long getLastSeenMillis() {
    return this.last_seen_millis;
  }

  private int or_port;
  public void setOrPort(int orPort) {
    this.or_port = orPort;
  }
  public int getOrPort() {
    return this.or_port;
  }

  private int dir_port;
  public void setDirPort(int dirPort) {
    this.dir_port = dirPort;
  }
  public int getDirPort() {
    return this.dir_port;
  }

  private SortedSet<String> relay_flags;
  public void setRelayFlags(SortedSet<String> relayFlags) {
    this.relay_flags = relayFlags;
  }
  public SortedSet<String> getRelayFlags() {
    return this.relay_flags;
  }

  private long consensus_weight;
  public void setConsensusWeight(long consensusWeight) {
    this.consensus_weight = consensusWeight;
  }
  public long getConsensusWeight() {
    return this.consensus_weight;
  }

  private String default_policy;
  public void setDefaultPolicy(String defaultPolicy) {
    this.default_policy = defaultPolicy;
  }
  public String getDefaultPolicy() {
    return this.default_policy;
  }

  private String port_list;
  public void setPortList(String portList) {
    this.port_list = portList;
  }
  public String getPortList() {
    return this.port_list;
  }

  private long last_changed_or_address_or_port;
  public void setLastChangedOrAddressOrPort(
      long lastChangedOrAddressOrPort) {
    this.last_changed_or_address_or_port = lastChangedOrAddressOrPort;
  }
  public long getLastChangedOrAddressOrPort() {
    return this.last_changed_or_address_or_port;
  }

  private Boolean recommended_version;
  public void setRecommendedVersion(Boolean recommendedVersion) {
    this.recommended_version = recommendedVersion;
  }
  public Boolean getRecommendedVersion() {
    return this.recommended_version;
  }

  /* From bridge pool assignments: */

  private String pool_assignment;
  public void setPoolAssignment(String poolAssignment) {
    this.pool_assignment = poolAssignment;
  }
  public String getPoolAssignment() {
    return this.pool_assignment;
  }

  /* From exit lists: */

  private Map<String, Long> exit_addresses;
  public void setExitAddresses(Map<String, Long> exitAddresses) {
    this.exit_addresses = exitAddresses;
  }
  public Map<String, Long> getExitAddresses() {
    return this.exit_addresses;
  }

  /* Calculated path-selection probabilities: */

  private Float consensus_weight_fraction;
  public void setConsensusWeightFraction(Float consensusWeightFraction) {
    this.consensus_weight_fraction = consensusWeightFraction;
  }
  public Float getConsensusWeightFraction() {
    return this.consensus_weight_fraction;
  }

  private Float guard_probability;
  public void setGuardProbability(Float guardProbability) {
    this.guard_probability = guardProbability;
  }
  public Float getGuardProbability() {
    return this.guard_probability;
  }

  private Float middle_probability;
  public void setMiddleProbability(Float middleProbability) {
    this.middle_probability = middleProbability;
  }
  public Float getMiddleProbability() {
    return this.middle_probability;
  }

  private Float exit_probability;
  public void setExitProbability(Float exitProbability) {
    this.exit_probability = exitProbability;
  }
  public Float getExitProbability() {
    return this.exit_probability;
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

  private String country_code;
  public void setCountryCode(String countryCode) {
    this.country_code = countryCode;
  }
  public String getCountryCode() {
    return this.country_code;
  }

  private String country_name;
  public void setCountryName(String countryName) {
    this.country_name = countryName;
  }
  public String getCountryName() {
    return this.country_name;
  }

  private String region_name;
  public void setRegionName(String regionName) {
    this.region_name = regionName;
  }
  public String getRegionName() {
    return this.region_name;
  }

  private String city_name;
  public void setCityName(String cityName) {
    this.city_name = cityName;
  }
  public String getCityName() {
    return this.city_name;
  }

  private String as_name;
  public void setASName(String aSName) {
    this.as_name = aSName;
  }
  public String getASName() {
    return this.as_name;
  }

  private String as_number;
  public void setASNumber(String aSNumber) {
    this.as_number = aSNumber;
  }
  public String getASNumber() {
    return this.as_number;
  }

  /* Reverse DNS lookup result: */

  private String host_name;
  public void setHostName(String hostName) {
    this.host_name = hostName;
  }
  public String getHostName() {
    return this.host_name;
  }
}
