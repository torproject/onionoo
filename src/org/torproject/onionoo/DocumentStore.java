/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

// TODO For later migration from disk to database, do the following:
// - read from database and then from disk if not found
// - write only to database, delete from disk once in database
// - move entirely to database once disk is "empty"
// TODO Also look into simple key-value stores instead of real databases.
public class DocumentStore {

  private final File statusDir = new File("status");

  private File outDir = new File("out");
  public void setOutDir(File outDir) {
    this.outDir = outDir;
  }

  private Time time;

  public DocumentStore() {
    this.time = ApplicationFactory.getTime();
  }

  private boolean listedArchivedNodeStatuses = false,
      listedCurrentNodeStatuses = false;

  private long listOperations = 0L, listedFiles = 0L, storedFiles = 0L,
      storedBytes = 0L, retrievedFiles = 0L, retrievedBytes = 0L,
      removedFiles = 0L;

  /* Node statuses are cached in memory, as opposed to all other document
   * types.  This cache is initialized when listing NodeStatus documents,
   * either including or excluding archived node statuses.  Later retrieve
   * operations depend on which NodeStatus documents were listed. */
  private SortedMap<String, NodeStatus> cachedNodeStatuses;

  public <T extends Document> SortedSet<String> list(
      Class<T> documentType, boolean includeArchive) {
    if (documentType.equals(NodeStatus.class)) {
      return this.listNodeStatuses(includeArchive);
    } else {
      return this.listDocumentFiles(documentType);
    }
  }

