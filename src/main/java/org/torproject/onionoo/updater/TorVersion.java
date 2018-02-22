/* Copyright 2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

/**
 * Helper class to compare Tor versions.
 *
 * <p>Based on "How Tor Version Numbers Work", available at
 * https://gitweb.torproject.org/torspec.git/tree/version-spec.txt</p>
 */
public class TorVersion implements Comparable<TorVersion> {

  private int majorVersion;

  private int minorVersion;

  private int microVersion;

  private String releaseSeries;

  private Integer patchLevel = null;

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
      String[] components = versionString.split("-")[0].split("\\.");
      try {
        result.majorVersion = Integer.parseInt(components[0]);
        result.minorVersion = Integer.parseInt(components[1]);
        result.microVersion = Integer.parseInt(components[2]);
        result.releaseSeries = String.format("%d.%d.%d",
            result.majorVersion, result.minorVersion, result.microVersion);
        if (components.length == 4) {
          result.patchLevel = Integer.parseInt(components[3]);
          if (versionString.contains("-")) {
            result.statusTag = versionString.split("-", 2)[1].split(" ")[0];
          }
        }
      } catch (ArrayIndexOutOfBoundsException
          | NumberFormatException exception) {
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
    if ((result = Integer.compare(this.majorVersion,
        other.majorVersion)) != 0) {
      return result;
    }
    if ((result = Integer.compare(this.minorVersion,
        other.minorVersion)) != 0) {
      return result;
    }
    if ((result = Integer.compare(this.microVersion,
        other.microVersion)) != 0) {
      return result;
    }
    if (null == this.patchLevel && null == other.patchLevel) {
      return 0;
    } else if (null == patchLevel) {
      return -1;
    } else if (null == other.patchLevel) {
      return 1;
    } else if ((result = Integer.compare(this.patchLevel,
        other.patchLevel)) != 0) {
      return result;
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
    return null != other && other instanceof TorVersion
        && this.compareTo((TorVersion) other) == 0;
  }

  @Override
  public int hashCode() {
    return 2 * Integer.hashCode(this.majorVersion)
        + 3 * Integer.hashCode(this.minorVersion)
        + 5 * Integer.hashCode(this.microVersion)
        + 7 * (null == this.patchLevel ? 0 : this.patchLevel)
        + 11 * (null == this.statusTag ? 0 : this.statusTag.hashCode());
  }

  /** Determine the version status of this tor version in the context of the
   * given recommended tor versions. */
  public TorVersionStatus determineVersionStatus(
      SortedSet<TorVersion> recommendedVersions) {
    if (recommendedVersions.contains(this)) {
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

