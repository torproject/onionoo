/* Copyright 2017--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import static org.junit.Assert.assertEquals;

import org.torproject.metrics.onionoo.docs.DateTimeHelper;
import org.torproject.metrics.onionoo.docs.SummaryDocument;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;

@RunWith(Parameterized.class)
public class SummaryDocumentComparatorTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private SummaryDocument createSummaryDoc() {
    return new SummaryDocument(true, "TorkaZ",
        "000C5F55BD4814B917CC474BD537F1A3B33CCE2A", Arrays.asList(
        "62.216.201.221", "62.216.201.222",
        "62.216.201.223"), DateTimeHelper.parse("2013-04-19 05:00:00"),
        false, new TreeSet<>(Arrays.asList("Running",
        "Valid")), 20L, "de",
        DateTimeHelper.parse("2013-04-18 05:00:00"), "AS8767",
        "m-net telekommunikations gmbh",
        "torkaz <klaus dot zufall at gmx dot de> "
        + "<fb-token:np5_g_83jmf=>", new TreeSet<>(Arrays.asList(
        "001C13B3A55A71B977CA65EC85539D79C653A3FC",
        "0025C136C1F3A9EEFE2AE3F918F03BFA21B5070B")),
        new TreeSet<>(Arrays.asList(
            "001C13B3A55A71B977CA65EC85539D79C653A3FC")), null,
        null, null, null, null);
  }

  /** Some values for running all comparison types. */
  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        {OrderParameterValues.FIRST_SEEN_ASC, new long[]{1234L, 85968L}},
        {OrderParameterValues.FIRST_SEEN_DES, new long[]{12345L, 859689L}},
        {OrderParameterValues.CONSENSUS_WEIGHT_ASC, new long[]{12340L, 85968L}},
        {OrderParameterValues.CONSENSUS_WEIGHT_DES, new long[]{1234L, 59680L}},
        {OrderParameterValues.FIRST_SEEN_ASC, new long[]{91234L, 5968L}},
        {OrderParameterValues.FIRST_SEEN_DES, new long[]{912345L, 59689L}},
        {OrderParameterValues.CONSENSUS_WEIGHT_ASC, new long[]{912340L, 5968L}},
        {OrderParameterValues.CONSENSUS_WEIGHT_DES, new long[]{91234L, 59680L}},
        {OrderParameterValues.FIRST_SEEN_ASC, new long[]{1234L, 1234L}},
        {OrderParameterValues.FIRST_SEEN_DES, new long[]{12345L, 12345L}},
        {OrderParameterValues.CONSENSUS_WEIGHT_ASC, new long[]{12340L, 12340L}},
        {OrderParameterValues.CONSENSUS_WEIGHT_DES, new long[]{1234L, 1234L}}
        }
      );
  }

  private SummaryDocument[] sd = new SummaryDocument[2];
  private String order;
  private int expected;

  /** This constructor receives the above defined data for each run. */
  public SummaryDocumentComparatorTest(String order, long[] vals) {
    for (int i = 0; i < sd.length; i++) {
      sd[i] = createSummaryDoc();
      if (order.contains("first_seen")) {
        sd[i].setFirstSeenMillis(vals[i]);
      } else {
        sd[i].setConsensusWeight(vals[i]);
      }
    }
    this.order = order;
    this.expected = Long.compare(vals[0], vals[1]);
    if (order.contains("-")) {
      this.expected = - this.expected;
    }
  }

  @Test()
  public void testInvalidParameter() {
    String[] dummy = {OrderParameterValues.FIRST_SEEN_DES, "odd parameter"};
    thrown.expect(RuntimeException.class);
    thrown.expectMessage(Matchers
        .allOf(Matchers.containsString("Invalid order parameter"),
             Matchers.containsString(dummy[1])));
    SummaryDocumentComparator sdc = new SummaryDocumentComparator(dummy);
    sdc.compare(createSummaryDoc(), createSummaryDoc());
  }

  @Test()
  public void testRegularComparisons() {
    SummaryDocumentComparator sdc
        = new SummaryDocumentComparator(this.order);
    assertEquals(this.expected, sdc.compare(this.sd[0], this.sd[1]));
  }

}
