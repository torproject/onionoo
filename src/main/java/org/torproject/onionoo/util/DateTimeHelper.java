/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DateTimeHelper {

  private DateTimeHelper() {
  }

  public static final long ONE_SECOND = 1000L,
      TEN_SECONDS = 10L * ONE_SECOND,
      ONE_MINUTE = 60L * ONE_SECOND,
      FIVE_MINUTES = 5L * ONE_MINUTE,
      FIFTEEN_MINUTES = 15L * ONE_MINUTE,
      FOURTY_FIVE_MINUTES = 45L * ONE_MINUTE,
      ONE_HOUR = 60L * ONE_MINUTE,
      FOUR_HOURS = 4L * ONE_HOUR,
      SIX_HOURS = 6L * ONE_HOUR,
      TWELVE_HOURS = 12L * ONE_HOUR,
      ONE_DAY = 24L * ONE_HOUR,
      TWO_DAYS = 2L * ONE_DAY,
      THREE_DAYS = 3L * ONE_DAY,
      ONE_WEEK = 7L * ONE_DAY,
      TEN_DAYS = 10L * ONE_DAY,
      ROUGHLY_ONE_MONTH = 31L * ONE_DAY,
      ROUGHLY_THREE_MONTHS = 92L * ONE_DAY,
      ROUGHLY_ONE_YEAR = 366L * ONE_DAY,
      ROUGHLY_FIVE_YEARS = 5L * ROUGHLY_ONE_YEAR;

  public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

  public static final String ISO_DATETIME_TAB_FORMAT =
      "yyyy-MM-dd\tHH:mm:ss";

  public static final String ISO_YEARMONTH_FORMAT = "yyyy-MM";

  public static final String DATEHOUR_NOSPACE_FORMAT = "yyyy-MM-dd-HH";

  private static ThreadLocal<Map<String, DateFormat>> dateFormats =
      new ThreadLocal<Map<String, DateFormat>> () {
    public Map<String, DateFormat> get() {
      return super.get();
    }
    protected Map<String, DateFormat> initialValue() {
      return new HashMap<String, DateFormat>();
    }
    public void remove() {
      super.remove();
    }
    public void set(Map<String, DateFormat> value) {
      super.set(value);
    }
  };

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

  public static long parse(String string, String format) {
    try {
      return getDateFormat(format).parse(string).getTime();
    } catch (ParseException e) {
      return -1L;
    }
  }

  public static long parse(String string) {
    return parse(string, ISO_DATETIME_FORMAT);
  }
}

