/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;
import org.apache.commons.codec.binary.*;

/* Parse a network status. */
public class NetworkStatusParser {
  public NetworkStatusData parseConsensus(String consensusString) {
    NetworkStatusData result = new NetworkStatusData();
    try {
      BufferedReader br = new BufferedReader(new StringReader(
          consensusString));
      String line, rLine = null;
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      while ((line = br.readLine()) != null) {
        if (line.startsWith("valid-after ")) {
          try {
            result.setValidAfterMillis(dateTimeFormat.parse(
                line.substring("valid-after ".length())).getTime());
          } catch (ParseException e) {
            System.err.println("Could not parse valid-after timestamp in "
                + "line '" + line + "' of consensus.  Skipping.");
            return null;
          }
        } else if (line.startsWith("r ")) {
          rLine = line;
        } else if (line.startsWith("s ") || line.equals("s")) {
          if (rLine == null) {
            System.err.println("s line without r line in consensus.  "
                + "Skipping");
            continue;
          }
          if (line.contains(" Running")) {
            String[] rLineParts = rLine.split(" ");
            String nickname = rLineParts[1];
            String fingerprint = Hex.encodeHexString(
                Base64.decodeBase64(rLineParts[2] + "=")).toUpperCase();
            String address = rLineParts[6];
            result.addStatusEntry(nickname, fingerprint, address);
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Ran into an IOException while parsing a String "
          + "in memory.  Something's really wrong.  Exiting.");
      System.exit(1);
    }
    return result;
  }
  public BridgeNetworkStatusData parseBridgeNetworkStatus(
      String statusString, long publishedMillis) {
    BridgeNetworkStatusData result = new BridgeNetworkStatusData();
    result.setPublishedMillis(publishedMillis);
    try {
      BufferedReader br = new BufferedReader(new StringReader(
          statusString));
      String line, rLine = null;
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      while ((line = br.readLine()) != null) {
        if (line.startsWith("r ")) {
          rLine = line;
        } else if (line.startsWith("s ") || line.equals("s")) {
          if (rLine == null) {
            System.err.println("s line without r line in consensus.  "
                + "Skipping");
            continue;
          }
          if (line.contains(" Running")) {
            String[] rLineParts = rLine.split(" ");
            String hashedFingerprint = Hex.encodeHexString(
                Base64.decodeBase64(rLineParts[2] + "=")).toUpperCase();
            result.addStatusEntry(hashedFingerprint);
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Ran into an IOException while parsing a String "
          + "in memory.  Something's really wrong.  Exiting.");
      System.exit(1);
    }
    return result;
  }
}

