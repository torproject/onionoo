/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;

/* Write status data files to disk and delete status files of relays or
 * bridges that fell out the search data list. */
public class StatusDataWriter {
  private long validAfterMillis;
  public void setValidAfterMillis(long validAfterMillis) {
    this.validAfterMillis = validAfterMillis;
  }
  private long freshUntilMillis;
  public void setFreshUntilMillis(long freshUntilMillis) {
    this.freshUntilMillis = freshUntilMillis;
  }
  private SortedMap<String, SearchEntryData> relays;
  public void setRelays(SortedMap<String, SearchEntryData> relays) {
    this.relays = relays;
  }
  private SortedMap<String, Long> bridges;
  public void setBridges(SortedMap<String, Long> bridges) {
    this.bridges = bridges;
  }
  private Map<String, ServerDescriptorData> serverDescriptors;
  public void updateRelayServerDescriptors(
      Map<String, ServerDescriptorData> serverDescriptors) {
    this.serverDescriptors = serverDescriptors;
  }
  public void writeStatusDataFiles() {
    SortedMap<String, File> remainingStatusFiles =
        this.listAllStatusFiles();
    remainingStatusFiles = this.updateRelayStatusFiles(
        remainingStatusFiles);
    remainingStatusFiles = this.updateBridgeStatusFiles(
        remainingStatusFiles);
    this.deleteStatusFiles(remainingStatusFiles);
  }
  private File statusFileDirectory = new File("www/status");
  private SortedMap<String, File> listAllStatusFiles() {
    SortedMap<String, File> result = new TreeMap<String, File>();
    if (statusFileDirectory.exists() &&
        statusFileDirectory.isDirectory()) {
      for (File file : statusFileDirectory.listFiles()) {
        if (file.getName().length() == 40) {
          result.put(file.getName(), file);
        }
      }
    }
    return result;
  }
  private SortedMap<String, File> updateRelayStatusFiles(
      SortedMap<String, File> remainingStatusFiles) {
    SortedMap<String, File> result =
        new TreeMap<String, File>(remainingStatusFiles);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    String validAfterString = dateTimeFormat.format(
        this.validAfterMillis);
    String freshUntilString = dateTimeFormat.format(
        this.freshUntilMillis);
    for (Map.Entry<String, SearchEntryData> relay :
        this.relays.entrySet()) {
      String fingerprint = relay.getKey();

      /* Read status file for this relay if it exists. */
      String descriptorParts = null;
      long publishedMillis = -1L;
      if (result.containsKey(fingerprint)) {
        File statusFile = result.remove(fingerprint);
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              statusFile));
          String line;
          boolean copyLines = false;
          StringBuilder sb = new StringBuilder();
          while ((line = br.readLine()) != null) {
            if (line.startsWith("\"desc_published\":")) {
              String published = line.substring(
                  "\"desc_published\":\"".length(),
                  "\"desc_published\":\"1970-01-01 00:00:00".length());
              publishedMillis = dateTimeFormat.parse(published).getTime();
              copyLines = true;
            }
            if (copyLines) {
              sb.append(line + "\n");
            }
          }
          br.close();
          descriptorParts = sb.toString();
        } catch (IOException e) {
          System.err.println("Could not read '"
              + statusFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        } catch (ParseException e) {
          System.err.println("Could not read '"
              + statusFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        }
      }

      /* Generate new descriptor-specific part if we have a more recent
       * descriptor. */
      if (this.serverDescriptors.containsKey(fingerprint) &&
          this.serverDescriptors.get(fingerprint).getPublishedMillis() >
          publishedMillis) {
        ServerDescriptorData descriptor = this.serverDescriptors.get(
            fingerprint);
        StringBuilder sb = new StringBuilder();
        sb.append("\"desc_published\":\""
            + dateTimeFormat.format(descriptor.getPublishedMillis())
            + "\",\n\"exit_policy\":[");
        int written = 0;
        for (String exitPolicyLine : descriptor.getExitPolicyLines()) {
          sb.append((written++ > 0 ? "," : "") + "\n  \"" + exitPolicyLine
              + "\"");
        }
        sb.append("\n],\n\"contact\":\"" + descriptor.getContactLine()
            + "\",\n\"platform\":\"" + descriptor.getPlatformLine()
            + "\"");
        if (descriptor.getFamily() != null) {
          sb.append(",\n\"family\":[");
          written = 0;
          for (String familyEntry : descriptor.getFamily()) {
            sb.append((written++ > 0 ? "," : "") + "\n  \"" + familyEntry
                + "\"");
          }
          sb.append("\n]");
        }
        descriptorParts = sb.toString();
      }

      /* Generate network-status-specific part. */
      SearchEntryData entry = relay.getValue();
      String nickname = entry.getNickname();
      String address = entry.getAddress();
      boolean running = entry.getValidAfterMillis() ==
          this.validAfterMillis;
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      StringBuilder sb = new StringBuilder();
      sb.append("{\"version\":1,\n"
          + "\"valid_after\":\"" + validAfterString + "\",\n"
          + "\"fresh_until\":\"" + freshUntilString + "\",\n"
          + "\"nickname\":\"" + nickname + "\",\n"
          + "\"fingerprint\":\"" + fingerprint + "\",\n"
          + "\"address\":[\"" + address + "\"],\n"
          + "\"or_port\":" + orPort + ",\n"
          + "\"dir_port\":" + dirPort + ",\n"
          + "\"running\":" + (running ? "true" : "false") + ",\n");
      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append("\"flags\":[");
        int written = 0;
        for (String relayFlag : relayFlags) {
          sb.append((written++ > 0 ? "," : "") + "\"" + relayFlag + "\"");
        }
        sb.append("]");
      }
      String statusParts = sb.toString();

      /* Write status file to disk. */
      File statusFile = new File(statusFileDirectory, fingerprint);
      try {
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            statusFile));
        bw.write(statusParts);
        if (descriptorParts != null) {
          bw.write(",\n" + descriptorParts);
        }
        bw.write("\n}\n");
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not write status file '"
            + statusFile.getAbsolutePath() + "'.  This file may now be "
            + "broken.  Ignoring.");
        e.printStackTrace();
      }
    }

    /* Return the files that we didn't update. */
    return result;
  }

  private SortedMap<String, File> updateBridgeStatusFiles(
      SortedMap<String, File> remainingStatusFiles) {
    System.err.println("Updating bridge status files is implemented "
        + "yet.  Skipping.");
    return remainingStatusFiles;
  }
  private void deleteStatusFiles(
      SortedMap<String, File> remainingStatusFiles) {
    for (File statusFile : remainingStatusFiles.values()) {
      statusFile.delete();
    }
  }
}

