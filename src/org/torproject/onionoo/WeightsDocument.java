/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Map;

class WeightsDocument extends Document {

  private String fingerprint;
  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  private Map<String, GraphHistory> advertised_bandwidth_fraction;
  public void setAdvertisedBandwidthFraction(
      Map<String, GraphHistory> advertisedBandwidthFraction) {
    this.advertised_bandwidth_fraction = advertisedBandwidthFraction;
  }

  private Map<String, GraphHistory> consensus_weight_fraction;
  public void setConsensusWeightFraction(
      Map<String, GraphHistory> consensusWeightFraction) {
    this.consensus_weight_fraction = consensusWeightFraction;
  }

  private Map<String, GraphHistory> guard_probability;
  public void setGuardProbability(
      Map<String, GraphHistory> guardProbability) {
    this.guard_probability = guardProbability;
  }

  private Map<String, GraphHistory> middle_probability;
  public void setMiddleProbability(
      Map<String, GraphHistory> middleProbability) {
    this.middle_probability = middleProbability;
  }

  private Map<String, GraphHistory> exit_probability;
  public void setExitProbability(
      Map<String, GraphHistory> exitProbability) {
    this.exit_probability = exitProbability;
  }

  private Map<String, GraphHistory> advertised_bandwidth;
  public void setAdvertisedBandwidth(
      Map<String, GraphHistory> advertisedBandwidth) {
    this.advertised_bandwidth = advertisedBandwidth;
  }

  private Map<String, GraphHistory> consensus_weight;
  public void setConsensusWeight(
      Map<String, GraphHistory> consensusWeight) {
    this.consensus_weight = consensusWeight;
  }
}

