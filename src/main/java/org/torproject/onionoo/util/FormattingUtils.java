/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.util;

import static org.apache.commons.lang3.StringEscapeUtils.unescapeJava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static helper methods for string processing etc. */
public class FormattingUtils {

  private static Logger log = LoggerFactory.getLogger(
      FormattingUtils.class);

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

  private static Pattern escapePattern = Pattern.compile(
       "(\\\\{4}u[0-9a-fA-F]{4})");

  /** De-escape only valid UTF and leave anything else escaped. */
  public static String replaceValidUtf(String text) {
    if (null == text || text.isEmpty()) {
      return text;
    }
    try {
      StringBuffer sb = new StringBuffer();
      Matcher mat = escapePattern.matcher(text);
      while (mat.find()) {
        String unescaped = mat.group(1).substring(1);
        mat.appendReplacement(sb, unescapeJava(unescaped));
      }
      mat.appendTail(sb);
      return sb.toString();
    } catch (Throwable ex) {
      log.debug("Couldn't process input '{}'.", text, ex);
      return text;
    }
  }

}

