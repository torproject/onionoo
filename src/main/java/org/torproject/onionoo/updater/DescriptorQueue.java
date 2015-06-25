package org.torproject.onionoo.updater;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;

class DescriptorQueue {

  private final static Logger log = LoggerFactory.getLogger(
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
      directory = this.getDirectoryForDescriptorType(inDir,
          descriptorType);
    }
    this.statusDir = statusDir;
    this.descriptorReader =
        DescriptorSourceFactory.createDescriptorReader();
    this.addDirectory(directory);
  }

  public File getDirectoryForDescriptorType(File inDir,
      DescriptorType descriptorType) {
    String directoryName = null;
    switch (descriptorType) {
    case RELAY_CONSENSUSES:
      directoryName = "relay-descriptors/consensuses";
      break;
    case RELAY_SERVER_DESCRIPTORS:
      directoryName = "relay-descriptors/server-descriptors";
      break;
    case RELAY_EXTRA_INFOS:
      directoryName = "relay-descriptors/extra-infos";
      break;
    case BRIDGE_STATUSES:
      directoryName = "bridge-descriptors/statuses";
      break;
    case BRIDGE_SERVER_DESCRIPTORS:
      directoryName = "bridge-descriptors/server-descriptors";
      break;
    case BRIDGE_EXTRA_INFOS:
      directoryName = "bridge-descriptors/extra-infos";
      break;
    case EXIT_LISTS:
      directoryName = "exit-lists";
      break;
    default:
      log.error("Unknown descriptor type.  Not adding directory "
          + "to descriptor reader.");
      return null;
    }
    File directory = new File(inDir, directoryName);
    return directory;
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
    String historyFileName = null;
    switch (descriptorHistory) {
    case RELAY_EXTRAINFO_HISTORY:
      historyFileName = "relay-extrainfo-history";
      break;
    case BRIDGE_EXTRAINFO_HISTORY:
      historyFileName = "bridge-extrainfo-history";
      break;
    case EXIT_LIST_HISTORY:
      historyFileName = "exit-list-history";
      break;
    case RELAY_CONSENSUS_HISTORY:
      historyFileName = "relay-consensus-history";
      break;
    case BRIDGE_STATUS_HISTORY:
      historyFileName = "bridge-status-history";
      break;
    case RELAY_SERVER_HISTORY:
      historyFileName = "relay-server-history";
      break;
    case BRIDGE_SERVER_HISTORY:
      historyFileName = "bridge-server-history";
      break;
    default:
      log.error("Unknown descriptor history.  Not excluding "
          + "files.");
      return;
    }
    this.historyFile = new File(this.statusDir, historyFileName);
    if (this.historyFile.exists() && this.historyFile.isFile()) {
      SortedMap<String, Long> excludedFiles = new TreeMap<String, Long>();
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            this.historyFile));
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
        br.close();
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
    SortedMap<String, Long> excludedAndParsedFiles =
        new TreeMap<String, Long>();
    excludedAndParsedFiles.putAll(
        this.descriptorReader.getExcludedFiles());
    excludedAndParsedFiles.putAll(this.descriptorReader.getParsedFiles());
    this.historySizeAfter = excludedAndParsedFiles.size();
    try {
      this.historyFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          this.historyFile));
      for (Map.Entry<String, Long> e : excludedAndParsedFiles.entrySet()) {
        String absolutePath = e.getKey();
        long lastModifiedMillis = e.getValue();
        bw.write(String.valueOf(lastModifiedMillis) + " " + absolutePath
            + "\n");
      }
      bw.close();
    } catch (IOException e) {
      log.error("Could not write history file '"
          + this.historyFile.getAbsolutePath() + "'.  Not excluding "
          + "descriptors in next execution.");
      return;
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
      if (descriptorFile.getDescriptors() != null &&
          !descriptorFile.getDescriptors().isEmpty()) {
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
