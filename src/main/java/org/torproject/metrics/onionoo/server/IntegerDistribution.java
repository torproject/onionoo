/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import java.util.Arrays;

class IntegerDistribution {

  /**
   * Counts by power of two with negative values at index 0, values < 2^0 at
   * index 1, values < 2^1 at index 2, and values >= 2^63 at index 64.
   */
  int[] logValues = new int[65];

  void addLong(long value) {
    if (value < 0L) {
      logValues[0]++;
    } else {
      logValues[65 - Long.numberOfLeadingZeros(value)]++;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    int totalValues = 0;
    for (int logValue : logValues) {
      totalValues += logValue;
    }
    int[] permilles = new int[] { 500, 900, 990, 999 };
    if (totalValues > 0) {
      int seenValues = 0;
      for (int i = 0, j = 0; i < logValues.length; i++) {
        seenValues += logValues[i];
        while (j < permilles.length
            && (seenValues * 1000 > totalValues * permilles[j])) {
          sb.append(j > 0 ? ", " : "").append(".").append(permilles[j]);
          if (i == 0) {
            sb.append("<0");
          } else if (i < logValues.length - 1) {
            sb.append("<").append(1L << (i - 1));
          } else {
            sb.append(">=").append(1L << i - 2);
          }
          j++;
        }
        if (j == permilles.length) {
          break;
        }
      }
    } else {
      for (int j = 0; j < permilles.length; j++) {
        sb.append(j > 0 ? ", " : "").append(".").append(permilles[j])
            .append("<null");
      }
    }
    return sb.toString();
  }

  void clear() {
    Arrays.fill(logValues, 0, logValues.length - 1, 0);
  }
}
