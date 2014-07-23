/* Copyright 2013--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;

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
    return s == null ? null :
        StringEscapeUtils.escapeJavaScript(s).replaceAll("\\\\'", "'");
  }
  private static String unescapeJSON(String s) {
    return s == null ? null :
        StringEscapeUtils.unescapeJavaScript(s.replaceAll("'", "\\'"));
  }

  private String desc_published;
  public void setDescPublished(String descPublished) {
    this.desc_published = descPublished;
  }
  public String getDescPublished() {
    return this.desc_published;
  }

  private String last_restarted;
  public void setLastRestarted(String lastRestarted) {
    this.last_restarted = lastRestarted;
  }
  public String getLastRestarted() {
    return this.last_restarted;
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

  private String pool_assignment;
  public void setPoolAssignment(String poolAssignment) {
    this.pool_assignment = poolAssignment;
  }
  public String getPoolAssignment() {
    return this.pool_assignment;
  }

  private Map<String, Long> exit_addresses;
  public void setExitAddresses(Map<String, Long> exitAddresses) {
    this.exit_addresses = exitAddresses;
  }
  public Map<String, Long> getExitAddresses() {
    return this.exit_addresses;
  }
}
