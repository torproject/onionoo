package org.torproject.onionoo.updater;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

class DescriptorDownloader {

  private final String protocolHostNameResourcePrefix =
      "https://collector.torproject.org/recent/";

  private String directory;

  private final File inDir = new File("in/recent");

  public DescriptorDownloader(DescriptorType descriptorType) {
    switch (descriptorType) {
    case RELAY_CONSENSUSES:
      this.directory = "relay-descriptors/consensuses/";
      break;
    case RELAY_SERVER_DESCRIPTORS:
      this.directory = "relay-descriptors/server-descriptors/";
      break;
    case RELAY_EXTRA_INFOS:
      this.directory = "relay-descriptors/extra-infos/";
      break;
    case EXIT_LISTS:
      this.directory = "exit-lists/";
      break;
    case BRIDGE_STATUSES:
      this.directory = "bridge-descriptors/statuses/";
      break;
    case BRIDGE_SERVER_DESCRIPTORS:
      this.directory = "bridge-descriptors/server-descriptors/";
      break;
    case BRIDGE_EXTRA_INFOS:
      this.directory = "bridge-descriptors/extra-infos/";
      break;
    case BRIDGE_POOL_ASSIGNMENTS:
      this.directory = "bridge-pool-assignments/";
      break;
    default:
      System.err.println("Unknown descriptor type.");
      return;
    }
  }

  private SortedSet<String> localFiles = new TreeSet<String>();

  public int statLocalFiles() {
    File localDirectory = new File(this.inDir, this.directory);
    if (localDirectory.exists()) {
      for (File file : localDirectory.listFiles()) {
        this.localFiles.add(file.getName());
      }
    }
    return this.localFiles.size();
  }

  private SortedSet<String> remoteFiles = new TreeSet<String>();

  public int fetchRemoteDirectory() {
    String directoryUrl = this.protocolHostNameResourcePrefix
        + this.directory;
    try {
      URL u = new URL(directoryUrl);
      HttpURLConnection huc = (HttpURLConnection) u.openConnection();
      huc.setRequestMethod("GET");
      huc.connect();
      if (huc.getResponseCode() != 200) {
        System.err.println("Could not fetch " + directoryUrl
            + ": " + huc.getResponseCode() + " "
            + huc.getResponseMessage() + ".  Skipping.");
        return 0;
      }
      BufferedReader br = new BufferedReader(new InputStreamReader(
          huc.getInputStream()));
      String line;
      while ((line = br.readLine()) != null) {
        if (!line.trim().startsWith("<tr>") ||
            !line.contains("<a href=\"")) {
          continue;
        }
        String linePart = line.substring(
            line.indexOf("<a href=\"") + "<a href=\"".length());
        if (!linePart.contains("\"")) {
          continue;
        }
        linePart = linePart.substring(0, linePart.indexOf("\""));
        if (linePart.endsWith("/")) {
          continue;
        }
        this.remoteFiles.add(linePart);
      }
      br.close();
    } catch (IOException e) {
      System.err.println("Could not fetch or parse " + directoryUrl
          + ".  Skipping.");
    }
    return this.remoteFiles.size();
  }

  public int fetchRemoteFiles() {
    int fetchedFiles = 0;
    for (String remoteFile : this.remoteFiles) {
      if (this.localFiles.contains(remoteFile)) {
        continue;
      }
      String fileUrl = this.protocolHostNameResourcePrefix
          + this.directory + remoteFile;
      File localTempFile = new File(this.inDir, this.directory
          + remoteFile + ".tmp");
      File localFile = new File(this.inDir, this.directory + remoteFile);
      try {
        localFile.getParentFile().mkdirs();
        URL u = new URL(fileUrl);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.addRequestProperty("Accept-Encoding", "gzip");
        huc.connect();
        if (huc.getResponseCode() != 200) {
          System.err.println("Could not fetch " + fileUrl
              + ": " + huc.getResponseCode() + " "
              + huc.getResponseMessage() + ".  Skipping.");
          continue;
        }
        long lastModified = huc.getHeaderFieldDate("Last-Modified", -1L);
        InputStream is;
        if (huc.getContentEncoding() != null &&
            huc.getContentEncoding().equalsIgnoreCase("gzip")) {
          is = new GZIPInputStream(huc.getInputStream());
        } else {
          is = huc.getInputStream();
        }
        BufferedInputStream bis = new BufferedInputStream(is);
        BufferedOutputStream bos = new BufferedOutputStream(
            new FileOutputStream(localTempFile));
        int len;
        byte[] data = new byte[1024];
        while ((len = bis.read(data, 0, 1024)) >= 0) {
          bos.write(data, 0, len);
        }
        bis.close();
        bos.close();
        localTempFile.renameTo(localFile);
        if (lastModified >= 0) {
          localFile.setLastModified(lastModified);
        }
        fetchedFiles++;
      } catch (IOException e) {
        System.err.println("Could not fetch or store " + fileUrl
            + ".  Skipping.");
      }
    }
    return fetchedFiles;
  }

  public int deleteOldLocalFiles() {
    int deletedFiles = 0;
    for (String localFile : this.localFiles) {
      if (!this.remoteFiles.contains(localFile)) {
        new File(this.inDir, this.directory + localFile).delete();
        deletedFiles++;
      }
    }
    return deletedFiles;
  }
}