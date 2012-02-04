/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

/* Read and write relay and bridge summary data from/to disk. */
public class SummaryDataWriter {

  private File internalRelaySearchDataFile =
      new File("status/summary.csv");
  private File relaySearchDataFile = new File("out/summary.json");

  /* Read the internal relay search data file from disk. */
  public CurrentNodes readRelaySearchDataFile() {
    CurrentNodes result = new CurrentNodes();
    if (this.internalRelaySearchDataFile.exists() &&
        !this.internalRelaySearchDataFile.isDirectory()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            this.internalRelaySearchDataFile));
        String line;
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        while ((line = br.readLine()) != null) {
          if (line.startsWith("r ")) {
            String[] parts = line.split(" ");
            if (parts.length < 9) {
              System.err.println("Line '" + line + "' in '"
                  + this.internalRelaySearchDataFile.getAbsolutePath()
                  + " is invalid.  Exiting.");
              System.exit(1);
            }
            String nickname = parts[1];
            String fingerprint = parts[2];
            String address = parts[3];
            long validAfterMillis = dateTimeFormat.parse(parts[4] + " "
               + parts[5]).getTime();
            int orPort = Integer.parseInt(parts[6]);
            int dirPort = Integer.parseInt(parts[7]);
            SortedSet<String> relayFlags = new TreeSet<String>(
                Arrays.asList(parts[8].split(",")));
            result.addRelay(nickname, fingerprint, address,
                validAfterMillis, orPort, dirPort, relayFlags);
          } else if (line.startsWith("b ")) {
            String[] parts = line.split(" ");
            if (parts.length < 7) {
              System.err.println("Line '" + line + "' in '"
                  + this.internalRelaySearchDataFile.getAbsolutePath()
                  + " is invalid.  Exiting.");
              System.exit(1);
            }
            int skip = parts.length == 8 ? 1 : 0;
            String hashedFingerprint = parts[1];
            String address = parts.length == 8 ? parts[2] : "0.0.0.0";
            long publishedMillis = dateTimeFormat.parse(parts[2 + skip]
               + " " + parts[3 + skip]).getTime();
            int orPort = Integer.parseInt(parts[4 + skip]);
            int dirPort = Integer.parseInt(parts[5 + skip]);
            SortedSet<String> relayFlags = new TreeSet<String>(
                Arrays.asList(parts[6 + skip].split(",")));
            result.addBridge(hashedFingerprint, address, publishedMillis,
                orPort, dirPort, relayFlags);
          }
        }
        br.close();
      } catch (IOException e) {
        System.err.println("Could not read "
            + this.internalRelaySearchDataFile.getAbsolutePath()
            + ".  Exiting.");
        e.printStackTrace();
        System.exit(1);
      } catch (ParseException e) {
        System.err.println("Could not read "
            + this.internalRelaySearchDataFile.getAbsolutePath()
            + ".  Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }
    return result;
  }

  /* Write the relay search data file to disk. */
  public void writeRelaySearchDataFile(CurrentNodes sd) {

    /* Write internal relay search data file to disk. */
    try {
      this.internalRelaySearchDataFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.internalRelaySearchDataFile));
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      for (Node entry : sd.getCurrentRelays().values()) {
        String nickname = entry.getNickname();
        String fingerprint = entry.getFingerprint();
        String address = entry.getAddress();
        String validAfter = dateTimeFormat.format(
            entry.getLastSeenMillis());
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder sb = new StringBuilder();
        for (String relayFlag : entry.getRelayFlags()) {
          sb.append("," + relayFlag);
        }
        String relayFlags = sb.toString().substring(1);
        bw.write("r " + nickname + " " + fingerprint + " " + address + " "
            + validAfter + " " + orPort + " " + dirPort + " " + relayFlags
            + "\n");
      }
      for (Node entry : sd.getCurrentBridges().values()) {
        String fingerprint = entry.getFingerprint();
        String published = dateTimeFormat.format(
            entry.getLastSeenMillis());
        String address = entry.getAddress().equals("0.0.0.0") ? "" :
            " " + String.valueOf(entry.getAddress());
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder sb = new StringBuilder();
        for (String relayFlag : entry.getRelayFlags()) {
          sb.append("," + relayFlag);
        }
        String relayFlags = sb.toString().substring(1);
        bw.write("b " + fingerprint + address + " " + published
            + " " + orPort + " " + dirPort + " " + relayFlags + "\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write '"
          + this.internalRelaySearchDataFile.getAbsolutePath()
          + "' to disk.  Exiting.");
      e.printStackTrace();
      System.exit(1);
    }

    /* (Over-)write relay search data file. */
    try {
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      long now = System.currentTimeMillis();
      String validAfterString = dateTimeFormat.format(now);
      String freshUntilString = dateTimeFormat.format(now
          + 75L * 60L * 1000L);
      String relaysPublishedString = dateTimeFormat.format(
          sd.getLastValidAfterMillis());
      String bridgesPublishedString = dateTimeFormat.format(
          sd.getLastPublishedMillis());
      this.relaySearchDataFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.relaySearchDataFile));
      bw.write("{\"version\":1,\n"
          + "\"valid_after\":\"" + validAfterString + "\",\n"
          + "\"fresh_until\":\"" + freshUntilString + "\",\n"
          + "\"relays_published\":\"" + relaysPublishedString + "\",\n"
          + "\"relays\":[");
      int written = 0;
      for (Node entry : sd.getCurrentRelays().values()) {
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
      for (Node entry : sd.getCurrentBridges().values()) {
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

