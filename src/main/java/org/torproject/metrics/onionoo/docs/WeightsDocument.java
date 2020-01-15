/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import java.util.Map;

public class WeightsDocument extends Document {

  @SuppressWarnings("unused")
  private String fingerprint;

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> consensusWeightFraction;

  public void setConsensusWeightFraction(
      Map<String, GraphHistory> consensusWeightFraction) {
    this.consensusWeightFraction = consensusWeightFraction;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> guardProbability;

  public void setGuardProbability(
      Map<String, GraphHistory> guardProbability) {
    this.guardProbability = guardProbability;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> middleProbability;

  public void setMiddleProbability(
      Map<String, GraphHistory> middleProbability) {
    this.middleProbability = middleProbability;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> exitProbability;

  public void setExitProbability(
      Map<String, GraphHistory> exitProbability) {
    this.exitProbability = exitProbability;
  }

  @SuppressWarnings("unused")
  private Map<String, GraphHistory> consensusWeight;

  public void setConsensusWeight(
      Map<String, GraphHistory> consensusWeight) {
    this.consensusWeight = consensusWeight;
  }
}

