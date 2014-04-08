/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class DateTimeHelper {

  public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

  public static final String ISO_DATETIME_TAB_FORMAT =
      "yyyy-MM-dd\tHH:mm:ss";

  public static final String ISO_YEARMONTH_FORMAT = "yyyy-MM";

  public static final String YEARHOUR_NOSPACE_FORMAT = "yyyy-MM-dd-HH";

  private static Map<String, DateFormat> dateFormats =
      new HashMap<String, DateFormat>();

  private static DateFormat getDateFormat(String format) {
    DateFormat dateFormat = dateFormats.get(format);
    if (dateFormat == null) {
      dateFormat = new SimpleDateFormat(format);
      dateFormat.setLenient(false);
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      dateFormats.put(format, dateFormat);
    }
    return dateFormat;
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

