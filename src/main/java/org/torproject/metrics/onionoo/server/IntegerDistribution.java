/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import java.util.Arrays;

class IntegerDistribution {

  int[] logValues = new int[64];

  void addLong(long value) {
    logValues[64 - Long.numberOfLeadingZeros(value)]++;
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
          sb.append(j > 0 ? ", " : "").append(".").append(permilles[j])
              .append(i < logValues.length - 1 ? "<" + (1L << i)
              : ">=" + (1L << i - 1));
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
