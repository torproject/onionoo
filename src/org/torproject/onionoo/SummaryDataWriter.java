/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.SortedMap;
import java.util.TimeZone;

/* Write relay and bridge summary data to disk. */
public class SummaryDataWriter {

  private long lastValidAfterMillis;
  public void setLastValidAfterMillis(long lastValidAfterMillis) {
    this.lastValidAfterMillis = lastValidAfterMillis;
  }

  private long lastPublishedMillis;
  public void setLastPublishedMillis(long lastPublishedMillis) {
    this.lastPublishedMillis = lastPublishedMillis;
  }

  private SortedMap<String, Node> currentRelays;
  public void setCurrentRelays(SortedMap<String, Node> currentRelays) {
    this.currentRelays = currentRelays;
  }

  private SortedMap<String, Node> currentBridges;
  public void setCurrentBridges(SortedMap<String, Node> currentBridges) {
    this.currentBridges = currentBridges;
  }

  private File relaySearchDataFile = new File("out/summary.json");
  public void writeSummaryDataFile() {
    try {
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      long now = System.currentTimeMillis();
      String relaysPublishedString = dateTimeFormat.format(
          this.lastValidAfterMillis);
      String bridgesPublishedString = dateTimeFormat.format(
          this.lastPublishedMillis);
      this.relaySearchDataFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.relaySearchDataFile));
      bw.write("{\"relays_published\":\"" + relaysPublishedString
          + "\",\n\"relays\":[");
      int written = 0;
      for (Node entry : this.currentRelays.values()) {
        String nickname = !entry.getNickname().equals("Unnamed") ?
            entry.getNickname() : null;
        String fingerprint = entry.getFingerprint();
        String running = entry.getRunning() ? "true" : "false";
        String address = entry.getAddress();
        if (written++ > 0) {
          bw.write(",");
        }
        bw.write("\n{"
            + (nickname == null ? "" : "\"n\":\"" + nickname + "\",")
            + "\"f\":\"" + fingerprint + "\","
            + "\"a\":[\"" + address + "\"],"
            + "\"r\":" + running + "}");
      }
      bw.write("\n],\n\"bridges_published\":\"" + bridgesPublishedString
          + "\",\n\"bridges\":[");
      written = 0;
      for (Node entry : this.currentBridges.values()) {
        String hashedFingerprint = entry.getFingerprint();
        String running = entry.getRunning() ? "true" : "false";
        if (written++ > 0) {
          bw.write(",");
        }
        bw.write("\n{"
            + "\"h\":\"" + hashedFingerprint + "\","
            + "\"r\":" + running + "}");
      }
      bw.write("\n]}\n");
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write "
          + this.relaySearchDataFile.getAbsolutePath() + ".  Exiting.");
      e.printStackTrace();
      System.exit(1);
    }
  }
}

