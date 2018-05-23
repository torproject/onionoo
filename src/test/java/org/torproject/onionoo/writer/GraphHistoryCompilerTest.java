/* Copyright 2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.torproject.onionoo.docs.DateTimeHelper;
import org.torproject.onionoo.docs.GraphHistory;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.time.Period;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@RunWith(Parameterized.class)
public class GraphHistoryCompilerTest {

  private static ObjectMapper objectMapper = new ObjectMapper()
      .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
      .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  /** Provide test data. */
  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "Empty history",
            false, new String[0][], 0, null, null, null, null, null, null,
            null },
        { "Single entry right before graphs end",
            false, new String[][] {
              new String[] { "2017-12-31 23:00", "2018-01-01 00:00", "1" }},
            0, null, null, null, null, null, null, null },
        { "Two consecutive entries right before graphs end",
            false, new String[][] {
              new String[] { "2017-12-31 22:00", "2017-12-31 23:00", "1" },
              new String[] { "2017-12-31 23:00", "2018-01-01 00:00", "1" }},
            1, "1_week", "2017-12-31 22:30", "2017-12-31 23:30", 3600, 0.001, 2,
            new Integer[] { 999, 999 } },
        { "Two non-consecutive entries towards graphs end",
            false, new String[][] {
              new String[] { "2017-12-31 21:00", "2017-12-31 22:00", "1" },
              new String[] { "2017-12-31 23:00", "2018-01-01 00:00", "1" }},
            0, null, null, null, null, null, null, null },
        { "Two consecutive entries passing 1 week threshold",
            false, new String[][] {
              new String[] { "2017-12-24 23:00", "2017-12-25 00:00", "1" },
              new String[] { "2017-12-25 00:00", "2017-12-25 01:00", "1" }},
            1, "1_month", "2017-12-24 22:00", "2017-12-25 02:00", 14400,
            2.7805583361138913E-10, 2, new Integer[] { 999, 999 } },
        { "Two consecutive 1-hour entries over 1 week from graphs end",
            false, new String[][] {
              new String[] { "2017-12-21 22:00", "2017-12-21 23:00", "1" },
              new String[] { "2017-12-21 23:00", "2017-12-22 00:00", "1" }},
            0, null, null, null, null, null, null, null },
        { "Two consecutive 4-hour entries over 1 week from graphs end",
            false, new String[][] {
              new String[] { "2017-12-21 16:00", "2017-12-21 20:00", "1" },
              new String[] { "2017-12-21 20:00", "2017-12-22 00:00", "1" }},
            1, "1_month", "2017-12-21 18:00", "2017-12-21 22:00", 14400, 0.001,
            2, new Integer[] { 999, 999 } },
        { "Two consecutive 4-hour entries right before graphs end",
            false, new String[][] {
              new String[] { "2017-12-31 16:00", "2017-12-31 20:00", "1" },
              new String[] { "2017-12-31 20:00", "2018-01-01 00:00", "1" }},
            1, "1_month", "2017-12-31 18:00", "2017-12-31 22:00", 14400, 0.001,
            2, new Integer[] { 999, 999 } },
        { "Single 1-week divisible entry right before graphs end",
            true, new String[][] {
              new String[] { "2017-12-25 00:00", "2018-01-01 00:00", "1" }},
            1, "1_week", "2017-12-25 00:30", "2017-12-31 23:30", 3600, 0.001,
            168, null },
        { "Single 1-week-and-1-hour divisible entry right before graphs end",
            true, new String[][] {
              new String[] { "2017-12-24 23:00", "2018-01-01 00:00", "1" }},
            2, "1_month", "2017-12-24 22:00", "2017-12-31 22:00", 14400, 0.001,
            43, null },
        { "Single 66-minute divisible entry right before graphs end",
            true, new String[][] {
              new String[] { "2017-12-31 22:54", "2018-01-01 00:00", "1" }},
            0, null, null, null, null, null, null, null },
        { "Single 72-minute divisible entry right before graphs end",
            true, new String[][] {
              new String[] { "2017-12-31 22:48", "2018-01-01 00:00", "1" }},
            1, "1_week", "2017-12-31 22:30", "2017-12-31 23:30", 3600, 0.001,
            2, null },
        { "Single 6-month divisible entry 6 years before graphs end",
            true, new String[][] {
              new String[] { "2012-01-01 00:00", "2012-07-01 00:00", "1" }},
            0, null, null, null, null, null, null, null },
        { "Two consecutive 1-hour entries right after graphs end",
            false, new String[][] {
              new String[] { "2018-01-01 00:00", "2018-01-01 01:00", "1" },
              new String[] { "2018-01-01 01:00", "2018-01-01 02:00", "1" }},
            0, null, null, null, null, null, null, null }
    });
  }

  @Parameter
  public String testDescription;

  @Parameter(1)
  public boolean divisible;

  @Parameter(2)
  public String[][] historyEntries;

  @Parameter(3)
  public int expectedGraphs;

  @Parameter(4)
  public String expectedGraphName;

  @Parameter(5)
  public String expectedFirst;

  @Parameter(6)
  public String expectedLast;

  @Parameter(7)
  public Integer expectedInterval;

  @Parameter(8)
  public Double expectedFactor;

  @Parameter(9)
  public Integer expectedCount;

  @Parameter(10)
  public Integer[] expectedValues;

  private final String[] graphNames = new String[] {
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private final Period[] graphIntervals = new Period[] {
      Period.ofWeeks(1),
      Period.ofMonths(1),
      Period.ofMonths(3),
      Period.ofYears(1),
      Period.ofYears(5) };

  private final long[] dataPointIntervals = new long[] {
      DateTimeHelper.ONE_HOUR,
      DateTimeHelper.FOUR_HOURS,
      DateTimeHelper.TWELVE_HOURS,
      DateTimeHelper.TWO_DAYS,
      DateTimeHelper.TEN_DAYS };

  @Test
  public void test() throws IOException {
    GraphHistoryCompiler ghc = new GraphHistoryCompiler(DateTimeHelper.parse(
        "2018-01-01 00:00:00"));
    ghc.setDivisible(this.divisible);
    for (int i = 0; i < this.graphIntervals.length; i++) {
      ghc.addGraphType(this.graphNames[i], this.graphIntervals[i],
          this.dataPointIntervals[i]);
    }
    for (String[] historyEntry : this.historyEntries) {
      ghc.addHistoryEntry(DateTimeHelper.parse(historyEntry[0] + ":00"),
          DateTimeHelper.parse(historyEntry[1] + ":00"),
          Double.parseDouble(historyEntry[2]));
    }
    Map<String, GraphHistory> compiledGraphHistories =
        ghc.compileGraphHistories();
    String message = this.testDescription + "; "
        + objectMapper.writeValueAsString(compiledGraphHistories);
    assertEquals(message, this.expectedGraphs, compiledGraphHistories.size());
    if (null != this.expectedGraphName) {
      GraphHistory gh = compiledGraphHistories.get(this.expectedGraphName);
      assertNotNull(message, gh);
      if (null != this.expectedFirst) {
        assertEquals(message, DateTimeHelper.parse(this.expectedFirst + ":00"),
            gh.getFirst());
      }
      if (null != this.expectedLast) {
        assertEquals(message, DateTimeHelper.parse(this.expectedLast + ":00"),
            gh.getLast());
      }
      if (null != this.expectedInterval) {
        assertEquals(message, this.expectedInterval, gh.getInterval());
      }
      if (null != this.expectedFactor) {
        assertEquals(message, this.expectedFactor, gh.getFactor(), 0.01);
      }
      if (null != this.expectedCount) {
        assertEquals(message, this.expectedCount, gh.getCount());
      }
      if (null != this.expectedValues) {
        assertEquals(message, Arrays.asList(this.expectedValues),
            gh.getValues());
      }
    }
  }
}
