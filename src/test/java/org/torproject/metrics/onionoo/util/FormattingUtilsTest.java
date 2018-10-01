package org.torproject.metrics.onionoo.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class FormattingUtilsTest {

  /** Provide test data. */
  @Parameters
  public static Collection<String[]> data() throws Exception {
    List<String> lines = Files.readAllLines((new File(ClassLoader
        .getSystemResource("lines-for-escape-tests.txt").toURI()))
        .toPath());
    List<String[]> testData = new ArrayList<>();
    for (int i = 0; i < lines.size(); i += 2) {
      testData.add(new String[]{lines.get(i), lines.get(i + 1)});
    }
    return testData;
  }

  @Parameter(0)
  public String in;

  @Parameter(1)
  public String out;

  @Test
  public void testReplaceUtf() {
    assertEquals(out, FormattingUtils.replaceValidUtf(in));
  }
}
