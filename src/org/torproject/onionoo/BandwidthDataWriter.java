/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;
import org.torproject.descriptor.*;

/* Write bandwidth data files to disk and delete bandwidth files of relays
 * or bridges that fell out the search data list.
 *
 * Bandwidth history data is available in different resolutions, depending
 * on the considered time interval.  Data for the past 72 hours is
 * available for 15 minute detail, data for the past week in 1 hour
 * detail, data for the past month in 4 hour detail, data for the past 3
 * months in 12 hour detail, data for the past year in 2 day detail, and
 * earlier data in 10 day detail.  These detail levels have been chosen to
 * provide between 92 and 192 data points for graphing the bandwidth of
 * the past day, past week, past month, past three months, past year, and
 * past five years. */
public class BandwidthDataWriter {
  private long validAfterMillis;
  public void setValidAfterMillis(long validAfterMillis) {
    this.validAfterMillis = validAfterMillis;
  }
  private long freshUntilMillis;
  public void setFreshUntilMillis(long freshUntilMillis) {
    this.freshUntilMillis = freshUntilMillis;
  }
  private SortedMap<String, SearchEntryData> relays;
  public void setRelays(SortedMap<String, SearchEntryData> relays) {
    this.relays = relays;
  }
  private SortedMap<String, Long> bridges;
  public void setBridges(SortedMap<String, Long> bridges) {
    this.bridges = bridges;
  }
  public void updateRelayExtraInfoDescriptors(
      Set<RelayExtraInfoDescriptor> extraInfoDescriptors) {
    for (RelayExtraInfoDescriptor descriptor : extraInfoDescriptors) {
      parseDescriptor(descriptor);
    }
  }
  private static void parseDescriptor(
      RelayExtraInfoDescriptor descriptor) {
    String fingerprint = descriptor.getFingerprint();
    boolean readNewHistory = false;
    SortedMap<Long, long[]> writeHistory = new TreeMap<Long, long[]>(),
        readHistory = new TreeMap<Long, long[]>();
    if (descriptor.getWriteHistory() != null) {
      parseHistoryLine(descriptor.getWriteHistory().getLine(),
          writeHistory);
      readNewHistory = true;
    }
    if (descriptor.getReadHistory() != null) {
      parseHistoryLine(descriptor.getReadHistory().getLine(),
          readHistory);
      readNewHistory = true;
    }
    if (readNewHistory) {
      readRelayHistoryFromDisk(fingerprint, writeHistory, readHistory);
      compressRelayHistory(writeHistory);
      compressRelayHistory(readHistory);
      writeRelayHistoryToDisk(fingerprint, writeHistory, readHistory);
      writeBandwidthDataFileToDisk(fingerprint, writeHistory,
          readHistory);
    }
  }
  private static SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
      "yyyy-MM-dd HH:mm:ss");
  static {
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }
  private static void parseHistoryLine(String line,
      SortedMap<Long, long[]> history) {
    String[] parts = line.split(" ");
    if (parts.length < 6) {
      return;
    }
    try {
      long endMillis = dateTimeFormat.parse(parts[1] + " " + parts[2]).
          getTime();
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
  private static void readRelayHistoryFromDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    File historyFile = new File("status/bandwidth", fingerprint);
    if (historyFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            historyFile));
        String line;
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(" ");
          SortedMap<Long, long[]> history = parts[0].equals("r")
              ? readHistory : writeHistory;
          long startMillis = dateTimeFormat.parse(parts[1] + " "
              + parts[2]).getTime();
          long endMillis = dateTimeFormat.parse(parts[3] + " "
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
            + "relay history file '" + historyFile.getAbsolutePath()
            + "'.  Skipping.");
      } catch (IOException e) {
        System.err.println("Could not read relay history file '"
            + historyFile.getAbsolutePath() + "'.  Skipping.");
      }
    }
  }
  private static long now = System.currentTimeMillis();
  private static void compressRelayHistory(
      SortedMap<Long, long[]> history) {
    SortedMap<Long, long[]> uncompressedHistory =
        new TreeMap<Long, long[]>(history);
    history.clear();
    long lastStartMillis = 0L, lastEndMillis = 0L, lastBandwidth = 0L;
    for (long[] v : uncompressedHistory.values()) {
      long startMillis = v[0], endMillis = v[1], bandwidth = v[2];
      long intervalLengthMillis;
      if (now - endMillis <= 72L * 60L * 60L * 1000L) {
        intervalLengthMillis = 15L * 60L * 1000L;
      } else if (now - endMillis <= 7L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 60L * 60L * 1000L;
      } else if (now - endMillis <= 31L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 4L * 60L * 60L * 1000L;
      } else if (now - endMillis <= 92L * 24L * 60L * 60L * 1000L) {
        intervalLengthMillis = 12L * 60L * 60L * 1000L;
      } else if (now - endMillis <= 366L * 24L * 60L * 60L * 1000L) {
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
  private static void writeRelayHistoryToDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    File historyFile = new File("status/bandwidth", fingerprint);
    try {
      historyFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
      for (long[] v : writeHistory.values()) {
        bw.write("w " + dateTimeFormat.format(v[0]) + " "
            + dateTimeFormat.format(v[1]) + " " + String.valueOf(v[2])
            + "\n");
      }
      for (long[] v : readHistory.values()) {
        bw.write("r " + dateTimeFormat.format(v[0]) + " "
            + dateTimeFormat.format(v[1]) + " " + String.valueOf(v[2])
            + "\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write relay history file '"
          + historyFile.getAbsolutePath() + "'.  Skipping.");
    }
  }
  private static void writeBandwidthDataFileToDisk(String fingerprint,
      SortedMap<Long, long[]> writeHistory,
      SortedMap<Long, long[]> readHistory) {
    if ((writeHistory.isEmpty() ||
        writeHistory.lastKey() < now - 7L * 24L * 60L * 60L * 1000L) &&
        (readHistory.isEmpty() ||
        readHistory.lastKey() < now - 7L * 24L * 60L * 60L * 1000L)) {
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
      System.err.println("Could not write detail data file '"
          + bandwidthFile.getAbsolutePath() + "'.  Skipping.");
    }
  }
  private static String[] graphNames = new String[] {
      "3_days",
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };
  private static long[] graphIntervals = new long[] {
      72L * 60L * 60L * 1000L,
      7L * 24L * 60L * 60L * 1000L,
      31L * 24L * 60L * 60L * 1000L,
      92L * 24L * 60L * 60L * 1000L,
      366L * 24L * 60L * 60L * 1000L,
      5L * 366L * 24L * 60L * 60L * 1000L };
  private static long[] dataPointIntervals = new long[] {
      15L * 60L * 1000L,
      60L * 60L * 1000L,
      4L * 60L * 60L * 1000L,
      12L * 60L * 60L * 1000L,
      2L * 24L * 60L * 60L * 1000L,
      10L * 24L * 60L * 60L * 1000L };
  private static String formatHistoryString(
      SortedMap<Long, long[]> history) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < graphIntervals.length; i++) {
      String graphName = graphNames[i];
      long graphInterval = graphIntervals[i];
      long dataPointInterval = dataPointIntervals[i];
      List<Long> dataPoints = new ArrayList<Long>();
      long intervalStartMillis = ((now - graphInterval)
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
      long firstDataPointMillis = (((now - graphInterval)
          / dataPointInterval) + firstNonNullIndex) * dataPointInterval
          + dataPointInterval / 2L;
      long lastDataPointMillis = firstDataPointMillis
          + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
      double factor = ((double) maxValue) / 999.0;
      int count = lastNonNullIndex - firstNonNullIndex + 1;
      StringBuilder sb2 = new StringBuilder();
      sb2.append("\"" + graphName + "\":{"
          + "\"first\":\"" + dateTimeFormat.format(firstDataPointMillis)
          + "\",\"last\":\"" + dateTimeFormat.format(lastDataPointMillis)
          + "\",\"interval\":" + String.valueOf(dataPointInterval / 1000L)
          + ",\"factor\":" + String.format("%.3f", factor) + ","
          + "\"count\":" + String.valueOf(count) + ","
          + "\"values\":[");
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
  private File bandwidthFileDirectory = new File("out/bandwidth");
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
    for (Map.Entry<String, SearchEntryData> relay :
        this.relays.entrySet()) {
      String fingerprint = relay.getKey();
      if (obsoleteBandwidthFiles.containsKey(fingerprint)) {
        obsoleteBandwidthFiles.remove(fingerprint);
      }
    }
    for (File bandwidthFile : obsoleteBandwidthFiles.values()) {
      bandwidthFile.delete();
    }
  }
}

