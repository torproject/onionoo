/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.List;

class DetailsDocument extends Document {

  class ExitPolicySummary {
    List<String> reject;
    List<String> accept;
  }

  class RelayDetails {
    String nickname;
    String fingerprint;
    List<String> or_addresses;
    List<String> exit_addresses;
    String dir_address;
    String last_seen;
    String last_changed_address_or_port;
    String first_seen;
    Boolean running;
    List<String> flags;
    String country;
    String country_name;
    String region_name;
    String city_name;
    Double latitude;
    Double longitude;
    String as_number;
    String as_name;
    Double consensus_weight;
    String host_name;
    String last_restarted;
    Integer bandwidth_rate;
    Integer bandwidth_burst;
    Integer observed_bandwidth;
    Integer advertised_bandwidth;
    List<String> exit_policy;
    ExitPolicySummary exit_policy_summary;
    String contact;
    String platform;
    List<String> family;
    Double advertised_bandwidth_fraction;
    Double consensus_weight_fraction;
    Double guard_probability;
    Double middle_probability;
    Double exit_probability;
  }

  class BridgeDetails {
    String nickname;
    String hashed_fingerprint;
    List<String> or_addresses;
    String last_seen;
    String first_seen;
    Boolean running;
    List<String> flags;
    String last_restarted;
    Integer advertised_bandwidth;
    String platform;
    String pool_assignment;
  }

  String relays_published;
  List<RelayDetails> relays;
  String bridges_published;
  List<BridgeDetails> bridges;
}