  private SortedSet<String> listNodeStatuses(boolean includeArchive) {
    SortedMap<String, NodeStatus> parsedNodeStatuses =
        new TreeMap<String, NodeStatus>();
    File directory = includeArchive ? this.statusDir : this.outDir;
    if (directory != null) {
      File summaryFile = new File(directory, "summary");
      if (summaryFile.exists()) {
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              summaryFile));
          String line;
          while ((line = br.readLine()) != null) {
            if (line.length() == 0) {
              continue;
            }
            NodeStatus node = NodeStatus.fromString(line);
            if (node != null) {
              parsedNodeStatuses.put(node.getFingerprint(), node);
            }
          }
          br.close();
          this.listedFiles += parsedNodeStatuses.size();
          this.listOperations++;
        } catch (IOException e) {
          System.err.println("Could not read file '"
              + summaryFile.getAbsolutePath() + "'.");
          e.printStackTrace();
          return null;
        }
      }
    }
    if (includeArchive) {
      this.listedArchivedNodeStatuses = true;
    } else {
      this.listedCurrentNodeStatuses = true;
    }
    this.cachedNodeStatuses = parsedNodeStatuses;
    return new TreeSet<String>(this.cachedNodeStatuses.keySet());
  }

  private <T extends Document> SortedSet<String> listDocumentFiles(
      Class<T> documentType) {
    SortedSet<String> fingerprints = new TreeSet<String>();
    File directory = null;
    String subdirectory = null;
    if (documentType.equals(DetailsStatus.class)) {
      directory = this.statusDir;
      subdirectory = "details";
    } else if (documentType.equals(BandwidthStatus.class)) {
      directory = this.statusDir;
      subdirectory = "bandwidth";
    } else if (documentType.equals(WeightsStatus.class)) {
      directory = this.statusDir;
      subdirectory = "weights";
    } else if (documentType.equals(ClientsStatus.class)) {
      directory = this.statusDir;
      subdirectory = "clients";
    } else if (documentType.equals(UptimeStatus.class)) {
      directory = this.statusDir;
      subdirectory = "uptimes";
    } else if (documentType.equals(DetailsDocument.class)) {
      directory = this.outDir;
      subdirectory = "details";
    } else if (documentType.equals(BandwidthDocument.class)) {
      directory = this.outDir;
      subdirectory = "bandwidth";
    } else if (documentType.equals(WeightsDocument.class)) {
      directory = this.outDir;
      subdirectory = "weights";
    } else if (documentType.equals(ClientsDocument.class)) {
      directory = this.outDir;
      subdirectory = "clients";
    } else if (documentType.equals(UptimeDocument.class)) {
      directory = this.outDir;
      subdirectory = "uptimes";
    }
    if (directory != null && subdirectory != null) {
      Stack<File> files = new Stack<File>();
      files.add(new File(directory, subdirectory));
      while (!files.isEmpty()) {
        File file = files.pop();
        if (file.isDirectory()) {
          files.addAll(Arrays.asList(file.listFiles()));
        } else if (file.getName().length() == 40) {
            fingerprints.add(file.getName());
        }
      }
    }
    this.listOperations++;
    this.listedFiles += fingerprints.size();
    return fingerprints;
  }

  public <T extends Document> boolean store(T document) {
    return this.store(document, null);
  }

  public <T extends Document> boolean store(T document,
      String fingerprint) {
    if (document instanceof NodeStatus) {
      return this.storeNodeStatus((NodeStatus) document, fingerprint);
    } else {
      return this.storeDocumentFile(document, fingerprint);
    }
  }

  private <T extends Document> boolean storeNodeStatus(
      NodeStatus nodeStatus, String fingerprint) {
    this.cachedNodeStatuses.put(fingerprint, nodeStatus);
    return true;
  }

  private <T extends Document> boolean storeDocumentFile(T document,
      String fingerprint) {
    File documentFile = this.getDocumentFile(document.getClass(),
        fingerprint);
    if (documentFile == null) {
      return false;
    }
    String documentString;
    if (document.getDocumentString() != null) {
      documentString = document.getDocumentString();
    } else if (document instanceof DetailsDocument ||
          document instanceof BandwidthDocument ||
          document instanceof WeightsDocument ||
          document instanceof ClientsDocument ||
          document instanceof UptimeDocument) {
      Gson gson = new Gson();
      documentString = gson.toJson(document);
    } else if (document instanceof BandwidthStatus ||
        document instanceof WeightsStatus ||
        document instanceof ClientsStatus ||
        document instanceof UptimeStatus) {
      documentString = document.toDocumentString();
    } else {
      System.err.println("Serializing is not supported for type "
          + document.getClass().getName() + ".");
      return false;
    }
    try {
      documentFile.getParentFile().mkdirs();
      File documentTempFile = new File(
          documentFile.getAbsolutePath() + ".tmp");
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          documentTempFile));
      bw.write(documentString);
      bw.close();
      documentFile.delete();
      documentTempFile.renameTo(documentFile);
      this.storedFiles++;
      this.storedBytes += documentString.length();
    } catch (IOException e) {
      System.err.println("Could not write file '"
          + documentFile.getAbsolutePath() + "'.");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse) {
    return this.retrieve(documentType, parse, null);
  }

  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse, String fingerprint) {
    if (documentType.equals(NodeStatus.class)) {
      return documentType.cast(this.retrieveNodeStatus(fingerprint));
    } else {
      return this.retrieveDocumentFile(documentType, parse, fingerprint);
    }
  }

  private NodeStatus retrieveNodeStatus(String fingerprint) {
    if (this.cachedNodeStatuses.containsKey(fingerprint)) {
      return this.cachedNodeStatuses.get(fingerprint);
    } else if (this.listedArchivedNodeStatuses) {
      return null;
    }
    /* TODO This is an evil hack to support looking up relays or bridges
     * that haven't been running for a week without having to load
     * 500,000 NodeStatus instances into memory.  Maybe there's a better
     * way?  Or do we need to switch to a real database for this? */
    DetailsDocument detailsDocument = this.retrieveDocumentFile(
        DetailsDocument.class, false, fingerprint);
    if (detailsDocument == null) {
      return null;
    }
    try {
      boolean isRelay = true, running = false;
      String nickname = null, address = null, countryCode = null,
          hostName = null, defaultPolicy = null, portList = null,
          aSNumber = null, contact = null;
      SortedSet<String> orAddressesAndPorts = new TreeSet<String>(),
          exitAddresses = new TreeSet<String>(),
          relayFlags = new TreeSet<String>();
      long lastSeenMillis = -1L, consensusWeight = -1L,
          lastRdnsLookup = -1L, firstSeenMillis = -1L,
          lastChangedAddresses = -1L;
      int orPort = 0, dirPort = 0;
      Boolean recommendedVersion = null;
      Scanner s = new Scanner(detailsDocument.getDocumentString());
      while (s.hasNextLine()) {
        String line = s.nextLine();
        if (!line.contains(":")) {
          continue;
        }
        String[] parts = line.split(":", 2);
        String key = parts[0], value = parts[1];
        if (key.equals("\"nickname\"")) {
          if (!value.startsWith("\"") || !value.endsWith("\",")) {
            return null;
          }
          nickname = value.substring(1, value.length() - 2);
        } else if (key.equals("\"hashed_fingerprint\"")) {
          isRelay = false;
        } else if (key.equals("\"or_addresses\"")) {
          if (!value.startsWith("[") || !value.endsWith("],")) {
            return null;
          }
          for (String addressAndPort :
              value.substring(1, value.length() - 2).split(",")) {
            if (addressAndPort.length() < 2 ||
                !addressAndPort.contains(":")) {
              return null;
            }
            if (address == null) {
              address = addressAndPort.substring(1,
                  addressAndPort.lastIndexOf(":"));
            } else {
              orAddressesAndPorts.add(addressAndPort);
            }
          }
        } else if (key.equals("\"exit_addresses\"")) {
          if (!value.startsWith("[") || !value.endsWith("],")) {
            return null;
          }
          for (String addressPart :
              value.substring(1, value.length() - 2).split(",")) {
            exitAddresses.add(addressPart);
          }
        } else if (key.equals("\"running\"")) {
          if (value.equals("true,")) {
            running = true;
          } else if (!value.equals("false,")) {
            return null;
          }
        }
      }
      NodeStatus nodeStatus = new NodeStatus(isRelay, nickname,
          fingerprint, address, orAddressesAndPorts, exitAddresses,
          lastSeenMillis, orPort, dirPort, relayFlags, consensusWeight,
          countryCode, hostName, lastRdnsLookup, defaultPolicy, portList,
          firstSeenMillis, lastChangedAddresses, aSNumber, contact,
          recommendedVersion);
      nodeStatus.setRunning(running);
      return nodeStatus;
    } catch (Exception e) {
      /* Play it safe and fall back to returning nothing. */
      return null;
    }
  }

  private <T extends Document> T retrieveDocumentFile(
      Class<T> documentType, boolean parse, String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null || !documentFile.exists()) {
      return null;
    } else if (documentFile.isDirectory()) {
      System.err.println("Could not read file '"
          + documentFile.getAbsolutePath() + "', because it is a "
          + "directory.");
      return null;
    }
    String documentString = null;
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      BufferedInputStream bis = new BufferedInputStream(
          new FileInputStream(documentFile));
      int len;
      byte[] data = new byte[1024];
      while ((len = bis.read(data, 0, 1024)) >= 0) {
        baos.write(data, 0, len);
      }
      bis.close();
      byte[] allData = baos.toByteArray();
      if (allData.length == 0) {
        return null;
      }
      documentString = new String(allData, "US-ASCII");
      this.retrievedFiles++;
      this.retrievedBytes += documentString.length();
    } catch (IOException e) {
      System.err.println("Could not read file '"
          + documentFile.getAbsolutePath() + "'.");
      e.printStackTrace();
      return null;
    }
    T result = null;
    if (!parse) {
      return this.retrieveUnparsedDocumentFile(documentType,
          documentString);
    } else if (documentType.equals(DetailsDocument.class) ||
        documentType.equals(BandwidthDocument.class) ||
        documentType.equals(WeightsDocument.class) ||
        documentType.equals(ClientsDocument.class) ||
        documentType.equals(UptimeDocument.class)) {
      return this.retrieveParsedDocumentFile(documentType,
          documentString);
    } else if (documentType.equals(BandwidthStatus.class) ||
        documentType.equals(WeightsStatus.class) ||
        documentType.equals(ClientsStatus.class) ||
        documentType.equals(UptimeStatus.class)) {
      return this.retrieveParsedStatusFile(documentType, documentString);
    } else {
      System.err.println("Parsing is not supported for type "
          + documentType.getName() + ".");
    }
    return result;
  }

  private <T extends Document> T retrieveParsedStatusFile(
      Class<T> documentType, String documentString) {
    T result = null;
    try {
      result = documentType.newInstance();
      result.fromDocumentString(documentString);
    } catch (InstantiationException e) {
      /* Handle below. */
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      /* Handle below. */
      e.printStackTrace();
    }
    if (result == null) {
      System.err.println("Could not initialize parsed status file of "
          + "type " + documentType.getName() + ".");
    }
    return result;
  }

  private <T extends Document> T retrieveParsedDocumentFile(
      Class<T> documentType, String documentString) {
    T result = null;
    Gson gson = new Gson();
    try {
      result = gson.fromJson(documentString, documentType);
    } catch (JsonParseException e) {
      /* Handle below. */
      e.printStackTrace();
    }
    if (result == null) {
      System.err.println("Could not initialize parsed document of type "
          + documentType.getName() + ".");
    }
    return result;
  }

  private <T extends Document> T retrieveUnparsedDocumentFile(
      Class<T> documentType, String documentString) {
    T result = null;
    try {
      result = documentType.newInstance();
      result.setDocumentString(documentString);
    } catch (InstantiationException e) {
      /* Handle below. */
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      /* Handle below. */
      e.printStackTrace();
    }
    if (result == null) {
      System.err.println("Could not initialize unparsed document of type "
          + documentType.getName() + ".");
    }
    return result;
  }

  public <T extends Document> boolean remove(Class<T> documentType) {
    return this.remove(documentType, null);
  }

  public <T extends Document> boolean remove(Class<T> documentType,
      String fingerprint) {
    if (documentType.equals(NodeStatus.class)) {
      return this.cachedNodeStatuses.remove(fingerprint) != null;
    } else {
      return this.removeDocumentFile(documentType, fingerprint);
    }
  }

  private <T extends Document> boolean removeDocumentFile(
      Class<T> documentType, String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null || !documentFile.delete()) {
      System.err.println("Could not delete file '"
          + documentFile.getAbsolutePath() + "'.");
      return false;
    }
    this.removedFiles++;
    return true;
  }

  private <T extends Document> File getDocumentFile(Class<T> documentType,
      String fingerprint) {
    File documentFile = null;
    if (fingerprint == null && !documentType.equals(UpdateStatus.class) &&
        !documentType.equals(UptimeStatus.class)) {
      // TODO Instead of using the update file workaround, add new method
      // lastModified(Class<T> documentType) that serves a similar
      // purpose.
      return null;
    }
    File directory = null;
    String fileName = null;
    if (documentType.equals(DetailsStatus.class)) {
      directory = this.statusDir;
      fileName = String.format("details/%s/%s/%s",
          fingerprint.substring(0, 1), fingerprint.substring(1, 2),
          fingerprint);
    } else if (documentType.equals(BandwidthStatus.class)) {
      directory = this.statusDir;
      fileName = String.format("bandwidth/%s/%s/%s",
          fingerprint.substring(0, 1), fingerprint.substring(1, 2),
          fingerprint);
    } else if (documentType.equals(WeightsStatus.class)) {
      directory = this.statusDir;
      fileName = String.format("weights/%s/%s/%s",
          fingerprint.substring(0, 1), fingerprint.substring(1, 2),
          fingerprint);
    } else if (documentType.equals(ClientsStatus.class)) {
      directory = this.statusDir;
      fileName = String.format("clients/%s/%s/%s",
          fingerprint.substring(0, 1), fingerprint.substring(1, 2),
          fingerprint);
    } else if (documentType.equals(UptimeStatus.class)) {
      directory = this.statusDir;
      if (fingerprint == null) {
        fileName = "uptime";
      } else {
        fileName = String.format("uptimes/%s/%s/%s",
            fingerprint.substring(0, 1), fingerprint.substring(1, 2),
            fingerprint);
      }
    } else if (documentType.equals(UpdateStatus.class)) {
      directory = this.outDir;
      fileName = "update";
    } else if (documentType.equals(DetailsDocument.class)) {
      directory = this.outDir;
      fileName = String.format("details/%s", fingerprint);
    } else if (documentType.equals(BandwidthDocument.class)) {
      directory = this.outDir;
      fileName = String.format("bandwidth/%s", fingerprint);
    } else if (documentType.equals(WeightsDocument.class)) {
      directory = this.outDir;
      fileName = String.format("weights/%s", fingerprint);
    } else if (documentType.equals(ClientsDocument.class)) {
      directory = this.outDir;
      fileName = String.format("clients/%s", fingerprint);
    } else if (documentType.equals(UptimeDocument.class)) {
      directory = this.outDir;
      fileName = String.format("uptimes/%s", fingerprint);
    }
    if (directory != null && fileName != null) {
      documentFile = new File(directory, fileName);
    }
    return documentFile;
  }

  public void flushDocumentCache() {
    /* Write cached node statuses to disk, and write update file
     * containing current time.  It's important to write the update file
     * now, not earlier, because the front-end should not read new node
     * statuses until all details, bandwidths, and weights are ready. */
    if (this.listedArchivedNodeStatuses) {
      this.writeNodeStatuses(false);
      this.writeNodeStatuses(true);
      this.writeUpdateStatus();
    } else if (this.listedCurrentNodeStatuses) {
      this.writeNodeStatuses(false);
      this.writeUpdateStatus();
    }
  }

  private void writeNodeStatuses(boolean includeArchive) {
    File directory = includeArchive ? this.statusDir : this.outDir;
    if (directory == null) {
      return;
    }
    File summaryFile = new File(directory, "summary");
    SortedMap<String, NodeStatus>
        cachedRelays = new TreeMap<String, NodeStatus>(),
        cachedBridges = new TreeMap<String, NodeStatus>();
    long cutoff = 0L;
    if (!includeArchive) {
      long maxLastSeenMillis = 0L;
      for (NodeStatus node : this.cachedNodeStatuses.values()) {
        if (node.getLastSeenMillis() > maxLastSeenMillis) {
          maxLastSeenMillis = node.getLastSeenMillis();
        }
      }
      cutoff = maxLastSeenMillis - DateTimeHelper.ONE_WEEK;
    }
    for (Map.Entry<String, NodeStatus> e :
        this.cachedNodeStatuses.entrySet()) {
      if (e.getValue().getLastSeenMillis() < cutoff) {
        continue;
      }
      if (e.getValue().isRelay()) {
        cachedRelays.put(e.getKey(), e.getValue());
      } else {
        cachedBridges.put(e.getKey(), e.getValue());
      }
    }
    StringBuilder sb = new StringBuilder();
    for (NodeStatus relay : cachedRelays.values()) {
      String line = relay.toString();
      if (line != null) {
        sb.append(line + "\n");
      } else {
        System.err.println("Could not serialize relay node status '"
            + relay.getFingerprint() + "'");
      }
    }
    for (NodeStatus bridge : cachedBridges.values()) {
      String line = bridge.toString();
      if (line != null) {
        sb.append(line + "\n");
      } else {
        System.err.println("Could not serialize bridge node status '"
            + bridge.getFingerprint() + "'");
      }
    }
    String documentString = sb.toString();
    try {
      summaryFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(summaryFile));
      bw.write(documentString);
      bw.close();
      this.storedFiles++;
      this.storedBytes += documentString.length();
    } catch (IOException e) {
      System.err.println("Could not write file '"
          + summaryFile.getAbsolutePath() + "'.");
      e.printStackTrace();
    }
  }

  private void writeUpdateStatus() {
    if (this.outDir == null) {
      return;
    }
    File updateFile = new File(this.outDir, "update");
    String documentString = String.valueOf(this.time.currentTimeMillis());
    try {
      updateFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(updateFile));
      bw.write(documentString);
      bw.close();
      this.storedFiles++;
      this.storedBytes += documentString.length();
    } catch (IOException e) {
      System.err.println("Could not write file '"
          + updateFile.getAbsolutePath() + "'.");
      e.printStackTrace();
    }
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + Logger.formatDecimalNumber(listOperations)
        + " list operations performed\n");
    sb.append("    " + Logger.formatDecimalNumber(listedFiles)
        + " files listed\n");
    sb.append("    " + Logger.formatDecimalNumber(storedFiles)
        + " files stored\n");
    sb.append("    " + Logger.formatBytes(storedBytes) + " stored\n");
    sb.append("    " + Logger.formatDecimalNumber(retrievedFiles)
        + " files retrieved\n");
    sb.append("    " + Logger.formatBytes(retrievedBytes)
        + " retrieved\n");
    sb.append("    " + Logger.formatDecimalNumber(removedFiles)
        + " files removed\n");
    return sb.toString();
  }
}

