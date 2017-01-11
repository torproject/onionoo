/* Copyright 2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.torproject.onionoo.docs.DetailsDocumentFields;

/** Provides constants for order parameter values. */
public class OrderParameterValues {

  private static final String DESCENDING = "-";

  public static final String FIRST_SEEN_ASC = DetailsDocumentFields.FIRST_SEEN;

  public static final String FIRST_SEEN_DES =
      DESCENDING + DetailsDocumentFields.FIRST_SEEN;

  public static final String CONSENSUS_WEIGHT_ASC =
      DetailsDocumentFields.CONSENSUS_WEIGHT;

  public static final String CONSENSUS_WEIGHT_DES =
      DESCENDING + DetailsDocumentFields.CONSENSUS_WEIGHT;
}

