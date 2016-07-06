/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LockFile {

  private static final Logger log = LoggerFactory.getLogger(
      LockFile.class);

  private final File lockFile = new File("lock");

  /**
   * Acquire the lock by writing a lock file with the current time in
   * milliseconds and return whether this operation was successful.
   *
   * @return <code>true</code> if the lock file did not exist and writing
   *     that file now succeeded, <code>false</code> otherwise.
   */
  public boolean acquireLock() {
    Time time = TimeFactory.getTime();
    try {
      if (this.lockFile.exists()) {
        return false;
      }
      if (this.lockFile.getParentFile() != null) {
        this.lockFile.getParentFile().mkdirs();
      }
    } catch (SecurityException e) {
      log.error("Unable to access lock file location", e);
      return false;
    }

    try (BufferedWriter bw = new BufferedWriter(new FileWriter(
        this.lockFile))) {
      bw.append(String.valueOf(time.currentTimeMillis()));
      bw.newLine();
      return true;
    } catch (IOException e) {
      log.error("Caught exception while trying to acquire lock!", e);
      return false;
    }
  }

  /**
   * Release the lock by deleting the lock file if it exists and return
   * whether the file was successfully deleted.
   *
   * @return <code>true</code> if the lock file does not exist anymore
   *     when returning, <code>false</code> otherwise.
   */
  public boolean releaseLock() {
    if (this.lockFile.exists()) {
      this.lockFile.delete();
    }
    return !this.lockFile.exists();
  }
}

