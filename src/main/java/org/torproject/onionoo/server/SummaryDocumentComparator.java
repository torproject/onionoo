/* Copyright 2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.torproject.onionoo.docs.SummaryDocument;

import java.util.Comparator;

public class SummaryDocumentComparator implements Comparator<SummaryDocument> {

  private final String[] orderParameters;

  /** Comparator is initialized with the order parameters. */
  public SummaryDocumentComparator(String ... orderParameters) {
    this.orderParameters = orderParameters;
  }

  @Override
  public int compare(SummaryDocument o1, SummaryDocument o2) {
    int result = 0;
    for (String orderParameter : orderParameters) {
      switch (orderParameter) {
        case OrderParameterValues.CONSENSUS_WEIGHT_ASC:
          result = Long.compare(o1.getConsensusWeight(),
              o2.getConsensusWeight());
          break;
        case OrderParameterValues.CONSENSUS_WEIGHT_DES:
          result = Long.compare(o2.getConsensusWeight(),
              o1.getConsensusWeight());
          break;
        case OrderParameterValues.FIRST_SEEN_ASC:
          result = Long.compare(o1.getFirstSeenMillis(),
              o2.getFirstSeenMillis());
          break;
        case OrderParameterValues.FIRST_SEEN_DES:
          result = Long.compare(o2.getFirstSeenMillis(),
              o1.getFirstSeenMillis());
          break;
        default:
          throw new RuntimeException("Invalid order parameter: "
              + orderParameter + ".  Check initialization of this class!");
      }
      if (result != 0) {
        break;
      }
    }
    return result;
  }
}

