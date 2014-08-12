/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.server;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PerformanceMetricsTest {

  @Test
  public void testCounterZero() {
    Counter c = new Counter();
    assertEquals("0", c.toString());
  }

  @Test
  public void testCounterClear() {
    Counter c = new Counter();
    c.increment();
    c.clear();
    assertEquals("0", c.toString());
  }

  @Test
  public void testCounterOne() {
    Counter c = new Counter();
    c.increment();
    assertEquals("1", c.toString());
  }

  @Test
  public void testCounterTwo() {
    Counter c = new Counter();
    c.increment();
    c.increment();
    assertEquals("2", c.toString());
  }

  @Test
  public void testCounterOverflow() {
    Counter c = new Counter();
    c.value = Integer.MAX_VALUE;
    c.increment();
    assertEquals(String.valueOf(Integer.MIN_VALUE), c.toString());
  }

  @Test
  public void testMostFrequentStringNothing() {
    MostFrequentString mfs = new MostFrequentString();
    assertEquals("null (0)", mfs.toString());
  }

  @Test
  public void testMostFrequentStringClear() {
    MostFrequentString mfs = new MostFrequentString();
    mfs.addString("foo");
    mfs.clear();
    assertEquals("null (0)", mfs.toString());
  }

  @Test
  public void testMostFrequentStringOneFoo() {
    MostFrequentString mfs = new MostFrequentString();
    mfs.addString("foo");
    assertEquals("foo (1)", mfs.toString());
  }

  @Test
  public void testMostFrequentStringTwoFoos() {
    MostFrequentString mfs = new MostFrequentString();
    mfs.addString("foo");
    mfs.addString("foo");
    assertEquals("foo (2)", mfs.toString());
  }

  @Test
  public void testMostFrequentStringTwoFoosOneBar() {
    MostFrequentString mfs = new MostFrequentString();
    mfs.addString("foo");
    mfs.addString("foo");
    mfs.addString("bar");
    assertEquals("foo (2), bar (1)", mfs.toString());
  }

  @Test
  public void testMostFrequentStringABCD() {
    MostFrequentString mfs = new MostFrequentString();
    mfs.addString("A");
    mfs.addString("B");
    mfs.addString("C");
    mfs.addString("D");
    assertEquals("A (1), B (1), C (1)", mfs.toString());
  }

  @Test
  public void testIntegerDistributionNothing() {
    IntegerDistribution id = new IntegerDistribution();
    assertEquals(".500<null, .900<null, .990<null, .999<null",
        id.toString());
  }

  @Test
  public void testIntegerDistributionClear() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(1);
    id.clear();
    assertEquals(".500<null, .900<null, .990<null, .999<null",
        id.toString());
  }

  @Test
  public void testIntegerDistributionZero() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(0);
    assertEquals(".500<1, .900<1, .990<1, .999<1", id.toString());
  }

  @Test
  public void testIntegerDistributionOne() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(1);
    assertEquals(".500<2, .900<2, .990<2, .999<2", id.toString());
  }

  @Test
  public void testIntegerDistributionTwo() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(2);
    assertEquals(".500<4, .900<4, .990<4, .999<4", id.toString());
  }

  @Test
  public void testIntegerDistributionThree() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(3);
    assertEquals(".500<4, .900<4, .990<4, .999<4", id.toString());
  }

  @Test
  public void testIntegerDistributionFour() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(4);
    assertEquals(".500<8, .900<8, .990<8, .999<8", id.toString());
  }

  @Test
  public void testIntegerDistributionFive() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(5);
    assertEquals(".500<8, .900<8, .990<8, .999<8", id.toString());
  }

  @Test
  public void testIntegerDistributionFifteen() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(15);
    assertEquals(".500<16, .900<16, .990<16, .999<16", id.toString());
  }

  @Test
  public void testIntegerDistributionSixteen() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(16);
    assertEquals(".500<32, .900<32, .990<32, .999<32", id.toString());
  }

  @Test
  public void testIntegerDistributionSeventeen() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(17);
    assertEquals(".500<32, .900<32, .990<32, .999<32", id.toString());
  }

  @Test
  public void testIntegerDistributionMaxLong() {
    IntegerDistribution id = new IntegerDistribution();
    id.addLong(Long.MAX_VALUE);
    long val = Long.highestOneBit(Long.MAX_VALUE);
    assertEquals(".500>=" + val + ", .900>=" + val + ", .990>=" + val
        + ", .999>=" + val, id.toString());
  }

  @Test
  public void testIntegerDistributionToThirtyTwo() {
    IntegerDistribution id = new IntegerDistribution();
    for (int i = 13; i <= 32; i++) id.addLong(i);
    assertEquals(".500<32, .900<32, .990<64, .999<64", id.toString());
  }

  @Test
  public void testIntegerDistributionToOneHundredTwentyEight() {
    IntegerDistribution id = new IntegerDistribution();
    for (int i = 27; i <= 128; i++) id.addLong(i);
    assertEquals(".500<128, .900<128, .990<128, .999<256",
        id.toString());
  }
}

