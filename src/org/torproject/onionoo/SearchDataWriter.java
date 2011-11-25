/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;

/* Read and write the relay search data file from/to disk. */
public class SearchDataWriter {

  private File relaySearchDataFile = new File("relay-search-data.json");
  private File relaySearchDataBackupFile =
      new File("relay-search-data.json.bak");

  /* Read the relay search data file from disk. */
  /* TODO Reading and updating the search data file has a fundamental
   * flaw: there's no way to detect when a relay hasn't been running for
   * more than a week.  We need some way to store last valid-after times
   * of relays in a separate file or database.  This is fine for testing,
   * but not for production. */
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
    if (this.relaySearchDataFile.exists() &&
        !this.relaySearchDataFile.isDirectory()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            this.relaySearchDataFile));
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            this.relaySearchDataBackupFile));
        String line;
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss");
        dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        long validAfterMillis = -1L;
        while ((line = br.readLine()) != null) {
          bw.write(line + "\n");
          if (line.startsWith("\"valid_after\":\"")) {
            String validAfterString = line.substring(
                "\"valid_after\":\"".length()).substring(0,
                "yyyy-MM-dd HH:mm:ss".length());
            validAfterMillis = dateTimeFormat.parse(validAfterString).
                getTime();
            result.addValidAfterMillis(validAfterMillis);
          } else if (line.startsWith("{\"n\"")) {
            if (validAfterMillis < 0L) {
              System.err.println(
                  this.relaySearchDataFile.getAbsolutePath() + " does "
                  + "not contain a valid_after timestamp.  Exiting.");
              System.exit(1);
            }
            String nickname = "Unnamed", fingerprint = null,
                address = null;
            long lastSeen = validAfterMillis;
            for (String part : line.replaceAll("\\{", "").
                replaceAll("\\}", "").replaceAll("\\[", "").
                replaceAll("\\]", "").replaceAll("\"", "").split(",")) {
              if (part.length() < 1) {
                continue;
              }
              String key = part.substring(0, part.indexOf(":"));
              String value = part.substring(part.indexOf(":") + 1);
              if (key.equals("n")) {
                nickname = value;
              } else if (key.equals("f")) {
                fingerprint = value;
              } else if (key.equals("a")) {
                address = value;
              } else if (key.equals("r")) {
                if (!value.equals("1")) {
                  lastSeen -= 60L * 60L * 1000L; /* TODO Hack! */
                }
              }
            }
            result.addRelay(nickname, fingerprint, address, lastSeen);
          }
        }
        br.close();
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not read "
            + this.relaySearchDataFile.getAbsolutePath() + ".  Exiting.");
        e.printStackTrace();
        System.exit(1);
      } catch (ParseException e) {
        System.err.println("Could not read "
            + this.relaySearchDataFile.getAbsolutePath() + ".  Exiting.");
        e.printStackTrace();
        System.exit(1);
      }
    }
    return result;
  }

  /* Write the relay search data file to disk. */
  public void writeRelaySearchDataFile(SearchData sd) {
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

