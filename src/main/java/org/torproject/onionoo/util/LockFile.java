/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockFile {

  private final static Logger log = LoggerFactory.getLogger(
      LockFile.class);

  private final File lockFile = new File("lock");

  public boolean acquireLock() {
    Time time = TimeFactory.getTime();
    try {
      if (this.lockFile.exists()) {
        return false;
      }
      if (this.lockFile.getParentFile() != null) {
        this.lockFile.getParentFile().mkdirs();
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.lockFile));
      bw.append("" + time.currentTimeMillis() + "\n");
      bw.close();
      return true;
    } catch (IOException e) {
      log.error("Caught exception while trying to acquire lock!", e);
      return false;
    }
  }

  public boolean releaseLock() {
    if (this.lockFile.exists()) {
      this.lockFile.delete();
    }
    return !this.lockFile.exists();
  }
}

