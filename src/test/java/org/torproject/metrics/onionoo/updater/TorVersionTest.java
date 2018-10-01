/* Copyright 2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

@RunWith(Enclosed.class)
public class TorVersionTest {

  @RunWith(Parameterized.class)
  public static class TorVersionStatusTest {

    private static String[] recommendedVersionStrings = new String[] {
        "0.2.5.16", "0.2.5.17", "0.2.9.14", "0.2.9.15", "0.3.1.9", "0.3.1.10",
        "0.3.2.8-rc", "0.3.2.9", "0.3.3.1-alpha", "0.3.3.2-alpha" };

    /** Provide test data. */
    @Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {
          { "Recommended version", "0.2.5.16", "recommended" },
          { "Recommended version", "0.3.2.8-rc", "recommended" },
          { "Recommended version", "0.3.3.2-alpha", "recommended" },
          { "Experimental version", "0.3.3.2-alpha-dev", "experimental" },
          { "Experimental version", "0.3.3.3-alpha",  "experimental" },
          { "Experimental version", "0.3.4.0-alpha-dev", "experimental" },
          { "Obsolete version", "0.2.5.15", "obsolete" },
          { "Obsolete version", "0.1.0.1-rc", "obsolete" },
          { "New-in-series version", "0.2.5.18", "new in series" },
          { "New-in-series version", "0.3.2.9-dev", "new in series" },
          { "New-in-series version", "0.3.2.10", "new in series" },
          { "Unrecommended version", "0.2.9.13", "unrecommended" },
          { "Unrecommended version", "0.3.1.8-dev", "unrecommended" },
          { "Unrecommended version", "0.3.3.0-alpha-dev", "unrecommended" },
          { "Unrecognized (experimental) version", "1.0-final",
              "unrecommended" },
          { "Unrecognized (obsolete) version", "0.0.2pre13", "unrecommended" }
      });
    }

    @Parameter
    public String testDescription;

    @Parameter(1)
    public String versionString;

    @Parameter(2)
    public String expectedVersionStatus;

    @Test
    public void test() {
      SortedSet<TorVersion> recommendedTorVersions = new TreeSet<>();
      for (String recommendedVersionString : recommendedVersionStrings) {
        recommendedTorVersions.add(TorVersion.of(recommendedVersionString));
      }
      TorVersion torVersion = TorVersion.of(this.versionString);
      String determinedVersionStatus = "unrecommended";
      if (null != torVersion) {
        determinedVersionStatus = torVersion
            .determineVersionStatus(recommendedTorVersions).toString();
      }
      assertEquals(this.testDescription, this.expectedVersionStatus,
          determinedVersionStatus);
    }
  }

  @RunWith(Parameterized.class)
  public static class TorVersionEqualsHashCodeCompareToTest {

    /** Provide test data. */
    @Parameters
    public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {
          { "0.2.5.16", "0.2.5.16", true, true, 0 },
          { "0.2.5.16", "0.2.5.17", false, false, -1 },
          { "0.3.3.1-alpha", "0.3.3.1-alpha", true, true, 0 },
          { "0.1.2.3", "00.01.02.03", true, true, 0 },
          { "0.1.2.3-alpha", "00.01.02.03-aallpphhaa", false, false, 1 },
          { "0", "0.1.2.3", false, false, -1 },
          { "0.", "0.1.2.3", false, false, -1 },
          { "0.1", "0.1.2.3", false, false, -1 },
          { "0.1.", "0.1.2.3", false, false, -1 },
          { "0.1.2", "0.1.2.3", false, false, -1 },
          { "0.1.2.", "0.1.2.3", false, false, -1 },
          { "0.2", "0.1.2.3", false, false, 1 },

      });
    }

    @Parameter
    public String firstVersionString;

    @Parameter(1)
    public String secondVersionString;

    @Parameter(2)
    public boolean expectedEqualsResult;

    @Parameter(3)
    public boolean expectedSameHashCodes;

    @Parameter(4)
    public int expectedCompareToResult;

    @Test
    public void test() {
      TorVersion firstVersion = TorVersion.of(this.firstVersionString);
      TorVersion secondVersion = TorVersion.of(this.secondVersionString);
      assertEquals(this.expectedEqualsResult,
          firstVersion.equals(secondVersion));
      if (this.expectedSameHashCodes) {
        assertEquals(firstVersion.hashCode(), secondVersion.hashCode());
      }
      int actualCompareToResult = firstVersion.compareTo(secondVersion);
      assertTrue(this.expectedCompareToResult < 0 && actualCompareToResult < 0
          || this.expectedCompareToResult == 0 && actualCompareToResult == 0
          || this.expectedCompareToResult > 0 && actualCompareToResult > 0);
    }
  }
}

