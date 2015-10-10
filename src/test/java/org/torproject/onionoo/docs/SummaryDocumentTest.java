/* Copyright 2015 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.TreeSet;

import org.junit.Test;

public class SummaryDocumentTest {

  @Test()
  public void testFingerprintSortedHexBlocksAreSorted() {
    SummaryDocument relayTorkaZ = new SummaryDocument(true, "TorkaZ",
        "000C5F55BD4814B917CC474BD537F1A3B33CCE2A", Arrays.asList(
        new String[] { "62.216.201.221", "62.216.201.222",
        "62.216.201.223" }), DateTimeHelper.parse("2013-04-19 05:00:00"),
        false, new TreeSet<String>(Arrays.asList(new String[] { "Running",
        "Valid" })), 20L, "de",
        DateTimeHelper.parse("2013-04-18 05:00:00"), "AS8767",
        "torkaz <klaus dot zufall at gmx dot de> "
        + "<fb-token:np5_g_83jmf=>", new TreeSet<String>(Arrays.asList(
        new String[] { "001C13B3A55A71B977CA65EC85539D79C653A3FC",
        "0025C136C1F3A9EEFE2AE3F918F03BFA21B5070B" })),
        new TreeSet<String>(Arrays.asList(
        new String[] { "001C13B3A55A71B977CA65EC85539D79C653A3FC" })));
    String[] fingerprintSortedHexBlocks =
        relayTorkaZ.getFingerprintSortedHexBlocks();
    for (int i = 0; i < fingerprintSortedHexBlocks.length - 1; i++) {
      assertTrue("Hex blocks not in sorted order",
          fingerprintSortedHexBlocks[i].compareTo(
          fingerprintSortedHexBlocks[i + 1]) <= 0);
    }
  }
}

