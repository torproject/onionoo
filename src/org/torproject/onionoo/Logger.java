/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Date;

public class Logger {

  private Logger() {
  }

  private static Time time;

  public static void setTime() {
    time = ApplicationFactory.getTime();
  }

  private static long currentTimeMillis() {
    if (time == null) {
      return System.currentTimeMillis();
    } else {
      return time.currentTimeMillis();
    }
  }

  public static String formatDecimalNumber(long decimalNumber) {
    return String.format("%,d", decimalNumber);
  }

  public static String formatMillis(long millis) {
    return String.format("%02d:%02d.%03d minutes",
        millis / DateTimeHelper.ONE_MINUTE,
        (millis % DateTimeHelper.ONE_MINUTE) / DateTimeHelper.ONE_SECOND,
        millis % DateTimeHelper.ONE_SECOND);
  }

  public static String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else {
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      return String.format("%.1f %siB", bytes / Math.pow(1024, exp),
          "KMGTPE".charAt(exp-1));
    }
  }

  private static long printedLastStatusMessage = -1L;

  public static void printStatus(String message) {
    System.out.println(new Date() + ": " + message);
    printedLastStatusMessage = currentTimeMillis();
  }

  public static void printStatistics(String component, String message) {
    System.out.print("  " + component + " statistics:\n" + message);
  }

  public static void printStatusTime(String message) {
    printStatusOrErrorTime(message, false);
  }

  public static void printErrorTime(String message) {
    printStatusOrErrorTime(message, true);
  }

  private static void printStatusOrErrorTime(String message,
      boolean printToSystemErr) {
    long now = currentTimeMillis();
    long millis = printedLastStatusMessage < 0 ? 0 :
        now - printedLastStatusMessage;
    String line = "  " + message + " (" + Logger.formatMillis(millis)
        + ").";
    if (printToSystemErr) {
      System.err.println(line);
    } else {
      System.out.println(line);
    }
    printedLastStatusMessage = now;
  }
}

