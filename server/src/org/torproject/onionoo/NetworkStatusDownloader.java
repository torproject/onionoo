/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/* Download the current network status consensus from one of the directory
 * authorities. */
public class NetworkStatusDownloader {
  public NetworkStatusData downloadConsensus() {
    String consensusString = this.downloadConsensusString();
    NetworkStatusData parsedConsensus = this.parseConsensus(
        consensusString);
    return parsedConsensus;
  }
  private String downloadConsensusString() {
    List<String> authorities = new ArrayList<String>();
    authorities.add("212.112.245.170");
    authorities.add("86.59.21.38");
    authorities.add("216.224.124.114:9030");
    authorities.add("213.115.239.118:443");
    authorities.add("193.23.244.244");
    authorities.add("208.83.223.34:443");
    authorities.add("128.31.0.34:9131");
    authorities.add("194.109.206.212");
    Collections.shuffle(authorities);
    String response = null;
    for (String authority : authorities) {
      String resource = "/tor/status-vote/current/consensus.z";
      String fullUrl = "http://" + authority + resource;
      response = this.downloadFromAuthority(fullUrl);
      if (response == null) {
        System.err.println("Could not download consensus from URL "
            + fullUrl + ".  Trying the next directory authority (if "
            + "available).");
      } else {
        return response;
      }
    }
    return null;
  }
  private String downloadFromAuthority(String url) {
    DownloadRunnable downloadRunnable = new DownloadRunnable(url);
    new Thread(downloadRunnable).start();
    long started = System.currentTimeMillis(), sleep;
    while (!downloadRunnable.finished && (sleep = started + 60L * 1000L
        - System.currentTimeMillis()) > 0L) {
      try {
        Thread.sleep(sleep);
      } catch (InterruptedException e) {
        /* Do nothing. */
      }
    }
    String response = downloadRunnable.response;
    downloadRunnable.finished = true;
    return response;
  }
  private static class DownloadRunnable implements Runnable {
    Thread mainThread;
    String url;
    String response;
    boolean finished = false;
    public DownloadRunnable(String url) {
      this.mainThread = Thread.currentThread();
      this.url = url;
    }
    public void run() {
      try {
        URL u = new URL(this.url);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.connect();
        int responseCode = huc.getResponseCode();
        if (responseCode == 200) {
          BufferedInputStream in = new BufferedInputStream(
              new InflaterInputStream(huc.getInputStream()));
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          int len;
          byte[] data = new byte[1024];
          while (!this.finished &&
              (len = in.read(data, 0, 1024)) >= 0) {
            baos.write(data, 0, len);
          }
          if (this.finished) {
            return;
          }
          in.close();
          byte[] allData = baos.toByteArray();
          this.response = new String(allData);
          this.finished = true;
          this.mainThread.interrupt();
        }
      } catch (IOException e) {
        /* Can't do much except leaving this.response at null. */
      }
      this.finished = true;
    }
  }
  private NetworkStatusData parseConsensus(String consensusString) {
    NetworkStatusParser nsp = new NetworkStatusParser();
    NetworkStatusData nsd = nsp.parseConsensus(consensusString);
    return nsd;
  }
}

