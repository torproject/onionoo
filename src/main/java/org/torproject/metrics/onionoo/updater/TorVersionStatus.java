/* Copyright 2018--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import java.util.HashMap;
import java.util.Map;

public enum TorVersionStatus {

  RECOMMENDED("recommended", "r"),
  EXPERIMENTAL("experimental", "e"),
  OBSOLETE("obsolete", "o"),
  NEW_IN_SERIES("new in series", "n"),
  UNRECOMMENDED("unrecommended", "u");

  private final String statusString;

  private final String abbreviation;

  TorVersionStatus(String statusString, String abbreviation) {
    this.statusString = statusString;
    this.abbreviation = abbreviation;
  }

  public String getAbbreviation() {
    return this.abbreviation;
  }

  private static Map<String, TorVersionStatus> byAbbreviation = new HashMap<>();

  static {
    for (TorVersionStatus status : TorVersionStatus.values()) {
      byAbbreviation.put(status.abbreviation, status);
    }
  }

  public static TorVersionStatus ofAbbreviation(String abbrevation) {
    return byAbbreviation.get(abbrevation);
  }

  @Override
  public String toString() {
    return this.statusString;
  }
}

