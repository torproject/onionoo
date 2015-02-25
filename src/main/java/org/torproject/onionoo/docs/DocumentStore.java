/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.onionoo.util.FormattingUtils;
import org.torproject.onionoo.util.Time;
import org.torproject.onionoo.util.TimeFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

// TODO For later migration from disk to database, do the following:
// - read from database and then from disk if not found
// - write only to database, delete from disk once in database
// - move entirely to database once disk is "empty"
// TODO Also look into simple key-value stores instead of real databases.
public class DocumentStore {

  private static Logger log = LoggerFactory.getLogger(
      DocumentStore.class);

  private final File statusDir = new File("status");

  private File outDir = new File("out");
  public void setOutDir(File outDir) throws FileNotFoundException {
    if (!outDir.exists() || !outDir.isDirectory()) {
      throw new FileNotFoundException("Cannot access directory "
          + outDir);
    }
    this.outDir = outDir;
  }

  private Time time;

  public DocumentStore() {
    this.time = TimeFactory.getTime();
  }

  private long listOperations = 0L, listedFiles = 0L, storedFiles = 0L,
      storedBytes = 0L, retrievedFiles = 0L, retrievedBytes = 0L,
      removedFiles = 0L;

  /* Node statuses and summary documents are cached in memory, as opposed
   * to all other document types.  These caches are initialized when first
   * accessing or modifying a NodeStatus or SummaryDocument document,
   * respectively. */
  private SortedMap<String, NodeStatus> cachedNodeStatuses;
  private SortedMap<String, SummaryDocument> cachedSummaryDocuments;

  /* Last-modified timestamp of cached network statuses and summary
   * documents when reading them from disk. */
  private long lastModifiedNodeStatuses = 0L;
  private long lastModifiedSummaryDocuments = 0L;

  /* Fingerprints of updated node statuses and summary documents that are
   * not yet written to disk. */
  private SortedSet<String> updatedNodeStatuses;
  private SortedSet<String> updatedSummaryDocuments;

  public <T extends Document> SortedSet<String> list(
      Class<T> documentType) {
    return this.list(documentType, 0L);
  }

  public <T extends Document> SortedSet<String> list(
      Class<T> documentType, long updatedAfter) {
    if (documentType.equals(NodeStatus.class)) {
      return this.listNodeStatuses(updatedAfter);
    } else if (documentType.equals(SummaryDocument.class)) {
      return this.listSummaryDocuments(updatedAfter);
    } else {
      return this.listDocumentFiles(documentType, updatedAfter);
    }
  }

  private SortedSet<String> listNodeStatuses(long updatedAfter) {
    if (this.cachedNodeStatuses == null) {
      this.cacheNodeStatuses();
    }
    if (updatedAfter >= this.lastModifiedNodeStatuses) {
      return new TreeSet<String>(this.updatedNodeStatuses);
    } else {
      return new TreeSet<String>(this.cachedNodeStatuses.keySet());
    }
  }

