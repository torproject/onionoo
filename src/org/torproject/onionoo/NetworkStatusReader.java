/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;

/* Read network statuses from disk. */
public class NetworkStatusReader {
  private File networkStatusDirectory = new File("import");
  public Set<NetworkStatusData> loadConsensuses() {
    Set<NetworkStatusData> results = new HashSet<NetworkStatusData>();
    SortedSet<File> consensusFiles = this.listFilesRecursively(
        networkStatusDirectory);
    if (!consensusFiles.isEmpty()) {
      NetworkStatusParser nsp = new NetworkStatusParser();
      for (File consensusFile : consensusFiles) {
        String consensusString = this.readFileToString(consensusFile);
        if (consensusString == null) {
          System.err.println("Could not read consensus from '"
              + consensusFile.getAbsolutePath() + "'.  Skipping.");
          continue;
        }
        NetworkStatusData nsd = nsp.parseConsensus(consensusString);
        results.add(nsd);
      }
    }
    return results;
  }
  private File bridgeNetworkStatusDirectory = new File("bridges");
  public Set<BridgeNetworkStatusData> loadBridgeNetworkStatuses() {
    Set<BridgeNetworkStatusData> results =
        new HashSet<BridgeNetworkStatusData>();
    SortedSet<File> statusFiles = this.listFilesRecursively(
        bridgeNetworkStatusDirectory);
    SimpleDateFormat fileNameFormat = new SimpleDateFormat(
        "yyyyMMdd-HHmmss");
    fileNameFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (!statusFiles.isEmpty()) {
      NetworkStatusParser nsp = new NetworkStatusParser();
      for (File statusFile : statusFiles) {
        String statusString = this.readFileToString(statusFile);
        if (statusString == null) {
          System.err.println("Could not read bridge network status from '"
              + statusFile.getAbsolutePath() + "'.  Skipping.");
          continue;
        }
        long publishedMillis = -1L;
        try {
          publishedMillis = fileNameFormat.parse(
              statusFile.getName().substring(0,
              "yyyyMMdd-HHmmss".length())).getTime();
        } catch (ParseException e) {
          System.err.println("Could not parse timestamp from bridge "
              + "network status file '" + statusFile.getAbsolutePath()
              + "'.  Skipping.");
          continue;
        }
        BridgeNetworkStatusData nsd = nsp.parseBridgeNetworkStatus(
            statusString, publishedMillis);
        results.add(nsd);
      }
    }
    return results;
  }
  private SortedSet<File> listFilesRecursively(File directory) {
    SortedSet<File> result = new TreeSet<File>();
    if (directory.exists() && directory.isDirectory()) {
      Stack<File> files = new Stack<File>();
      files.add(directory);
      while (!files.isEmpty()) {
        File file = files.pop();
        if (file.isDirectory()) {
          for (File f : file.listFiles()) {
            files.add(f);
          }
        } else {
          result.add(file);
        }
      }
    }
    return result;
  }
  private String readFileToString(File file) {
    String result = null;
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line + "\n");
      }
      br.close();
      result = sb.toString();
    } catch (IOException e) {
      System.err.println("Could not read file '" + file.getAbsolutePath()
          + "'to string.");
    }
    return result;
  }
}

