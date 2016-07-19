/* Copyright 2013--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.util;

public class FormattingUtils {

  private FormattingUtils() {
  }

  private static final long ONE_SECOND = 1000L;

  private static final long ONE_MINUTE = 60L * ONE_SECOND;

  /** Formats the given number of milliseconds using the format
   * <code>"${minutes}:${seconds}.{milliseconds} minutes"</code>. */
  public static String formatMillis(long millis) {
    return String.format("%02d:%02d.%03d minutes", millis / ONE_MINUTE,
        (millis % ONE_MINUTE) / ONE_SECOND, millis % ONE_SECOND);
  }

  /** Formats the given number of bytes as B, KiB, MiB, GiB, etc. */
  public static String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else {
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      return String.format("%.1f %siB", bytes / Math.pow(1024, exp),
          "KMGTPE".charAt(exp - 1));
    }
  }

  /** Formats the given decimal number with a comma as thousands
   * separator. */
  public static String formatDecimalNumber(long decimalNumber) {
    return String.format("%,d", decimalNumber);
  }
}

