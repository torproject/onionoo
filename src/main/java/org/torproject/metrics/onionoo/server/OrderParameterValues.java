/* Copyright 2017--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

/** Provides constants for order parameter values. */
public class OrderParameterValues {

  private static final String DESCENDING = "-";

  public static final String FIRST_SEEN_ASC = "first_seen";

  public static final String FIRST_SEEN_DES =
      DESCENDING + FIRST_SEEN_ASC;

  public static final String CONSENSUS_WEIGHT_ASC =
      "consensus_weight";

  public static final String CONSENSUS_WEIGHT_DES =
      DESCENDING + CONSENSUS_WEIGHT_ASC;
}

