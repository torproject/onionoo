/* Copyright 2014--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import java.util.Map;

public class WeightsDocument extends Document {

  @SuppressWarnings("unused")
  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> consensus_weight_fraction;

  public void setConsensusWeightFraction(
      Map<String, GraphHistory> consensusWeightFraction) {
    this.consensus_weight_fraction = consensusWeightFraction;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> guard_probability;

  public void setGuardProbability(
      Map<String, GraphHistory> guardProbability) {
    this.guard_probability = guardProbability;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> middle_probability;

  public void setMiddleProbability(
      Map<String, GraphHistory> middleProbability) {
    this.middle_probability = middleProbability;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> exit_probability;

  public void setExitProbability(
      Map<String, GraphHistory> exitProbability) {
    this.exit_probability = exitProbability;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> consensus_weight;

  public void setConsensusWeight(
      Map<String, GraphHistory> consensusWeight) {
    this.consensus_weight = consensusWeight;
  }
}

