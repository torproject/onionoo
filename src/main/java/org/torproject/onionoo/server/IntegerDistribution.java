/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

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
    for (int i = 0; i < logValues.length; i++) {
      totalValues += logValues[i];
    }
    int[] permilles = new int[] { 500, 900, 990, 999 };
    if (totalValues > 0) {
      int seenValues = 0;
      for (int i = 0, j = 0; i < logValues.length; i++) {
        seenValues += logValues[i];
        while (j < permilles.length
            && (seenValues * 1000 > totalValues * permilles[j])) {
          sb.append((j > 0 ? ", " : "") + "." + permilles[j]
              + (i < logValues.length - 1 ? "<" + (1L << i)
              : ">=" + (1L << i - 1)));
          j++;
        }
        if (j == permilles.length) {
          break;
        }
      }
    } else {
      for (int j = 0; j < permilles.length; j++) {
        sb.append((j > 0 ? ", " : "") + "." + permilles[j] + "<null");
      }
    }
    return sb.toString();
  }

  void clear() {
    Arrays.fill(logValues, 0, logValues.length - 1, 0);
  }
}
