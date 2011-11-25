/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.util.*;

/* Read network statuses from disk. */
public class NetworkStatusReader {
  private File networkStatusDirectory = new File("import");
  public Set<NetworkStatusData> loadConsensuses() {
    Set<NetworkStatusData> results = new HashSet<NetworkStatusData>();
    SortedSet<File> consensusFiles = new TreeSet<File>();
    if (networkStatusDirectory.exists() &&
        networkStatusDirectory.isDirectory()) {
      Stack<File> files = new Stack<File>();
      files.add(this.networkStatusDirectory);
      while (!files.isEmpty()) {
        File file = files.pop();
        if (file.isDirectory()) {
          for (File f : file.listFiles()) {
            files.add(f);
          }
        } else {
          consensusFiles.add(file);
        }
      }
    }
    if (!consensusFiles.isEmpty()) {
      NetworkStatusParser nsp = new NetworkStatusParser();
      for (File consensusFile : consensusFiles) {
        String consensusString = null;
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              consensusFile));
          StringBuilder sb = new StringBuilder();
          String line;
          while ((line = br.readLine()) != null) {
            sb.append(line + "\n");
          }
          br.close();
          consensusString = sb.toString();
        } catch (IOException e) {
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
}

