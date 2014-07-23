/* Copyright 2013, 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.util.SortedSet;

public interface FingerprintListener {
  abstract void processFingerprints(SortedSet<String> fingerprints,
      boolean relay);
}