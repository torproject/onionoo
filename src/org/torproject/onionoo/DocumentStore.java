/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

enum DocumentType {
  STATUS_SUMMARY,
  STATUS_BANDWIDTH,
  STATUS_WEIGHTS,
  OUT_UPDATE,
  OUT_SUMMARY,
  OUT_DETAILS,
  OUT_BANDWIDTH,
  OUT_WEIGHTS;
}

// TODO For later migration from disk to database, do the following:
// - read from database and then from disk if not found
// - write only to database, delete from disk once in database
// - move entirely to database once disk is "empty"
// TODO Also look into simple key-value stores instead of real databases.
public class DocumentStore {

  private File statusDir;

  private File outDir;

  long listOperations = 0L, listedFiles = 0L, storedFiles = 0L,
      storedBytes = 0L, retrievedFiles = 0L, retrievedBytes = 0L,
      removedFiles = 0L;

  public DocumentStore(File outDir) {
    this.outDir = outDir;
  }

  public DocumentStore(File statusDir, File outDir) {
    this.statusDir = statusDir;
    this.outDir = outDir;
  }

  public SortedSet<String> list(DocumentType documentType) {
    SortedSet<String> fingerprints = new TreeSet<String>();
    File directory = null;
    String subdirectory = null;
    switch (documentType) {
    case STATUS_BANDWIDTH:
      directory = this.statusDir;
      subdirectory = "bandwidth";
      break;
    case STATUS_WEIGHTS:
      directory = this.statusDir;
      subdirectory = "weights";
      break;
    case OUT_DETAILS:
      directory = this.outDir;
      subdirectory = "details";
      break;
    case OUT_BANDWIDTH:
      directory = this.outDir;
      subdirectory = "bandwidth";
      break;
    case OUT_WEIGHTS:
      directory = this.outDir;
      break;
    default:
      break;
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

  public boolean store(String documentString, DocumentType documentType) {
    return this.store(documentString, documentType, null);
  }

  public boolean store(String documentString, DocumentType documentType,
      String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null) {
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

  public String retrieve(DocumentType documentType) {
    return this.retrieve(documentType, null);
  }

  public String retrieve(DocumentType documentType, String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null || !documentFile.exists()) {
      return null;
    } else if (documentFile.isDirectory()) {
      System.err.println("Could not read file '"
          + documentFile.getAbsolutePath() + "', because it is a "
          + "directory.");
      return null;
    }
    try {
      BufferedReader br = new BufferedReader(new FileReader(
          documentFile));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        sb.append(line + "\n");
      }
      br.close();
      this.retrievedFiles++;
      this.retrievedBytes += sb.length();
      return sb.toString();
    } catch (IOException e) {
      System.err.println("Could not read file '"
          + documentFile.getAbsolutePath() + "'.");
      e.printStackTrace();
      return null;
    }
  }

  public boolean remove(DocumentType documentType) {
    return this.remove(documentType, null);
  }

  public boolean remove(DocumentType documentType, String fingerprint) {
    File documentFile = this.getDocumentFile(documentType, fingerprint);
    if (documentFile == null || !documentFile.delete()) {
      System.err.println("Could not delete file '"
          + documentFile.getAbsolutePath() + "'.");
      return false;
    }
    this.removedFiles++;
    return true;
  }

  private File getDocumentFile(DocumentType documentType,
      String fingerprint) {
    File documentFile = null;
    if (fingerprint == null && !(
        documentType == DocumentType.STATUS_SUMMARY ||
        documentType == DocumentType.OUT_UPDATE||
        documentType == DocumentType.OUT_SUMMARY)) {
      return null;
    }
    File directory = null;
    String fileName = null;
    switch (documentType) {
    case STATUS_SUMMARY:
      directory = this.statusDir;
      fileName = "summary";
      break;
    case STATUS_BANDWIDTH:
      directory = this.statusDir;
      fileName = String.format("bandwidth/%s/%s/%s",
          fingerprint.substring(0, 1), fingerprint.substring(1, 2),
          fingerprint);
      break;
    case STATUS_WEIGHTS:
      directory = this.statusDir;
      fileName = String.format("weights/%s/%s/%s",
          fingerprint.substring(0, 1), fingerprint.substring(1, 2),
          fingerprint);
      break;
    case OUT_UPDATE:
      directory = this.outDir;
      fileName = "update";
      break;
    case OUT_SUMMARY:
      directory = this.outDir;
      fileName = "summary";
      break;
    case OUT_DETAILS:
      directory = this.outDir;
      fileName = String.format("details/%s", fingerprint);
      break;
    case OUT_BANDWIDTH:
      directory = this.outDir;
      fileName = String.format("bandwidth/%s", fingerprint);
      break;
    case OUT_WEIGHTS:
      directory = this.outDir;
      fileName = String.format("weights/%s", fingerprint);
      break;
    }
    if (directory != null && fileName != null) {
      documentFile = new File(directory, fileName);
    }
    return documentFile;
  }

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + formatDecimalNumber(listOperations)
        + " list operations performed\n");
    sb.append("    " + formatDecimalNumber(listedFiles)
        + " files listed\n");
    sb.append("    " + formatDecimalNumber(storedFiles)
        + " files stored\n");
    sb.append("    " + formatBytes(storedBytes) + " stored\n");
    sb.append("    " + formatDecimalNumber(retrievedFiles)
        + " files retrieved\n");
    sb.append("    " + formatBytes(retrievedBytes) + " retrieved\n");
    sb.append("    " + formatDecimalNumber(removedFiles)
        + " files removed\n");
    return sb.toString();
  }

  //TODO This method should go into a utility class.
  private static String formatDecimalNumber(long decimalNumber) {
    return String.format("%,d", decimalNumber);
  }

  // TODO This method should go into a utility class.
  private static String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    } else {
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      return String.format("%.1f %siB", bytes / Math.pow(1024, exp),
          "KMGTPE".charAt(exp-1));
    }
  }
}

