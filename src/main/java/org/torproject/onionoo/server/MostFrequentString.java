/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

class MostFrequentString {

  Map<String, Integer> stringFrequencies = new HashMap<>();

  void addString(String string) {
    if (!this.stringFrequencies.containsKey(string)) {
      this.stringFrequencies.put(string, 1);
    } else {
      this.stringFrequencies.put(string,
          this.stringFrequencies.get(string) + 1);
    }
  }

  @Override
  public String toString() {
    SortedMap<Integer, SortedSet<String>> sortedFrequencies = new TreeMap<>(
        Collections.reverseOrder());
    if (this.stringFrequencies.isEmpty()) {
      return "null (0)";
    }
    for (Map.Entry<String, Integer> e : stringFrequencies.entrySet()) {
      if (!sortedFrequencies.containsKey(e.getValue())) {
        sortedFrequencies.put(e.getValue(), new TreeSet<>(
            Arrays.asList(new String[] { e.getKey() } )));
      } else {
        sortedFrequencies.get(e.getValue()).add(e.getKey());
      }
    }
    StringBuilder sb = new StringBuilder();
    int stringsToAdd = 3;
    int written = 0;
    SortedSet<String> remainingStrings = new TreeSet<>();
    for (Map.Entry<Integer, SortedSet<String>> e :
        sortedFrequencies.entrySet()) {
      for (String string : e.getValue()) {
        if (stringsToAdd-- > 0) {
          sb.append(written++ > 0 ? ", " : "").append(string).append(" (")
              .append(e.getKey()).append(")");
        } else {
          remainingStrings.add(string);
        }
      }
    }
    for (String string : remainingStrings) {
      sb.append(", ").append(string);
    }
    return sb.toString();
  }

  void clear() {
    this.stringFrequencies.clear();
  }
}
