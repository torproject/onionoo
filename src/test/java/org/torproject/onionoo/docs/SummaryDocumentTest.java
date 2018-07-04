/* Copyright 2015--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.TreeSet;

public class SummaryDocumentTest {

  private SummaryDocument createSummaryDocumentRelayTorkaZ() {
    return new SummaryDocument(true, "TorkaZ",
        "000C5F55BD4814B917CC474BD537F1A3B33CCE2A", Arrays.asList(
        new String[] { "62.216.201.221", "62.216.201.222",
            "62.216.201.223" }), DateTimeHelper.parse("2013-04-19 05:00:00"),
        false, new TreeSet<>(Arrays.asList(new String[] { "Running",
            "Valid" })), 20L, "de",
        DateTimeHelper.parse("2013-04-18 05:00:00"), "AS8767",
        "torkaz <klaus dot zufall at gmx dot de> "
        + "<fb-token:np5_g_83jmf=>", new TreeSet<>(Arrays.asList(
        new String[] { "001C13B3A55A71B977CA65EC85539D79C653A3FC",
            "0025C136C1F3A9EEFE2AE3F918F03BFA21B5070B" })),
        new TreeSet<>(Arrays.asList(
        new String[] { "001C13B3A55A71B977CA65EC85539D79C653A3FC" })), null,
        null, null, null, true);
  }

  @Test()
  public void testFingerprintSortedHexBlocksAreSorted() {
    SummaryDocument relayTorkaZ = this.createSummaryDocumentRelayTorkaZ();
    String[] fingerprintSortedHexBlocks =
        relayTorkaZ.getFingerprintSortedHexBlocks();
    for (int i = 0; i < fingerprintSortedHexBlocks.length - 1; i++) {
      assertTrue("Hex blocks not in sorted order",
          fingerprintSortedHexBlocks[i].compareTo(
          fingerprintSortedHexBlocks[i + 1]) <= 0);
    }
  }

  @Test()
  public void testFingerprintSortedHexBlocksReset() {
    SummaryDocument relayTorkaZ = this.createSummaryDocumentRelayTorkaZ();
    assertArrayEquals("Hex blocks differ", new String[] { "000C", "14B9",
        "17CC", "474B", "5F55", "B33C", "BD48", "CE2A", "D537", "F1A3" },
        relayTorkaZ.getFingerprintSortedHexBlocks());
    relayTorkaZ.setFingerprint(
        "A2ECC33B3A1F735D8474CC719B4184DB55F5C000");
    assertArrayEquals("Hex blocks differ", new String[] { "3A1F", "55F5",
        "735D", "8474", "84DB", "9B41", "A2EC", "C000", "C33B", "CC71" },
        relayTorkaZ.getFingerprintSortedHexBlocks());
  }
}

