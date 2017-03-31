/* Copyright 2016--2017 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

class DescriptorQueue {

  private static final Logger log = LoggerFactory.getLogger(
      DescriptorQueue.class);

  private File statusDir;

  private DescriptorReader descriptorReader;

  private File historyFile;

  private Iterator<DescriptorFile> descriptorFiles;

  private List<Descriptor> descriptors;

  private int historySizeBefore;

  public int getHistorySizeBefore() {
    return this.historySizeBefore;
  }

  private int historySizeAfter;

  public int getHistorySizeAfter() {
    return this.historySizeAfter;
  }

  private long returnedDescriptors = 0L;

  public long getReturnedDescriptors() {
    return this.returnedDescriptors;
  }

  private long returnedBytes = 0L;

  public long getReturnedBytes() {
    return this.returnedBytes;
  }

  public DescriptorQueue(File inDir, DescriptorType descriptorType,
      File statusDir) {
    File directory = inDir;
    if (descriptorType != null) {
      directory = new File(inDir, descriptorType.getDir());
    }
    this.statusDir = statusDir;
    this.descriptorReader =
        DescriptorSourceFactory.createDescriptorReader();
    this.addDirectory(directory);
  }

  private void addDirectory(File directory) {
    if (directory == null) {
      return;
    }
    if (directory.exists() && directory.isDirectory()) {
      this.descriptorReader.addDirectory(directory);
      this.descriptorReader.setMaxDescriptorFilesInQueue(1);
    } else {
      log.error("Directory " + directory.getAbsolutePath()
          + " either does not exist or is not a directory.  Not adding "
          + "to descriptor reader.");
    }
  }

  public void readHistoryFile(DescriptorHistory descriptorHistory) {
    if (this.statusDir == null) {
      return;
    }
    this.historyFile = new File(this.statusDir,
        descriptorHistory.getFileName());
    if (this.historyFile.exists() && this.historyFile.isFile()) {
      SortedMap<String, Long> excludedFiles = new TreeMap<>();
      try (BufferedReader br = new BufferedReader(new FileReader(
          this.historyFile))) {
        String line;
        while ((line = br.readLine()) != null) {
          try {
            String[] parts = line.split(" ", 2);
            excludedFiles.put(parts[1], Long.parseLong(parts[0]));
          } catch (NumberFormatException e) {
            log.error("Illegal line '" + line + "' in parse "
                + "history.  Skipping line.");
          }
        }
      } catch (IOException e) {
        log.error("Could not read history file '"
            + this.historyFile.getAbsolutePath() + "'.  Not excluding "
            + "descriptors in this execution.", e);
        return;
      }
      this.historySizeBefore = excludedFiles.size();
      this.descriptorReader.setExcludedFiles(excludedFiles);
    }
  }

  public void writeHistoryFile() {
    if (this.historyFile == null) {
      return;
    }
    SortedMap<String, Long> excludedAndParsedFiles = new TreeMap<>();
    excludedAndParsedFiles.putAll(
        this.descriptorReader.getExcludedFiles());
    excludedAndParsedFiles.putAll(this.descriptorReader.getParsedFiles());
    this.historySizeAfter = excludedAndParsedFiles.size();
    this.historyFile.getParentFile().mkdirs();
    try (BufferedWriter bw = new BufferedWriter(new FileWriter(
        this.historyFile))) {
      for (Map.Entry<String, Long> e : excludedAndParsedFiles.entrySet()) {
        String absolutePath = e.getKey();
        long lastModifiedMillis = e.getValue();
        bw.write(String.valueOf(lastModifiedMillis) + " " + absolutePath
            + "\n");
      }
    } catch (IOException e) {
      log.error("Could not write history file '"
          + this.historyFile.getAbsolutePath() + "'.  Not excluding "
          + "descriptors in next execution.");
    }
  }

  public Descriptor nextDescriptor() {
    Descriptor nextDescriptor = null;
    if (this.descriptorFiles == null) {
      this.descriptorFiles = this.descriptorReader.readDescriptors();
    }
    while (this.descriptors == null && this.descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = this.descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        log.error("Could not parse " + descriptorFile.getFileName(), 
            descriptorFile.getException());
      }
      if (descriptorFile.getDescriptors() != null
          && !descriptorFile.getDescriptors().isEmpty()) {
        this.descriptors = descriptorFile.getDescriptors();
      }
    }
    if (this.descriptors != null) {
      nextDescriptor = this.descriptors.remove(0);
      this.returnedDescriptors++;
      this.returnedBytes += nextDescriptor.getRawDescriptorBytes().length;
      if (this.descriptors.isEmpty()) {
        this.descriptors = null;
      }
    }
    return nextDescriptor;
  }
}

