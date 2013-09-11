/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LockFile {

  private File lockFile;

  public LockFile(File lockFile) {
    this.lockFile = lockFile;
  }

  public boolean acquireLock() {
    try {
      if (this.lockFile.exists()) {
        return false;
      }
      if (this.lockFile.getParentFile() != null) {
        this.lockFile.getParentFile().mkdirs();
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.lockFile));
      bw.append("" + System.currentTimeMillis() + "\n");
      bw.close();
      return true;
    } catch (IOException e) {
      System.err.println("Caught exception while trying to acquire "
          + "lock!");
      e.printStackTrace();
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

