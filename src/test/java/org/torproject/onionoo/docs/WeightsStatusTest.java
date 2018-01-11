/* Copyright 2016--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;

public class WeightsStatusTest {

  @Test()
  public void testSetup() {
    WeightsStatus ws = new WeightsStatus();
    assertTrue("received: " + ws.toDocumentString(),
        ws.toDocumentString().isEmpty());
    assertTrue("received: " + mapToString(ws.getHistory()),
        ws.getHistory().isEmpty());
  }

  private String mapToString(SortedMap<long[], double[]> someMap) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<long[], double[]> entry : someMap.entrySet()) {
      sb.append(Arrays.toString(entry.getKey())).append(" : ")
          .append(Arrays.toString(entry.getValue())).append("\n");
    }
    return sb.toString();
  }

  @Test()
  public void testWeightHistory() {
    WeightsStatus ws = new WeightsStatus();
    SortedMap<long[], double[]> history = ws.getHistory();
    assertTrue("actually: " + mapToString(history), history.isEmpty());
    long lastSeenMillis = 1515670935476L;
    long hourMillis = 60L * 60L * 1000L;
    for (long j = 1L; j < 10L; j++) { // add arbitrary data
      ws.addToHistory(lastSeenMillis - j * hourMillis - hourMillis,
          lastSeenMillis - j * hourMillis,
          new double[]{ 1.0 * j, 2.0 * j, 1.0 * j, 2.0 * j, 1.0 * j,
              2.0 * j, Double.NaN});
      assertEquals("have: " + mapToString(history), (int) j, history.size());
    }
    ws.compressHistory(lastSeenMillis);
    assertEquals("history map: " + mapToString(history), 9, history.size());
    assertFalse("document shouldn't contain NaN: " + ws.toDocumentString(),
        ws.toDocumentString().contains("NaN"));
  }

  @Test()
  public void testWeightHistoryCompress() {
    WeightsStatus ws = new WeightsStatus();
    SortedMap<long[], double[]> history = ws.getHistory();
    assertTrue("actually: " + mapToString(history), history.isEmpty());
    long longerAgo = 1415660400;
    long hourMillis = 60L * 60L * 1000L;
    for (long j = 1L; j < 10L; j++) { // add arbitrary data
      ws.addToHistory(longerAgo - j * hourMillis - hourMillis,
          longerAgo - j * hourMillis,
          new double[]{ 1.0 * j, 2.0 * j, 1.0 * j, 2.0 * j, 1.0 * j,
              2.0 * j, j * 0.5});
      assertEquals("have: " + mapToString(history), (int) j, history.size());
    }
    ws.compressHistory(1515670935476L);
    assertEquals("history map: " + mapToString(history), 1, history.size());
    assertFalse("document shouldn't contain NaN: " + ws.toDocumentString(),
        ws.toDocumentString().contains("NaN"));
  }

  private static final String[] correctLines = new String[]{ // order matters!
      "2015-05-07 21:00:00 2015-05-07 22:00:00  0.000178279826 0.000261827632"
      + " 0.000200304932 0.000000000000  6980.000000000000\n",
      "2015-05-07 22:00:00 2015-05-07 23:00:00  0.000178279826 0.000261827632"
      + " 0.000200304932 0.000000000000  1080.000000000000\n",
      "2015-05-07 23:00:00 2015-05-08 00:00:00  0.000178279826 0.000261827632"
      + " 0.000200304932 0.000000000000  6980.000000000000\n"
  };

  private static String correctHistory =
      "[1431032400000, 1431036000000] : [-1.0, 1.78279826E-4, 2.61827632E-4, "
      + "2.00304932E-4, 0.0, -1.0, 6980.0]\n"
      + "[1431036000000, 1431039600000] : [-1.0, 1.78279826E-4, 2.61827632E-4, "
      + "2.00304932E-4, 0.0, -1.0, 1080.0]\n"
      + "[1431039600000, 1431043200000] : [-1.0, 1.78279826E-4, 2.61827632E-4, "
      + "2.00304932E-4, 0.0, -1.0, 6980.0]\n";

  @Test()
  public void testSetFromDocumentString() {
    WeightsStatus ws = new WeightsStatus();
    String exp = "";
    for (String correctLine : correctLines) {
      ws.setFromDocumentString(correctLine);
      exp += correctLine;
      assertEquals("document string: ", exp, ws.toDocumentString());
    }
    assertEquals("history: ", correctHistory, mapToString(ws.getHistory()));
    assertEquals(correctLines.length, ws.getHistory().size());
    ws.compressHistory(1515670935476L);
    assertEquals("found: " + mapToString(ws.getHistory()), 1,
        ws.getHistory().size());
    assertEquals("[1431032400000, 1431043200000] : [-1.0, 1.78279826E-4, "
        + "2.61827632E-4, 2.00304932E-4, 0.0, -1.0, 5013.333333333333]\n",
        mapToString(ws.getHistory()));
  }

  private static final String[] correctLines2 = new String[]{ // order matters!
      "2015-05-07 21:00:00 2015-05-07 22:00:00  0.000178279826 0.000261827632 "
      + "0.000200304932 0.000000000000  6980.000000000000\n",
      "2015-05-07 22:00:00 2015-05-07 23:00:00  0.000178279826 0.000261827632  "
      + "0.000000000000  1080.000000000000\n",
      "2015-05-07 23:00:00 2015-05-08 00:00:00  0.000178279826  0.000200304932 "
      + "0.000000000000  6980.000000000000\n"
  };

  @Test()
  public void testMissingValues() {
    WeightsStatus ws = new WeightsStatus();
    String exp = "";
    for (String correctLine : correctLines2) {
      ws.setFromDocumentString(correctLine);
      exp += correctLine;
      assertEquals("document string: ", exp, ws.toDocumentString());
    }
    assertEquals(correctLines.length, ws.getHistory().size());
    ws.compressHistory(1515670935476L);
    assertEquals("found: " + mapToString(ws.getHistory()), 3,
        ws.getHistory().size());
  }

  private static final String[] wrongLines = new String[]{
      "1970-01-01 22:00:00 1970-01-01 23:00:00  "
      + "0.000178279826 0.000261827632 0.000200304932 0 6980.000000200000\n",
      "1970-01-04 22:00:00 1970-01-04 23:00:00 0.000178279826 0.000261827632"
      + " 0.000200304932 0.000000000200  6980.000000000000\n",
      "2013-07-07 22:00:00 2013-07-07 23:00:00 NaN 0.000178279826 "
      + "0.000261827632 0,000200304932 0.000000000000  6980.000000000000\n",
      "1971-01-06 22:00:00 1971-01-06 23:00:00  0,000178279826 0,000261827632"
      + " 0,000200304932 0,000000000000  6980,000000000000\n"
  };

  @Test()
  public void testSetFromDocumentStringWrongInput() {
    WeightsStatus ws = new WeightsStatus();
    String exp = "";
    for (String wrongLine : wrongLines) {
      ws.setFromDocumentString(wrongLine);
      assertEquals(exp, ws.toDocumentString());
    }
  }

  private static final String[] wrongDateLines = new String[]{
      "1970-01-02 22:00:00 1970-01-01 23:00:00  " // later day first
      + "0.000178279826 0.000261827632 0.000200304932 0.000000000000 "
      + " 6980.000000000000\n",
      "1971-01-01 20:00:00 1971-01-01 10:00:00  " // later time first
      + "0.000178279826 0.000261827632 0.000200304932 0.000000000000 "
      + " 6980.000000000000\n",
      "1970-01-02 22:00 1970-01-02 23:00  " // wrong time format
      + "0.000178279826 0.000261827632 0.000200304932 0.000000000000 "
      + " 6980.000000000000\n",
      "1970/01/03 22:00:00 1970/01/03 23:00:00  " // wrong date format
      + "0.000178279826 0.000261827632 0.000200304932 0.000000000000 "
      + " 6980.000000000000\n"
  };

  @Test()
  public void testSetFromDocumentStringWrongDateTime() {
    WeightsStatus ws = new WeightsStatus();
    String exp = "";
    for (String wrongLine : wrongDateLines) {
      ws.setFromDocumentString(wrongLine);
      assertEquals(exp, ws.toDocumentString());
    }
  }

  private static final String[] compareLines = new String[]{
      "2010-05-07 12:00:00 2010-05-07 13:00:01  "
      + "1.000000000000 1.000000000000 1.000000000000 1.000000000000 "
      + " 1.000000000000\n",
      "2010-05-07 12:00:00 2010-05-07 13:00:02  "
      + "2.000000000000 2.000000000000 2.000000000000 2.000000000000 "
      + " 2.000000000000\n",
      "2010-05-07 12:00:00 2010-05-07 13:00:03  "
      + "3.000000000000 3.000000000000 3.000000000000 3.000000000000 "
      + " 3.000000000000\n"
  };

  @Test()
  public void testCompare() {
    SortedMap<long[], double[]> hist = new WeightsStatus().getHistory();
    int count = 10;
    for (int i = 0; i < count; i++) {
      hist.put(new long[]{ i + 1, 2}, new double[]{});
    }
    assertEquals(count, hist.size());
    for (int i = 0; i < count; i++) {
      hist.put(new long[]{ 1, count - i}, new double[]{});
    }
    assertEquals(2 * count - 1, hist.size());
  }

  @Test()
  public void testSetDocument() {
    WeightsStatus ws = new WeightsStatus();
    String exp = "";
    for (String compareLine : compareLines) {
      exp += compareLine;
      ws.setFromDocumentString(compareLine);
      assertEquals("Expected " + exp + " but received " + ws.toDocumentString(),
          exp, ws.toDocumentString());
    }
  }

  private static final String[] oldStyle = new String[]{
      "2015-06-01 00:00:00 2015-06-01 12:00:00  0.000883231558 "
      + "-1.000000000000 -1.000000000000 -1.000000000000  30500.000000000000\n",
      "2015-05-31 13:00:00 2015-05-31 16:00:00  0.000023372492 "
      + "-1.000000000000 -1.000000000000 -1.000000000000  778.000000000000\n",
      "2014-07-04 22:00:00 2014-07-04 23:00:00 -1.000000000000 "
      + "0.000140201280 0.000025070287 0.000025063393 0.000370501851 "
      + "-1.000000000000 3210.000000000000\n",
      "2014-07-08 19:00:00 2014-07-08 20:00:00 -1.000000000000 0.000000285817"
      + " 0.000000000000 0.000000857371 0.000000000000 -1.000000000000 "
      + "6.000000000000\n",
      "2013-09-29 07:00:00 2013-09-29 08:00:00 -1.000000000000 0.000000844333 "
      + "0.000000000000 0.000002532788 0.000000000000 -1.000000000000 "
      + "11.000000000000\n"
  };

  private static final String[] newStyle = new String[]{
      "2015-06-01 00:00:00 2015-06-01 12:00:00  0.000883231558 "
      + "    30500.000000000000\n",
      "2015-05-31 13:00:00 2015-05-31 16:00:00  0.000023372492 "
      + "    778.000000000000\n",
      "2014-07-04 22:00:00 2014-07-04 23:00:00  0.000140201280 "
      + "0.000025070287 0.000025063393 0.000370501851  3210.000000000000\n",
      "2014-07-08 19:00:00 2014-07-08 20:00:00  0.000000285817"
      + " 0.000000000000 0.000000857371 0.000000000000  6.000000000000\n",
      "2013-09-29 07:00:00 2013-09-29 08:00:00  0.000000844333 "
      + "0.000000000000 0.000002532788 0.000000000000  11.000000000000\n"
  };

  @Test()
  public void testOlderFormat() {
    for (int i = 0; i < newStyle.length; i++) {
      WeightsStatus ws = new WeightsStatus();
      ws.setFromDocumentString(oldStyle[i]);
      assertEquals("Expected\n" + newStyle[i] + "\nbut received\n"
          + ws.toDocumentString(), newStyle[i], ws.toDocumentString());
    }
  }
}

