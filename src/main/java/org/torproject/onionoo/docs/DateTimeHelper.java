/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DateTimeHelper {

  public static final long NO_TIME_AVAILABLE = -1L;

  private static final Logger log = LoggerFactory.getLogger(
      DateTimeHelper.class);

  private DateTimeHelper() {
  }

  public static final long ONE_SECOND = 1000L;

  public static final long TEN_SECONDS = 10L * ONE_SECOND;

  public static final long ONE_MINUTE = 60L * ONE_SECOND;

  public static final long FIVE_MINUTES = 5L * ONE_MINUTE;

  public static final long FIFTEEN_MINUTES = 15L * ONE_MINUTE;

  public static final long FOURTY_FIVE_MINUTES = 45L * ONE_MINUTE;

  public static final long ONE_HOUR = 60L * ONE_MINUTE;

  public static final long FOUR_HOURS = 4L * ONE_HOUR;

  public static final long SIX_HOURS = 6L * ONE_HOUR;

  public static final long TWELVE_HOURS = 12L * ONE_HOUR;

  public static final long ONE_DAY = 24L * ONE_HOUR;

  public static final long TWO_DAYS = 2L * ONE_DAY;

  public static final long THREE_DAYS = 3L * ONE_DAY;

  public static final long ONE_WEEK = 7L * ONE_DAY;

  public static final long TEN_DAYS = 10L * ONE_DAY;

  public static final long ROUGHLY_ONE_MONTH = 31L * ONE_DAY;

  public static final long ROUGHLY_THREE_MONTHS = 92L * ONE_DAY;

  public static final long ROUGHLY_SIX_MONTHS = 183L * ONE_DAY;

  public static final long ROUGHLY_ONE_YEAR = 366L * ONE_DAY;

  public static final long ROUGHLY_FIVE_YEARS = 5L * ROUGHLY_ONE_YEAR;

  public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

  public static final String ISO_DATETIME_TAB_FORMAT =
      "yyyy-MM-dd\tHH:mm:ss";

  public static final String ISO_YEARMONTH_FORMAT = "yyyy-MM";

  public static final String DATEHOUR_NOSPACE_FORMAT = "yyyy-MM-dd-HH";

  private static ThreadLocal<Map<String, DateFormat>> dateFormats =
      ThreadLocal.withInitial(HashMap::new);

  private static DateFormat getDateFormat(String format) {
    Map<String, DateFormat> threadDateFormats = dateFormats.get();
    if (!threadDateFormats.containsKey(format)) {
      DateFormat dateFormat = new SimpleDateFormat(format);
      dateFormat.setLenient(false);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      threadDateFormats.put(format, dateFormat);
    }
    return threadDateFormats.get(format);
  }

  public static String format(long millis, String format) {
    return getDateFormat(format).format(millis);
  }

  public static String format(long millis) {
    return format(millis, ISO_DATETIME_FORMAT);
  }

  /** Parses the given string using the given format and return the time
   * in milliseconds since the epoch or {@link #NO_TIME_AVAILABLE} if the
   * string cannot be parsed. */
  public static long parse(String string, String format) {
    if (null == string) {
      log.warn("Date String was null.");
      return NO_TIME_AVAILABLE;
    }
    try {
      return getDateFormat(format).parse(string).getTime();
    } catch (ParseException e) {
      log.warn(e.getMessage(), e);
      return NO_TIME_AVAILABLE;
    }
  }

  /** Parses the given string using {@link #ISO_DATETIME_FORMAT} as format
   * and return the time in milliseconds since the epoch or
   * {@link #NO_TIME_AVAILABLE} if the string cannot be parsed. */
  public static long parse(String string) {
    return parse(string, ISO_DATETIME_FORMAT);
  }
}

