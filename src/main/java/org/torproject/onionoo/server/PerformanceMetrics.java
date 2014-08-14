/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.server;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.onionoo.util.DateTimeHelper;
import org.torproject.onionoo.util.Time;
import org.torproject.onionoo.util.TimeFactory;

class Counter {
  int value = 0;
  void increment() {
    this.value++;
  }
  public String toString() {
    return String.valueOf(this.value);
  }
  void clear() {
    this.value = 0;
  }
}

class MostFrequentString {
  Map<String, Integer> stringFrequencies = new HashMap<String, Integer>();
  void addString(String string) {
    if (!this.stringFrequencies.containsKey(string)) {
      this.stringFrequencies.put(string, 1);
    } else {
      this.stringFrequencies.put(string,
          this.stringFrequencies.get(string) + 1);
    }
  }
  public String toString() {
    SortedMap<Integer, SortedSet<String>> sortedFrequencies =
        new TreeMap<Integer, SortedSet<String>>(
        Collections.reverseOrder());
    if (this.stringFrequencies.isEmpty()) {
      return "null (0)";
    }
    for (Map.Entry<String, Integer> e : stringFrequencies.entrySet()) {
      if (!sortedFrequencies.containsKey(e.getValue())) {
        sortedFrequencies.put(e.getValue(), new TreeSet<String>(
            Arrays.asList(new String[] { e.getKey() } )));
      } else {
        sortedFrequencies.get(e.getValue()).add(e.getKey());
      }
    }
    StringBuilder sb = new StringBuilder();
    int stringsToAdd = 3, written = 0;
    for (Map.Entry<Integer, SortedSet<String>> e :
        sortedFrequencies.entrySet()) {
      for (String string : e.getValue()) {
        if (stringsToAdd-- > 0) {
          sb.append((written++ > 0 ? ", " : "") + string + " ("
              + e.getKey() + ")");
        }
      }
      if (stringsToAdd == 0) {
        break;
      }
    }
    return sb.toString();
  }
  void clear() {
    this.stringFrequencies.clear();
  }
}

class IntegerDistribution {
  int[] logValues = new int[64];
  void addLong(long value) {
    logValues[64 - Long.numberOfLeadingZeros(value)]++;
  }
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
        while (j < permilles.length &&
            (seenValues * 1000 > totalValues * permilles[j])) {
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

public class PerformanceMetrics {

  private static final Object lock = new Object();

  private static Time time;

  private static long lastLoggedMillis = -1L;

  private static final long LOG_INTERVAL = DateTimeHelper.ONE_HOUR;

  private static Counter totalProcessedRequests = new Counter();

  private static MostFrequentString
      requestsByResourceType = new MostFrequentString(),
      requestsByParameters = new MostFrequentString();

  private static IntegerDistribution
      matchingRelayDocuments = new IntegerDistribution(),
      matchingBridgeDocuments = new IntegerDistribution(),
      writtenChars = new IntegerDistribution(),
      handleRequestMillis = new IntegerDistribution(),
      buildResponseMillis = new IntegerDistribution();

  public static void logStatistics(long receivedRequestMillis,
      String resourceType, Collection<String> parameterKeys,
      long parsedRequestMillis, int relayDocumentsWritten,
      int bridgeDocumentsWritten, int charsWritten,
      long writtenResponseMillis) {
    synchronized (lock) {
      if (time == null) {
        time = TimeFactory.getTime();
      }
      if (lastLoggedMillis < 0L) {
        lastLoggedMillis = time.currentTimeMillis();
      } else if (receivedRequestMillis - lastLoggedMillis >
          LOG_INTERVAL) {
        System.err.println("Request statistics ("
            + DateTimeHelper.format(lastLoggedMillis + LOG_INTERVAL)
            + ", " + (LOG_INTERVAL / DateTimeHelper.ONE_SECOND) + " s):");
        System.err.println("  Total processed requests: "
            + totalProcessedRequests);
        System.err.println("  Most frequently requested resource: "
            + requestsByResourceType);
        System.err.println("  Most frequently requested parameter "
            + "combinations: " + requestsByParameters);
        System.err.println("  Matching relays per request: "
            + matchingRelayDocuments);
        System.err.println("  Matching bridges per request: "
            + matchingBridgeDocuments);
        System.err.println("  Written characters per response: "
            + writtenChars);
        System.err.println("  Milliseconds to handle request: "
            + handleRequestMillis);
        System.err.println("  Milliseconds to build response: "
            + buildResponseMillis);
        totalProcessedRequests.clear();
        requestsByResourceType.clear();
        requestsByParameters.clear();
        matchingRelayDocuments.clear();
        matchingBridgeDocuments.clear();
        writtenChars.clear();
        handleRequestMillis.clear();
        buildResponseMillis.clear();
        do {
          lastLoggedMillis += LOG_INTERVAL;
        } while (receivedRequestMillis - lastLoggedMillis > LOG_INTERVAL);
      }
      totalProcessedRequests.increment();
      handleRequestMillis.addLong(parsedRequestMillis
          - receivedRequestMillis);
      requestsByResourceType.addString(resourceType);
      requestsByParameters.addString(parameterKeys.toString());
      matchingRelayDocuments.addLong(relayDocumentsWritten);
      matchingBridgeDocuments.addLong(bridgeDocumentsWritten);
      writtenChars.addLong(charsWritten);
      buildResponseMillis.addLong(writtenResponseMillis
          - parsedRequestMillis);
    }
  }

}

