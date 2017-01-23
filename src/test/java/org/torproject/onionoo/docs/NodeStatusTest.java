/* Copyright 2015--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

public class NodeStatusTest {

  private static final String GABELMOO_NODE_STATUS =
      "r\tgabelmoo\tF2044413DAC2E02E3D6BCF4735A19BCA1DE97281\t"
      + "131.188.40.189;[2001:638:a000:4140::ffff:189]:443;\t2015-08-13\t"
      + "08:00:00\t443\t80\tAuthority,HSDir,Running,Stable,V2Dir,Valid\t"
      + "20\tde\t\t-1\treject\t1-65535\t2015-08-04\t12:00:00\t"
      + "2015-08-04\t12:00:00\tAS680\t"
      + "4096r/261c5fbe77285f88fb0c343266c8c2d7c5aa446d sebastian hahn "
      + "<tor@sebastianhahn.net> - 12nbrajag5u3llwetsf7fstcdaz32mu5cn\t"
      + "true\tnull";

  private void assertFamiliesCanBeDeSerialized(
      String[] declaredFamilyArray, String[] effectiveFamilyArray,
      String[] extendedFamilyArray) {
    SortedSet<String> declaredFamily = new TreeSet<>(
        Arrays.asList(declaredFamilyArray));
    SortedSet<String> effectiveFamily = new TreeSet<>(
        Arrays.asList(effectiveFamilyArray));
    SortedSet<String> extendedFamily = new TreeSet<>(
        Arrays.asList(extendedFamilyArray));
    NodeStatus nodeStatus = NodeStatus.fromString(GABELMOO_NODE_STATUS);
    nodeStatus.setDeclaredFamily(declaredFamily);
    nodeStatus.setEffectiveFamily(effectiveFamily);
    nodeStatus.setExtendedFamily(extendedFamily);
    String serialized = nodeStatus.toString();
    NodeStatus deserialized = NodeStatus.fromString(serialized);
    assertEquals("Declared families don't match", declaredFamily,
        deserialized.getDeclaredFamily());
    assertEquals("Effective families don't match", effectiveFamily,
        deserialized.getEffectiveFamily());
    assertEquals("Extended families don't match", extendedFamily,
        deserialized.getExtendedFamily());
  }

  private static final String A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";
  private static final String C = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC";
  private static final String D = "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD";
  private static final String E = "EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE";
  private static final String F = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
  private static final String NICK = "nickname";

  @Test
  public void testFamiliesEmpty() {
    assertFamiliesCanBeDeSerialized(
        new String[] {}, new String[] {}, new String[] {});
  }

  @Test
  public void testFamiliesOneNotMutual() {
    assertFamiliesCanBeDeSerialized(
        new String[] { A }, new String[] {}, new String[] {});
  }

  @Test
  public void testFamiliesTwoNotMutual() {
    assertFamiliesCanBeDeSerialized(
        new String[] { A, B }, new String[] {}, new String[] {});
  }

  @Test
  public void testFamiliesOneNotMutualOneMutual() {
    assertFamiliesCanBeDeSerialized(
        new String[] { A, B }, new String[] { B }, new String[] { B });
  }

  @Test
  public void testFamiliesOneMutualOneIndirect() {
    assertFamiliesCanBeDeSerialized(
        new String[] { A }, new String[] { A }, new String[] { A, B });
  }

  @Test
  public void testFamiliesOneNotMutualOneIndirect() {
    /* This case is special, because B is both in this relay's alleged and
     * extended family, but it's not in an effective family relationship
     * with this relay.  It's a valid case, because B can be in a mutual
     * family relationship with A. */
    assertFamiliesCanBeDeSerialized(
        new String[] { A, B }, new String[] { A }, new String[] { A, B});
  }

  @Test
  public void testFamiliesOneNotMutualOneMutualOneIndirect() {
    assertFamiliesCanBeDeSerialized(
        new String[] { A, B }, new String[] { B }, new String[] { B, C});
  }

  @Test
  public void testFamiliesTwoNotMutualTwoMutualTwoIndirect() {
    assertFamiliesCanBeDeSerialized(
        new String[] { A, B, C, D }, new String[] { C, D },
        new String[] { C, D, E, F });
  }

  @Test
  public void testFamiliesNickname() {
    assertFamiliesCanBeDeSerialized(
        new String[] { NICK }, new String[] {}, new String[] {});
  }

  private static final String KI_LAST_SEEN_1970_NODE_STATUS =
      "b\tnull\t0016C76AC19753A65F8E122046079CD09D355BA1\tnull;;\t1970-01-01\t"
      + "00:00:00\t0\t0\t\t-1\t??\t\t-1\tnull\tnull\t1970-01-01\t00:00:00\t"
      + "null\tnull\tnull\t\tnull\t::";

  @Test
  public void testLastSeen19700101Skipped() {
    assertNull(NodeStatus.fromString(KI_LAST_SEEN_1970_NODE_STATUS));
  }
}
