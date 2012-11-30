/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ExtraInfoDescriptor;

/* Write bandwidth data files to disk and delete bandwidth files of relays
 * or bridges that fell out of the summary list.
 *
 * Bandwidth history data is available in different resolutions, depending
 * on the considered time interval.  Data for the past 72 hours is
 * available for 15 minute detail, data for the past week in 1 hour
 * detail, data for the past month in 4 hour detail, data for the past 3
 * months in 12 hour detail, data for the past year in 2 day detail, and
 * earlier data in 10 day detail.  These detail levels have been chosen to
 * provide between 92 and 192 data points for graphing the bandwidth of
 * the past day, past week, past month, past three months, past year, and
 * past five years.
 *
 * Only update bandwidth data files for which new bandwidth histories are
 * available.  There's no point in updating bandwidth documents when we
 * don't have newer bandwidth data to add.  This means that, e.g., the
 * last 3 days in the bandwidth document may not be equivalent to the last
 * 3 days as of publishing the document, but that's something clients can
 * work around. */
public class BandwidthDataWriter {

  private SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
      "yyyy-MM-dd HH:mm:ss");
  public BandwidthDataWriter() {
    this.dateTimeFormat.setLenient(false);
    this.dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private SortedSet<String> currentFingerprints = new TreeSet<String>();
  public void setCurrentRelays(SortedMap<String, Node> currentRelays) {
    this.currentFingerprints.addAll(currentRelays.keySet());
  }
  public void setCurrentBridges(SortedMap<String, Node> currentBridges) {
    this.currentFingerprints.addAll(currentBridges.keySet());
  }

  public void readExtraInfoDescriptors() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/relay-descriptors/extra-infos"));
    reader.addDirectory(new File("in/bridge-descriptors/extra-infos"));
    reader.setExcludeFiles(new File("status/extrainfo-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getException() != null) {
        System.out.println("Could not parse "
            + descriptorFile.getFileName());
        descriptorFile.getException().printStackTrace();
      }
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ExtraInfoDescriptor) {
            ExtraInfoDescriptor extraInfoDescriptor =
                (ExtraInfoDescriptor) descriptor;
            this.parseDescriptor(extraInfoDescriptor);
          }
        }
      }
    }
  }

  private void parseDescriptor(ExtraInfoDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    boolean updateHistory = false;
    SortedMap<Long, long[]> writeHistory = new TreeMap<Long, long[]>(),
        readHistory = new TreeMap<Long, long[]>();
    if (descriptor.getWriteHistory() != null) {
      parseHistoryLine(descriptor.getWriteHistory().getLine(),
          writeHistory);
      updateHistory = true;
    }
    if (descriptor.getReadHistory() != null) {
      parseHistoryLine(descriptor.getReadHistory().getLine(),
          readHistory);
      updateHistory = true;
    }
    if (updateHistory) {
      this.readHistoryFromDisk(fingerprint, writeHistory, readHistory);
      this.compressHistory(writeHistory);
      this.compressHistory(readHistory);
      this.writeHistoryToDisk(fingerprint, writeHistory, readHistory);
      this.writeBandwidthDataFileToDisk(fingerprint, writeHistory,
          readHistory);
    }
  }

  private void parseHistoryLine(String line,
      SortedMap<Long, long[]> history) {
    String[] parts = line.split(" ");
    if (parts.length < 6) {
      return;
    }
    try {
      long endMillis = this.dateTimeFormat.parse(parts[1] + " "
          + parts[2]).getTime();
      long intervalMillis = Long.parseLong(parts[3].substring(1)) * 1000L;
      String[] values = parts[5].split(",");
      for (int i = values.length - 1; i >= 0; i--) {
        long bandwidthValue = Long.parseLong(values[i]);
        long startMillis = endMillis - intervalMillis;
        history.put(startMillis, new long[] { startMillis, endMillis,
            bandwidthValue });
        endMillis -= intervalMillis;
      }
    } catch (ParseException e) {
      System.err.println("Could not parse timestamp in line '" + line
          + "'.  Skipping.");
    }
  }

  private void readHistoryFromDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    File historyFile = new File(String.format("status/bandwidth/%s/%s/%s",
        fingerprint.substring(0, 1), fingerprint.substring(1, 2),
        fingerprint));
    if (historyFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            historyFile));
        String line;
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(" ");
          if (parts.length != 6) {
            System.err.println("Illegal line '" + line + "' in history "
                + "file '" + historyFile.getAbsolutePath()
                + "'.  Skipping this line.");
            continue;
          }
          SortedMap<Long, long[]> history = parts[0].equals("r")
              ? readHistory : writeHistory;
          long startMillis = this.dateTimeFormat.parse(parts[1] + " "
              + parts[2]).getTime();
          long endMillis = this.dateTimeFormat.parse(parts[3] + " "
              + parts[4]).getTime();
          long bandwidth = Long.parseLong(parts[5]);
          long previousEndMillis = history.headMap(startMillis).isEmpty()
              ? startMillis
              : history.get(history.headMap(startMillis).lastKey())[1];
          long nextStartMillis = history.tailMap(startMillis).isEmpty()
              ? endMillis : history.tailMap(startMillis).firstKey();
          if (previousEndMillis <= startMillis &&
              nextStartMillis >= endMillis) {
            history.put(startMillis, new long[] { startMillis, endMillis,
                bandwidth });
          }
        }
        br.close();
      } catch (ParseException e) {
        System.err.println("Could not parse timestamp while reading "
            + "history file '" + historyFile.getAbsolutePath()
            + "'.  Skipping.");
      } catch (IOException e) {
        System.err.println("Could not read history file '"
            + historyFile.getAbsolutePath() + "'.  Skipping.");
      }
    }
  }

  private long now = System.currentTimeMillis();
  private void compressHistory(
      SortedMap<Long, long[]> history) {
    SortedMap<Long, long[]> uncompressedHistory =
        new TreeMap<Long, long[]>(history);
    history.clear();
    long lastStartMillis = 0L, lastEndMillis = 0L, lastBandwidth = 0L;
    for (long[] v : uncompressedHistory.values()) {
      long startMillis = v[0], endMillis = v[1], bandwidth = v[2];
      long intervalLengthMillis;
      if (this.now - endMillis <= 72L * 60L * 60L * 1000L) {
        intervalLengthMillis = 15L * 60L * 1000L;
      } else if (this.now - endMillis <= 7L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 60L * 60L * 1000L;
      } else if (this.now - endMillis <= 31L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 4L * 60L * 60L * 1000L;
      } else if (this.now - endMillis <= 92L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 12L * 60L * 60L * 1000L;
      } else if (this.now - endMillis <= 366L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 2L * 24L * 60L * 60L * 1000L;
      } else {
        intervalLengthMillis = 10L * 24L * 60L * 60L * 1000L;
      }
      if (lastEndMillis == startMillis &&
          (lastEndMillis / intervalLengthMillis) ==
          (endMillis / intervalLengthMillis)) {
        lastEndMillis = endMillis;
        lastBandwidth += bandwidth;
      } else {
        if (lastStartMillis > 0L) {
          history.put(lastStartMillis, new long[] { lastStartMillis,
              lastEndMillis, lastBandwidth });
        }
        lastStartMillis = startMillis;
        lastEndMillis = endMillis;
        lastBandwidth = bandwidth;
      }
    }
    if (lastStartMillis > 0L) {
      history.put(lastStartMillis, new long[] { lastStartMillis,
          lastEndMillis, lastBandwidth });
    }
  }

  private void writeHistoryToDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    File historyFile = new File(String.format("status/bandwidth/%s/%s/%s",
        fingerprint.substring(0, 1), fingerprint.substring(1, 2),
        fingerprint));
    try {
      historyFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
      for (long[] v : writeHistory.values()) {
        bw.write("w " + this.dateTimeFormat.format(v[0]) + " "
            + this.dateTimeFormat.format(v[1]) + " "
            + String.valueOf(v[2]) + "\n");
      }
      for (long[] v : readHistory.values()) {
        bw.write("r " + this.dateTimeFormat.format(v[0]) + " "
            + this.dateTimeFormat.format(v[1]) + " "
            + String.valueOf(v[2]) + "\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write history file '"
          + historyFile.getAbsolutePath() + "'.  Skipping.");
    }
  }

  private File bandwidthFileDirectory = new File("out/bandwidth");
  private void writeBandwidthDataFileToDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    if ((writeHistory.isEmpty() || writeHistory.lastKey() < this.now
        - 7L * 24L * 60L * 60L * 1000L) &&
        (readHistory.isEmpty() || readHistory.lastKey() < this.now
        - 7L * 24L * 60L * 60L * 1000L)) {
      /* Don't write bandwidth data file to disk. */
      return;
    }
    String writeHistoryString = formatHistoryString(writeHistory);
    String readHistoryString = formatHistoryString(readHistory);
    File bandwidthFile = new File("out/bandwidth", fingerprint);
    try {
      bandwidthFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          bandwidthFile));
      bw.write("{\"fingerprint\":\"" + fingerprint + "\",\n"
          + "\"write_history\":{\n" + writeHistoryString + "},\n"
          + "\"read_history\":{\n" + readHistoryString + "}}\n");
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write bandwidth data file '"
          + bandwidthFile.getAbsolutePath() + "'.  Skipping.");
    }
  }

  private String[] graphNames = new String[] {
      "3_days",
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private long[] graphIntervals = new long[] {
      72L * 60L * 60L * 1000L,
      7L * 24L * 60L * 60L * 1000L,
      31L * 24L * 60L * 60L * 1000L,
      92L * 24L * 60L * 60L * 1000L,
      366L * 24L * 60L * 60L * 1000L,
      5L * 366L * 24L * 60L * 60L * 1000L };

  private long[] dataPointIntervals = new long[] {
      15L * 60L * 1000L,
      60L * 60L * 1000L,
      4L * 60L * 60L * 1000L,
      12L * 60L * 60L * 1000L,
      2L * 24L * 60L * 60L * 1000L,
      10L * 24L * 60L * 60L * 1000L };

  private String formatHistoryString(SortedMap<Long, long[]> history) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < this.graphIntervals.length; i++) {
      String graphName = this.graphNames[i];
      long graphInterval = this.graphIntervals[i];
      long dataPointInterval = this.dataPointIntervals[i];
      List<Long> dataPoints = new ArrayList<Long>();
      long intervalStartMillis = ((this.now - graphInterval)
          / dataPointInterval) * dataPointInterval;
      long totalMillis = 0L, totalBandwidth = 0L;
      for (long[] v : history.values()) {
        long startMillis = v[0], endMillis = v[1], bandwidth = v[2];
        if (endMillis < intervalStartMillis) {
          continue;
        }
        while ((intervalStartMillis / dataPointInterval) !=
            (endMillis / dataPointInterval)) {
          dataPoints.add(totalMillis * 5L < dataPointInterval
              ? -1L : (totalBandwidth * 1000L) / totalMillis);
          totalBandwidth = 0L;
          totalMillis = 0L;
          intervalStartMillis += dataPointInterval;
        }
        totalBandwidth += bandwidth;
        totalMillis += (endMillis - startMillis);
      }
      dataPoints.add(totalMillis * 5L < dataPointInterval
          ? -1L : (totalBandwidth * 1000L) / totalMillis);
      long maxValue = 1L;
      int firstNonNullIndex = -1, lastNonNullIndex = -1;
      for (int j = 0; j < dataPoints.size(); j++) {
        long dataPoint = dataPoints.get(j);
        if (dataPoint >= 0L) {
          if (firstNonNullIndex < 0) {
            firstNonNullIndex = j;
          }
          lastNonNullIndex = j;
          if (dataPoint > maxValue) {
            maxValue = dataPoint;
          }
        }
      }
      if (firstNonNullIndex < 0) {
        continue;
      }
      long firstDataPointMillis = (((this.now - graphInterval)
          / dataPointInterval) + firstNonNullIndex) * dataPointInterval
          + dataPointInterval / 2L;
      if (i > 0 &&
          firstDataPointMillis >= this.now - graphIntervals[i - 1]) {
        /* Skip bandwidth history object, because it doesn't contain
         * anything new that wasn't already contained in the last
         * bandwidth history object(s). */
        continue;
      }
      long lastDataPointMillis = firstDataPointMillis
          + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
      double factor = ((double) maxValue) / 999.0;
      int count = lastNonNullIndex - firstNonNullIndex + 1;
      StringBuilder sb2 = new StringBuilder();
      sb2.append("\"" + graphName + "\":{"
          + "\"first\":\""
          + this.dateTimeFormat.format(firstDataPointMillis) + "\","
          + "\"last\":\""
          + this.dateTimeFormat.format(lastDataPointMillis) + "\","
          +"\"interval\":" + String.valueOf(dataPointInterval / 1000L)
          + ",\"factor\":" + String.format(Locale.US, "%.3f", factor)
          + ",\"count\":" + String.valueOf(count) + ",\"values\":[");
      int written = 0, previousNonNullIndex = -2;
      boolean foundTwoAdjacentDataPoints = false;
      for (int j = firstNonNullIndex; j <= lastNonNullIndex; j++) {
        long dataPoint = dataPoints.get(j);
        if (dataPoint >= 0L) {
          if (j - previousNonNullIndex == 1) {
            foundTwoAdjacentDataPoints = true;
          }
          previousNonNullIndex = j;
        }
        sb2.append((written++ > 0 ? "," : "") + (dataPoint < 0L ? "null" :
            String.valueOf((dataPoint * 999L) / maxValue)));
      }
      sb2.append("]},\n");
      if (foundTwoAdjacentDataPoints) {
        sb.append(sb2.toString());
      }
    }
    String result = sb.toString();
    if (result.length() >= 2) {
      result = result.substring(0, result.length() - 2) + "\n";
    }
    return result;
  }

  public void deleteObsoleteBandwidthFiles() {
    SortedMap<String, File> obsoleteBandwidthFiles =
        new TreeMap<String, File>();
    if (bandwidthFileDirectory.exists() &&
        bandwidthFileDirectory.isDirectory()) {
      for (File file : bandwidthFileDirectory.listFiles()) {
        if (file.getName().length() == 40) {
          obsoleteBandwidthFiles.put(file.getName(), file);
        }
      }
    }
    for (String fingerprint : this.currentFingerprints) {
      if (obsoleteBandwidthFiles.containsKey(fingerprint)) {
        obsoleteBandwidthFiles.remove(fingerprint);
      }
    }
    for (File bandwidthFile : obsoleteBandwidthFiles.values()) {
      bandwidthFile.delete();
    }
  }
}

