/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import org.torproject.onionoo.docs.DateTimeHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.TimeZone;

public class PerformanceMetrics {

  private static final Logger log = LoggerFactory.getLogger(
      PerformanceMetrics.class);

  private static final Object lock = new Object();

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
      if (lastLoggedMillis < 0L) {
        lastLoggedMillis = System.currentTimeMillis();
      } else if (receivedRequestMillis - lastLoggedMillis
          > LOG_INTERVAL_MILLIS) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        log.info("Request statistics ({}, {} s):",
            dateTimeFormat.format(lastLoggedMillis + LOG_INTERVAL_MILLIS),
            LOG_INTERVAL_SECONDS);
        log.info("  Total processed requests: {}", totalProcessedRequests);
        log.info("  Most frequently requested resource: {}",
            requestsByResourceType);
        log.info("  Most frequently requested parameter combinations: {}",
            requestsByParameters);
        log.info("  Matching relays per request: {}", matchingRelayDocuments);
        log.info("  Matching bridges per request: {}", matchingBridgeDocuments);
        log.info("  Written characters per response: {}", writtenChars);
        log.info("  Milliseconds to handle request: {}", handleRequestMillis);
        log.info("  Milliseconds to build response: {}", buildResponseMillis);
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
        log.warn("longer request handling: {} ms for {} params: {} and {} "
            + "chars.", handlingTime, resourceType, parameterKeys,
            charsWritten);
      }
      handleRequestMillis.addLong(handlingTime);
      requestsByResourceType.addString(resourceType);
      requestsByParameters.addString(parameterKeys.toString());
      matchingRelayDocuments.addLong(relayDocumentsWritten);
      matchingBridgeDocuments.addLong(bridgeDocumentsWritten);
      writtenChars.addLong(charsWritten);
      long responseTime = writtenResponseMillis - parsedRequestMillis;
      if (responseTime > DateTimeHelper.ONE_SECOND) {
        log.warn("longer response building: {} ms for {} params: {} and {} "
            + "chars.", responseTime, resourceType, parameterKeys,
            charsWritten);
      }
      buildResponseMillis.addLong(responseTime);
    }
  }
}

