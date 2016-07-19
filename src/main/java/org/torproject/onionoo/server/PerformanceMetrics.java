/* Copyright 2014--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.util.Time;
import org.torproject.onionoo.util.TimeFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

class Counter {

  int value = 0;

  void increment() {
    this.value++;
  }

  @Override
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

  @Override
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
    int stringsToAdd = 3;
    int written = 0;
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

public class PerformanceMetrics {

  private static final Logger log = LoggerFactory.getLogger(
      PerformanceMetrics.class);

  private static final Object lock = new Object();

  private static Time time;

  private static long lastLoggedMillis = -1L;

  private static final long LOG_INTERVAL_SECONDS = 60L * 60L;

  private static final long LOG_INTERVAL_MILLIS =
      LOG_INTERVAL_SECONDS * 1000L;

  private static Counter totalProcessedRequests = new Counter();

  private static MostFrequentString requestsByResourceType =
      new MostFrequentString();

  private static MostFrequentString requestsByParameters =
      new MostFrequentString();

  private static IntegerDistribution matchingRelayDocuments =
      new IntegerDistribution();

  private static IntegerDistribution matchingBridgeDocuments =
      new IntegerDistribution();

  private static IntegerDistribution writtenChars =
      new IntegerDistribution();

  private static IntegerDistribution handleRequestMillis =
      new IntegerDistribution();

  private static IntegerDistribution buildResponseMillis =
      new IntegerDistribution();

  /** Collects aggregate statistics on a given request for periodic
   * request statistics, and logs requests taking longer than expected to
   * process. */
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
      } else if (receivedRequestMillis - lastLoggedMillis
          > LOG_INTERVAL_MILLIS) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        log.info("Request statistics ("
            + dateTimeFormat.format(lastLoggedMillis
            + LOG_INTERVAL_MILLIS) + ", " + (LOG_INTERVAL_SECONDS)
            + " s):");
        log.info("  Total processed requests: "
            + totalProcessedRequests);
        log.info("  Most frequently requested resource: "
            + requestsByResourceType);
        log.info("  Most frequently requested parameter "
            + "combinations: " + requestsByParameters);
        log.info("  Matching relays per request: "
            + matchingRelayDocuments);
        log.info("  Matching bridges per request: "
            + matchingBridgeDocuments);
        log.info("  Written characters per response: "
            + writtenChars);
        log.info("  Milliseconds to handle request: "
            + handleRequestMillis);
        log.info("  Milliseconds to build response: "
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
          lastLoggedMillis += LOG_INTERVAL_MILLIS;
        } while (receivedRequestMillis - lastLoggedMillis
            > LOG_INTERVAL_MILLIS);
      }
      totalProcessedRequests.increment();
      long handlingTime = parsedRequestMillis - receivedRequestMillis;
      if (handlingTime > DateTimeHelper.ONE_SECOND) {
        log.warn("longer request handling: " + handlingTime + " ms for "
            + resourceType + " params: " + parameterKeys + " and "
            + charsWritten + " chars.");
      }
      handleRequestMillis.addLong(handlingTime);
      requestsByResourceType.addString(resourceType);
      requestsByParameters.addString(parameterKeys.toString());
      matchingRelayDocuments.addLong(relayDocumentsWritten);
      matchingBridgeDocuments.addLong(bridgeDocumentsWritten);
      writtenChars.addLong(charsWritten);
      long responseTime = writtenResponseMillis - parsedRequestMillis;
      if (responseTime > DateTimeHelper.ONE_SECOND) {
        log.warn("longer response building: " + responseTime + " ms for "
            + resourceType + " params: " + parameterKeys + " and "
            + charsWritten + " chars.");
      }
      buildResponseMillis.addLong(responseTime);
    }
  }
}

