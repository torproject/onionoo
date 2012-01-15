/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;

/* Read and write the relay search data file from/to disk. */
public class SearchDataWriter {

  private File internalRelaySearchDataFile =
      new File("status/summary.csv");
  private File relaySearchDataFile = new File("out/summary.json");
  private File relaySearchDataBackupFile =
      new File("out/summary.json.bak");

  /* Read the internal relay search data file from disk. */
  public SearchData readRelaySearchDataFile() {
    SearchData result = new SearchData();
    if (this.relaySearchDataBackupFile.exists()) {
      System.err.println("Found '"
          + relaySearchDataBackupFile.getAbsolutePath() + "' which "
          + "indicates that a previous execution did not terminate "
          + "cleanly.  Please investigate the problem, delete this file, "
          + "and try again.  Exiting.");
      System.exit(1);
    }
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
            String hashedFingerprint = parts[1];
            long publishedMillis = dateTimeFormat.parse(parts[2] + " "
               + parts[3]).getTime();
            int orPort = Integer.parseInt(parts[4]);
            int dirPort = Integer.parseInt(parts[5]);
            SortedSet<String> relayFlags = new TreeSet<String>(
                Arrays.asList(parts[6].split(",")));
            result.addBridge(hashedFingerprint, publishedMillis, orPort,
                dirPort, relayFlags);
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
  public void writeRelaySearchDataFile(SearchData sd) {

    /* Check valid-after times of known network status consensuses. */
    SortedSet<Long> allValidAfterMillis = sd.getAllValidAfterMillis();
    switch (allValidAfterMillis.size()) {
      case 0:
        System.err.println("No relay search data known that we could "
            + "write to disk.  Exiting.");
        System.exit(1);
        break;
      case 1:
        /* TODO warn if only a single consensus was added.  This could
         * mean that the download didn't work, or that the user forgot to
         * import past network status consensuses.  We might even say more
         * accurately by remembering whether we loaded search data from
         * disk or not. */
        break;
    }
    /* TODO Check valid-after times and warn if we're missing one or more
     * network status consensuses.  The user should know, so that she can
     * download and import missing network status consensuses. */

    /* Write internal relay search data file to disk. */
    try {
      this.internalRelaySearchDataFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.internalRelaySearchDataFile));
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      for (SearchEntryData entry : sd.getRelays().values()) {
        String nickname = entry.getNickname();
        String fingerprint = entry.getFingerprint();
        String address = entry.getAddress();
        String validAfter = dateTimeFormat.format(
            entry.getValidAfterMillis());
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
      for (SearchEntryData entry : sd.getBridges().values()) {
        String fingerprint = entry.getFingerprint();
        String published = dateTimeFormat.format(
            entry.getValidAfterMillis());
        String orPort = String.valueOf(entry.getOrPort());
        String dirPort = String.valueOf(entry.getDirPort());
        StringBuilder sb = new StringBuilder();
        for (String relayFlag : entry.getRelayFlags()) {
          sb.append("," + relayFlag);
        }
        String relayFlags = sb.toString().substring(1);
        bw.write("b " + fingerprint + " " + published + " "
            + orPort + " " + dirPort + " " + relayFlags + "\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write '"
          + this.internalRelaySearchDataFile.getAbsolutePath()
          + "' to disk.  Exiting.");
      e.printStackTrace();
      System.exit(1);
    }

    /* Make a backup before overwriting the relay search data file. */
    if (this.relaySearchDataFile.exists() &&
        !this.relaySearchDataFile.isDirectory()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            this.relaySearchDataFile));
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            this.relaySearchDataBackupFile));
        String line;
        while ((line = br.readLine()) != null) {
          bw.write(line + "\n");
        }
        bw.close();
        br.close();
      } catch (IOException e) {
        System.err.println("Could not create backup of '"
            + this.relaySearchDataFile.getAbsolutePath() + "'.  "
            + "Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }

    /* (Over-)write relay search data file. */
    try {
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      String validAfterString = dateTimeFormat.format(
          sd.getLastValidAfterMillis());
      String freshUntilString = dateTimeFormat.format(
          sd.getLastFreshUntilMillis());
      this.relaySearchDataFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.relaySearchDataFile));
      bw.write("{\"version\":1,\n"
          + "\"valid_after\":\"" + validAfterString + "\",\n"
          + "\"fresh_until\":\"" + freshUntilString + "\",\n"
          + "\"relays\":[");
      int written = 0;
      long lastValidAfterMillis = sd.getLastValidAfterMillis();
      for (SearchEntryData entry : sd.getRelays().values()) {
        String nickname = !entry.getNickname().equals("Unnamed") ?
            entry.getNickname() : null;
        String fingerprint = entry.getFingerprint();
        entry.setRunning(entry.getValidAfterMillis() ==
            lastValidAfterMillis);
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
      bw.write("\n],\n\"bridges\":[");
      written = 0;
      long lastPublishedMillis = sd.getLastPublishedMillis();
      for (SearchEntryData entry : sd.getBridges().values()) {
        String hashedFingerprint = entry.getFingerprint();
        entry.setRunning(entry.getValidAfterMillis() ==
            lastPublishedMillis);
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
    this.relaySearchDataBackupFile.delete();
  }
}

