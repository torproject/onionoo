/* Copyright 2016--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.UnparseableDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

class DescriptorQueue {

  private static final Logger logger = LoggerFactory.getLogger(
      DescriptorQueue.class);

  private File statusDir;

  private DescriptorReader descriptorReader;

  private File historyFile;

  private File directory;

  private Iterator<Descriptor> descriptors;

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
    this.directory = inDir;
    if (descriptorType != null) {
      this.directory = new File(inDir, descriptorType.getDir());
    }
    this.statusDir = statusDir;
    this.descriptorReader =
        DescriptorSourceFactory.createDescriptorReader();
    this.descriptorReader.setMaxDescriptorsInQueue(20);
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
            logger.error("Illegal line '{}' in parse history. Skipping line.",
                line);
          }
        }
      } catch (IOException e) {
        logger.error("Could not read history file '{}'. Not excluding "
            + "descriptors in this execution.",
            this.historyFile.getAbsolutePath(), e);
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
    if (null == this.descriptors) {
      logger.debug("Not writing history file {}, because we did not read a "
          + "single descriptor from {}.", this.historyFile, this.directory);
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
        bw.write(lastModifiedMillis + " " + absolutePath + "\n");
      }
    } catch (IOException e) {
      logger.error("Could not write history file '{}'. Not excluding "
          + "descriptors in next execution.",
          this.historyFile.getAbsolutePath());
    }
  }

  /** Returns the next parseable Descriptor, or null if there are no further
   * parseable Descriptors available. */
  public Descriptor nextDescriptor() {
    Descriptor nextDescriptor = null;
    if (null == this.descriptors) {
      if (this.directory.exists()
          && directory.isDirectory()) {
        this.descriptors = this.descriptorReader.readDescriptors(
            this.directory).iterator();
      } else {
        logger.error("Directory {} either does not exist or is not a "
            + "directory. Not adding to descriptor reader.",
            this.directory.getAbsolutePath());
        return null;
      }
    }
    while (this.descriptors.hasNext()) {
      nextDescriptor = this.descriptors.next();
      if (!(nextDescriptor instanceof UnparseableDescriptor)) {
        this.returnedDescriptors++;
        this.returnedBytes += nextDescriptor.getRawDescriptorLength();
        break;
      }
    }
    return nextDescriptor;
  }
}