  private void cacheNodeStatuses() {
    SortedMap<String, NodeStatus> parsedNodeStatuses =
        new TreeMap<String, NodeStatus>();
    File directory = this.statusDir;
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
          this.lastModifiedNodeStatuses = summaryFile.lastModified();
          this.listedFiles += parsedNodeStatuses.size();
          this.listOperations++;
        } catch (IOException e) {
          log.error("Could not read file '"
              + summaryFile.getAbsolutePath() + "'.", e);
        }
      }
    }
    this.cachedNodeStatuses = parsedNodeStatuses;
    this.updatedNodeStatuses = new TreeSet<String>();
  }

  private SortedSet<String> listSummaryDocuments(long updatedAfter) {
    if (this.cachedSummaryDocuments == null) {
      this.cacheSummaryDocuments();
    }
    if (updatedAfter >= this.lastModifiedSummaryDocuments) {
      return new TreeSet<String>(this.updatedSummaryDocuments);
    } else {
      return new TreeSet<String>(this.cachedSummaryDocuments.keySet());
    }
  }

  private void cacheSummaryDocuments() {
    SortedMap<String, SummaryDocument> parsedSummaryDocuments =
        new TreeMap<String, SummaryDocument>();
    File directory = this.outDir;
    if (directory != null) {
      File summaryFile = new File(directory, "summary");
      if (summaryFile.exists()) {
        String line = null;
        try {
          Gson gson = new Gson();
          BufferedReader br = new BufferedReader(new FileReader(
              summaryFile));
          while ((line = br.readLine()) != null) {
            if (line.length() == 0) {
              continue;
            }
            SummaryDocument summaryDocument = gson.fromJson(line,
                SummaryDocument.class);
            if (summaryDocument != null) {
              parsedSummaryDocuments.put(summaryDocument.getFingerprint(),
                  summaryDocument);
            }
          }
          br.close();
          this.lastModifiedSummaryDocuments = summaryFile.lastModified();
          this.listedFiles += parsedSummaryDocuments.size();
          this.listOperations++;
        } catch (IOException e) {
          log.error("Could not read file '"
              + summaryFile.getAbsolutePath() + "'.", e);
        } catch (JsonParseException e) {
          log.error("Could not parse summary document '" + line
              + "' in file '" + summaryFile.getAbsolutePath() + "'.", e);
        }
      }
    }
    this.cachedSummaryDocuments = parsedSummaryDocuments;
    this.updatedSummaryDocuments = new TreeSet<String>();
  }

  private <T extends Document> SortedSet<String> listDocumentFiles(
      Class<T> documentType, long updatedAfter) {
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
        } else if (file.getName().length() == 40 &&
            (updatedAfter == 0L || file.lastModified() > updatedAfter)) {
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
    } else if (document instanceof SummaryDocument) {
      return this.storeSummaryDocument((SummaryDocument) document,
          fingerprint);
    } else {
      return this.storeDocumentFile(document, fingerprint);
    }
  }

  private <T extends Document> boolean storeNodeStatus(
      NodeStatus nodeStatus, String fingerprint) {
    if (this.cachedNodeStatuses == null) {
      this.cacheNodeStatuses();
    }
    this.updatedNodeStatuses.add(fingerprint);
    this.cachedNodeStatuses.put(fingerprint, nodeStatus);
    return true;
  }

  private <T extends Document> boolean storeSummaryDocument(
      SummaryDocument summaryDocument, String fingerprint) {
    if (this.cachedSummaryDocuments == null) {
      this.cacheSummaryDocuments();
    }
    this.updatedSummaryDocuments.add(fingerprint);
    this.cachedSummaryDocuments.put(fingerprint, summaryDocument);
    return true;
  }

  private static final long ONE_BYTE = 1L,
      ONE_KIBIBYTE = 1024L * ONE_BYTE,
      ONE_MIBIBYTE = 1024L * ONE_KIBIBYTE;

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
    } else if (document instanceof BandwidthDocument ||
          document instanceof WeightsDocument ||
          document instanceof ClientsDocument ||
          document instanceof UptimeDocument) {
      Gson gson = new Gson();
      documentString = gson.toJson(document);
    } else if (document instanceof DetailsStatus ||
        document instanceof DetailsDocument) {
      /* Don't escape HTML characters, like < and >, contained in
       * strings. */
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      /* We must ensure that details files only contain ASCII characters
       * and no UTF-8 characters.  While UTF-8 characters are perfectly
       * valid in JSON, this would break compatibility with existing files
       * pretty badly.  We already make sure that all strings in details
       * objects are escaped JSON, e.g., \u00F2.  When Gson serlializes
       * this string, it escapes the \ to \\, hence writes \\u00F2.  We
       * need to undo this and change \\u00F2 back to \u00F2. */
      documentString = StringUtils.replace(gson.toJson(document),
          "\\\\u", "\\u");
      /* Existing details statuses don't contain opening and closing curly
       * brackets, so we should remove them from new details statuses,
       * too. */
       if (document instanceof DetailsStatus) {
         documentString = documentString.substring(
             documentString.indexOf("{") + 1,
             documentString.lastIndexOf("}"));
       }
    } else if (document instanceof BandwidthStatus ||
        document instanceof WeightsStatus ||
        document instanceof ClientsStatus ||
        document instanceof UptimeStatus ||
        document instanceof UpdateStatus) {
      documentString = document.toDocumentString();
    } else {
      log.error("Serializing is not supported for type "
          + document.getClass().getName() + ".");
      return false;
    }
    try {
      if (documentString.length() > ONE_MIBIBYTE) {
        log.warn("Attempting to store very large document file: path='"
            + documentFile.getAbsolutePath() + "', bytes="
            + documentString.length());
      }
      documentFile.getParentFile().mkdirs();
      File documentTempFile = new File(
          documentFile.getAbsolutePath() + ".tmp");
      writeToFile(documentTempFile, documentString);
      documentFile.delete();
      documentTempFile.renameTo(documentFile);
      this.storedFiles++;
      this.storedBytes += documentString.length();
    } catch (IOException e) {
      log.error("Could not write file '"
          + documentFile.getAbsolutePath() + "'.", e);
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
    } else if (documentType.equals(SummaryDocument.class)) {
      return documentType.cast(this.retrieveSummaryDocument(fingerprint));
    } else {
      return this.retrieveDocumentFile(documentType, parse, fingerprint);
    }
  }

  private NodeStatus retrieveNodeStatus(String fingerprint) {
    if (this.cachedNodeStatuses == null) {
      this.cacheNodeStatuses();
    }
    return this.cachedNodeStatuses.get(fingerprint);
  }

  private SummaryDocument retrieveSummaryDocument(String fingerprint) {
    if (this.cachedSummaryDocuments == null) {
      this.cacheSummaryDocuments();
    }
    if (this.cachedSummaryDocuments.containsKey(fingerprint)) {
      return this.cachedSummaryDocuments.get(fingerprint);
    }
    /* TODO This is an evil hack to support looking up relays or bridges
     * that haven't been running for a week without having to load
     * 500,000 NodeStatus instances into memory.  Maybe there's a better
     * way?  Or do we need to switch to a real database for this? */
    DetailsDocument detailsDocument = this.retrieveDocumentFile(
        DetailsDocument.class, true, fingerprint);
    if (detailsDocument == null) {
      /* There is no details document available that we could serve as
       * basis for generating a summary document on-the-fly.  Nothing to
       * worry about. */
      return null;
    }
    boolean isRelay = detailsDocument.getHashedFingerprint() == null;
    boolean running = false;
    String nickname = detailsDocument.getNickname();
    List<String> addresses = new ArrayList<String>();
    String countryCode = null, aSNumber = null, contact = null;
    for (String orAddressAndPort : detailsDocument.getOrAddresses()) {
      if (!orAddressAndPort.contains(":")) {
        log.warn("Attempt to create summary document from details "
            + "document for fingerprint " + fingerprint + " failed "
            + "because of invalid OR address/port: '" + orAddressAndPort
            + "'.  Not returning a summary document in this case.");
        return null;
      }
      String orAddress = orAddressAndPort.substring(0,
          orAddressAndPort.lastIndexOf(":"));
      if (!addresses.contains(orAddress)) {
        addresses.add(orAddress);
      }
    }
    if (detailsDocument.getExitAddresses() != null) {
      for (String exitAddress : detailsDocument.getExitAddresses()) {
        if (!addresses.contains(exitAddress)) {
          addresses.add(exitAddress);
        }
      }
    }
    SortedSet<String> relayFlags = new TreeSet<String>(), family = null;
    long lastSeenMillis = -1L, consensusWeight = -1L,
        firstSeenMillis = -1L;
    SummaryDocument summaryDocument = new SummaryDocument(isRelay,
        nickname, fingerprint, addresses, lastSeenMillis, running,
        relayFlags, consensusWeight, countryCode, firstSeenMillis,
        aSNumber, contact, family);
    return summaryDocument;
  }

  private <T extends Document> T retrieveDocumentFile(
      Class<T> documentType, boolean parse, String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null || !documentFile.exists()) {
      /* Document file does not exist.  That's okay. */
      return null;
    } else if (documentFile.isDirectory()) {
      log.error("Could not read file '"
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
        /* Document file is empty. */
        return null;
      }
      documentString = new String(allData, "US-ASCII");
      this.retrievedFiles++;
      this.retrievedBytes += documentString.length();
    } catch (IOException e) {
      log.error("Could not read file '"
          + documentFile.getAbsolutePath() + "'.", e);
      return null;
    }
    if (documentString.length() > ONE_MIBIBYTE) {
      log.warn("Retrieved very large document file: path='"
          + documentFile.getAbsolutePath() + "', bytes="
          + documentString.length());
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
        documentType.equals(UptimeStatus.class) ||
        documentType.equals(UpdateStatus.class)) {
      return this.retrieveParsedStatusFile(documentType, documentString);
    } else if (documentType.equals(DetailsStatus.class)) {
      return this.retrieveParsedDocumentFile(documentType, "{"
          + documentString + "}");
    } else {
      log.error("Parsing is not supported for type "
          + documentType.getName() + ".");
    }
    return result;
  }

  private <T extends Document> T retrieveParsedStatusFile(
      Class<T> documentType, String documentString) {
    T result = null;
    try {
      result = documentType.newInstance();
      result.setFromDocumentString(documentString);
    } catch (InstantiationException e) {
      /* Handle below. */
      log.error(e.getMessage(), e);
    } catch (IllegalAccessException e) {
      /* Handle below. */
      log.error(e.getMessage(), e);
    }
    if (result == null) {
      log.error("Could not initialize parsed status file of "
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
      log.error(e.getMessage(), e);
    }
    if (result == null) {
      log.error("Could not initialize parsed document of type "
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
      log.error(e.getMessage(), e);
    } catch (IllegalAccessException e) {
      /* Handle below. */
      log.error(e.getMessage(), e);
    }
    if (result == null) {
      log.error("Could not initialize unparsed document of type "
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
      return this.removeNodeStatus(fingerprint);
    } else if (documentType.equals(SummaryDocument.class)) {
      return this.removeSummaryDocument(fingerprint);
    } else {
      return this.removeDocumentFile(documentType, fingerprint);
    }
  }

  private boolean removeNodeStatus(String fingerprint) {
    if (this.cachedNodeStatuses == null) {
      this.cacheNodeStatuses();
    }
    this.updatedNodeStatuses.remove(fingerprint);
    return this.cachedNodeStatuses.remove(fingerprint) != null;
  }

  private boolean removeSummaryDocument(String fingerprint) {
    if (this.cachedSummaryDocuments == null) {
      this.cacheSummaryDocuments();
    }
    this.updatedSummaryDocuments.remove(fingerprint);
    return this.cachedSummaryDocuments.remove(fingerprint) != null;
  }

  private <T extends Document> boolean removeDocumentFile(
      Class<T> documentType, String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null || !documentFile.delete()) {
      log.error("Could not delete file '"
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
      log.warn("Attempted to locate a document file of type "
          + documentType.getName() + " without providing a fingerprint.  "
          + "Such a file does not exist.");
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
    if (this.cachedNodeStatuses != null ||
        this.cachedSummaryDocuments != null) {
      if (this.cachedNodeStatuses != null) {
        this.writeNodeStatuses();
      }
      if (this.cachedSummaryDocuments != null) {
        this.writeSummaryDocuments();
      }
      this.writeUpdateStatus();
    }
  }

  public void invalidateDocumentCache() {
    this.cachedNodeStatuses = null;
    this.cachedSummaryDocuments = null;
    this.lastModifiedNodeStatuses = 0L;
    this.lastModifiedSummaryDocuments = 0L;
    this.updatedNodeStatuses = null;
    this.updatedSummaryDocuments = null;
  }

  private void writeNodeStatuses() {
    File directory = this.statusDir;
    if (directory == null) {
      log.error("Unable to write node statuses without knowing the "
          + "'status' directory to write to!");
      return;
    }
    File summaryFile = new File(directory, "summary");
    SortedMap<String, NodeStatus>
        cachedRelays = new TreeMap<String, NodeStatus>(),
        cachedBridges = new TreeMap<String, NodeStatus>();
    for (Map.Entry<String, NodeStatus> e :
        this.cachedNodeStatuses.entrySet()) {
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
        log.error("Could not serialize relay node status '"
            + relay.getFingerprint() + "'");
      }
    }
    for (NodeStatus bridge : cachedBridges.values()) {
      String line = bridge.toString();
      if (line != null) {
        sb.append(line + "\n");
      } else {
        log.error("Could not serialize bridge node status '"
            + bridge.getFingerprint() + "'");
      }
    }
    String documentString = sb.toString();
    try {
      summaryFile.getParentFile().mkdirs();
      writeToFile(summaryFile, documentString);
      this.lastModifiedNodeStatuses = summaryFile.lastModified();
      this.updatedNodeStatuses.clear();
      this.storedFiles++;
      this.storedBytes += documentString.length();
    } catch (IOException e) {
      log.error("Could not write file '"
          + summaryFile.getAbsolutePath() + "'.", e);
    }
  }

  private static void writeToFile(File file, String content)
      throws IOException {
    BufferedOutputStream bos = new BufferedOutputStream(
        new FileOutputStream(file));
    bos.write(content.getBytes("US-ASCII"));
    bos.close();
  }

  private void writeSummaryDocuments() {
    StringBuilder sb = new StringBuilder();
    Gson gson = new Gson();
    for (SummaryDocument summaryDocument :
        this.cachedSummaryDocuments.values()) {
      String line = gson.toJson(summaryDocument);
      if (line != null) {
        sb.append(line + "\n");
      } else {
        log.error("Could not serialize relay summary document '"
            + summaryDocument.getFingerprint() + "'");
      }
    }
    String documentString = sb.toString();
    File summaryFile = new File(this.outDir, "summary");
    try {
      summaryFile.getParentFile().mkdirs();
      writeToFile(summaryFile, documentString);
      this.lastModifiedSummaryDocuments = summaryFile.lastModified();
      this.updatedSummaryDocuments.clear();
      this.storedFiles++;
      this.storedBytes += documentString.length();
    } catch (IOException e) {
      log.error("Could not write file '"
          + summaryFile.getAbsolutePath() + "'.", e);
    }
  }

  private void writeUpdateStatus() {
    if (this.outDir == null) {
      log.error("Unable to write update status file without knowing the "
          + "'out' directory to write to!");
      return;
    }
    UpdateStatus updateStatus = new UpdateStatus();
    updateStatus.setUpdatedMillis(this.time.currentTimeMillis());
    this.store(updateStatus);
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + FormattingUtils.formatDecimalNumber(listOperations)
        + " list operations performed\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(listedFiles)
        + " files listed\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(storedFiles)
        + " files stored\n");
    sb.append("    " + FormattingUtils.formatBytes(storedBytes)
        + " stored\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(retrievedFiles)
        + " files retrieved\n");
    sb.append("    " + FormattingUtils.formatBytes(retrievedBytes)
        + " retrieved\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(removedFiles)
        + " files removed\n");
    return sb.toString();
  }
}

