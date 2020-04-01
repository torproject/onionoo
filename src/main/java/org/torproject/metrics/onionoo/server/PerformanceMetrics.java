/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import org.torproject.metrics.onionoo.docs.DateTimeHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Collection;

public class PerformanceMetrics {

  private static final Logger logger = LoggerFactory.getLogger(
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
        logger.info("Request statistics ({}, {} s):",
            dateTimeFormat.format(lastLoggedMillis + LOG_INTERVAL_MILLIS),
            LOG_INTERVAL_SECONDS);
        logger.info("  Total processed requests: {}", totalProcessedRequests);
        logger.info("  Most frequently requested resource: {}",
            requestsByResourceType);
        logger.info("  Most frequently requested parameter combinations: {}",
            requestsByParameters);
        logger.info("  Matching relays per request: {}",
            matchingRelayDocuments);
        logger.info("  Matching bridges per request: {}",
            matchingBridgeDocuments);
        logger.info("  Written characters per response: {}",
            writtenChars);
        logger.info("  Milliseconds to handle request: {}",
            handleRequestMillis);
        logger.info("  Milliseconds to build response: {}",
            buildResponseMillis);
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
        logger.warn("longer request handling: {} ms for {} params: {} and {} "
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
        logger.warn("longer response building: {} ms for {} params: {} and {} "
            + "chars.", responseTime, resourceType, parameterKeys,
            charsWritten);
      }
      buildResponseMillis.addLong(responseTime);
    }
  }
}

