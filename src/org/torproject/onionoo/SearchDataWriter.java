/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;

/* Read and write the relay search data file from/to disk. */
public class SearchDataWriter {

  private File internalRelaySearchDataFile =
      new File("relay-search-data.csv");
  private File relaySearchDataFile = new File("relay-search-data.json");
  private File relaySearchDataBackupFile =
      new File("relay-search-data.json.bak");

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
          if (line.startsWith("version") && !line.equals("version 1")) {
            System.err.println("Internal relay search data file is newer "
                + "than version 1.  We don't understand that.  Exiting.");
            System.exit(1);
          } else if (line.startsWith("r ")) {
            String[] parts = line.split(" ");
            if (parts.length < 5) {
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
            result.addRelay(nickname, fingerprint, address,
                validAfterMillis);
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
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.internalRelaySearchDataFile));
      bw.write("version 1\n");
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      for (SearchEntryData entry : sd.getRelays().values()) {
        String nickname = entry.getNickname();
        String fingerprint = entry.getFingerprint();
        String address = entry.getAddress();
        String validAfter = dateTimeFormat.format(
            entry.getValidAfterMillis());
        bw.write("r " + nickname + " " + fingerprint + " " + address + " "
            + validAfter + "\n");
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
          sd.getValidAfterMillis());
      String freshUntilString = dateTimeFormat.format(
          sd.getFreshUntilMillis());
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.relaySearchDataFile));
      bw.write("{\"version\":1,\n"
          + "\"valid_after\":\"" + validAfterString + "\",\n"
          + "\"fresh_until\":\"" + freshUntilString + "\",\n"
          + "\"relays\":[");
      int written = 0;
      long lastValidAfterMillis = sd.getValidAfterMillis();
      for (SearchEntryData entry : sd.getRelays().values()) {
        String nickname = !entry.getNickname().equals("Unnamed") ?
            entry.getNickname() : null;
        String fingerprint = entry.getFingerprint();
        String running = entry.getValidAfterMillis()
            == lastValidAfterMillis ? "1" : "0";
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

