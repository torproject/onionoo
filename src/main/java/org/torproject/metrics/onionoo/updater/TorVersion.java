/* Copyright 2018--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Helper class to compare Tor versions.
 *
 * <p>Based on "How Tor Version Numbers Work", available at
 * https://gitweb.torproject.org/torspec.git/tree/version-spec.txt</p>
 */
public class TorVersion implements Comparable<TorVersion> {

  private List<Integer> versionNumbers = new ArrayList<>();

  private String releaseSeries;

  private String statusTag = null;

  private static Map<String, TorVersion> knownVersions = new HashMap<>();

  private TorVersion() {
  }

  /** Return a TorVersion instance from the given tor version string that can be
   * compared to other tor version strings, or null if the given string is not a
   * valid tor version. */
  public static TorVersion of(String versionString) {
    if (null == versionString) {
      return null;
    }
    if (!knownVersions.containsKey(versionString)) {
      TorVersion result = new TorVersion();
      boolean isValid = true;
      try {
        String[] components = versionString.split("-")[0].split("\\.", -1);
        for (int position = 0; position < 4 && position < components.length;
             position++) {
          if (!components[position].isEmpty()) {
            result.versionNumbers.add(Integer.parseInt(components[position]));
          } else if (0 == position || position < components.length - 1) {
            /* Version cannot start with a blank, nor can it contain a blank in
             * between two dots. */
            isValid = false;
          }
        }
        if (result.versionNumbers.size() >= 3) {
          result.releaseSeries = String.format("%d.%d.%d",
              result.versionNumbers.get(0), result.versionNumbers.get(1),
              result.versionNumbers.get(2));
        }
        if (versionString.contains("-")) {
          result.statusTag = versionString.split("-", 2)[1].split(" ")[0];
        }
      } catch (ArrayIndexOutOfBoundsException
          | NumberFormatException exception) {
        isValid = false;
      }
      if (!isValid) {
        result = null;
      }
      knownVersions.put(versionString, result);
    }
    return knownVersions.get(versionString);
  }

  @Override
  public int compareTo(TorVersion other) {
    if (null == other) {
      throw new NullPointerException();
    }
    int result;
    for (int position = 0; position < this.versionNumbers.size()
        && position < other.versionNumbers.size(); position++) {
      if ((result = Integer.compare(this.versionNumbers.get(position),
          other.versionNumbers.get(position))) != 0) {
        return result;
      }
    }
    if (this.versionNumbers.size() != other.versionNumbers.size()) {
      return this.versionNumbers.size() < other.versionNumbers.size() ? -1 : 1;
    }
    if (null == this.statusTag && null == other.statusTag) {
      return 0;
    } else if (null == this.statusTag) {
      return -1;
    } else if (null == other.statusTag) {
      return 1;
    } else {
      return this.statusTag.compareTo(other.statusTag);
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TorVersion
        && this.compareTo((TorVersion) other) == 0;
  }

  /** Return whether prefixes of this version and another version match.
   *
   * <p>Two versions A and B have the same prefix if A starts with B, B starts
   * with A, or A and B are the same.</p>
   */
  public boolean matchingPrefix(TorVersion other) {
    if (null == other) {
      throw new NullPointerException();
    }
    for (int position = 0; position < this.versionNumbers.size()
        && position < other.versionNumbers.size(); position++) {
      if (!this.versionNumbers.get(position).equals(
          other.versionNumbers.get(position))) {
        return false;
      }
    }
    if (null != this.statusTag && null != other.statusTag) {
      return this.statusTag.equals(other.statusTag);
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    for (int position = 0; position < this.versionNumbers.size(); position++) {
      result += (2 * position + 1)
          * Integer.hashCode(this.versionNumbers.get(position));
    }
    if (null != this.statusTag) {
      result += 11 * this.statusTag.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int position = 0; position < this.versionNumbers.size(); position++) {
      if (position > 0) {
        sb.append('.');
      }
      sb.append(this.versionNumbers.get(position));
    }
    if (null != this.statusTag) {
      sb.append('-').append(this.statusTag);
    }
    return sb.toString();
  }

  /** Determine the version status of this tor version in the context of the
   * given recommended tor versions. */
  public TorVersionStatus determineVersionStatus(
      SortedSet<TorVersion> recommendedVersions) {
    if (null == this.releaseSeries) {
      /* Only consider full versions, not partial versions. */
      return TorVersionStatus.UNRECOMMENDED;
    } else if (recommendedVersions.contains(this)) {
      return TorVersionStatus.RECOMMENDED;
    } else if (this.compareTo(recommendedVersions.last()) > 0) {
      return TorVersionStatus.EXPERIMENTAL;
    } else if (this.compareTo(recommendedVersions.first()) < 0) {
      return TorVersionStatus.OBSOLETE;
    } else {
      boolean seriesHasRecommendedVersions = false;
      boolean notNewInSeries = false;
      for (TorVersion recommendedVersion : recommendedVersions) {
        if (this.releaseSeries.equals(
            recommendedVersion.releaseSeries)) {
          seriesHasRecommendedVersions = true;
          if (this.compareTo(recommendedVersion) < 0) {
            notNewInSeries = true;
          }
        }
      }
      if (seriesHasRecommendedVersions && !notNewInSeries) {
        return TorVersionStatus.NEW_IN_SERIES;
      } else {
        return TorVersionStatus.UNRECOMMENDED;
      }
    }
  }
}

