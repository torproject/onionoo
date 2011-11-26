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
            int orPort = Integer.parseInt(rLineParts[7]);
            int dirPort = Integer.parseInt(rLineParts[8]);
            SortedSet<String> relayFlags = new TreeSet<String>(
                Arrays.asList(line.substring(2).split(" ")));
            result.addStatusEntry(nickname, fingerprint, address, orPort,
                dirPort, relayFlags);
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
  public ServerDescriptorData parseServerDescriptor(
      String descriptorString) {
    ServerDescriptorData result = new ServerDescriptorData();
    try {
      BufferedReader br = new BufferedReader(new StringReader(
          descriptorString));
      String line;
      SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss");
      dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
      List<String> exitPolicyLines = new ArrayList<String>();
      while ((line = br.readLine()) != null) {
        if (line.startsWith("platform ")) {
          String platformLine = new String(
              line.substring("platform ".length()).getBytes(),
              "US-ASCII").replaceAll("[^\\p{ASCII}]","").
              replaceAll("\\\"", "\\\\\"");
          result.setPlatformLine(platformLine);
        } else if (line.startsWith("published ")) {
          long publishedMillis = dateTimeFormat.parse(line.substring(
              "published ".length())).getTime();
          result.setPublishedMillis(publishedMillis);
        } else if (line.startsWith("opt fingerprint") ||
            line.startsWith("fingerprint")) {
          String fingerprint = line.substring(line.indexOf("fingerprint ")
              + "finerprint ".length()).replaceAll(" ", "");
          result.setFingerprint(fingerprint);
        } else if (line.startsWith("contact ")) {
          String contactLine = new String(
              line.substring("contact ".length()).getBytes(), "US-ASCII").
              replaceAll("[^\\p{ASCII}]","").replaceAll("\\\"", "\\\\\"");
          result.setContactLine(contactLine);
        } else if (line.startsWith("reject ") ||
            line.startsWith("accept ")) {
          exitPolicyLines.add(line);
        } else if (line.startsWith("family ")) {
          List<String> family = Arrays.asList(
              line.substring("family ".length()).split(" "));
          result.setFamily(family);
        } else if (line.equals("router-signature")) {
          break;
        }
      }
      result.setExitPolicyLines(exitPolicyLines);
    } catch (IOException e) {
      System.err.println("Ran into an IOException while parsing a String "
          + "in memory.  Something's really wrong.  Exiting.");
      e.printStackTrace();
      System.exit(1);
    } catch (ParseException e) {
      System.err.println("Could not parse timestamp in server descriptor "
          + "string.  Skipping.");
      e.printStackTrace();
      result = null;
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

