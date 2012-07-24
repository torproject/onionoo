/* Copyright 2012 The Tor Project
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

public class WeightsDataWriter {

  private SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
      "yyyy-MM-dd HH:mm:ss");
  public WeightsDataWriter() {
    this.dateTimeFormat.setLenient(false);
    this.dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private SortedSet<String> currentFingerprints = new TreeSet<String>();
  public void setCurrentRelays(SortedMap<String, Node> currentRelays) {
    this.currentFingerprints.addAll(currentRelays.keySet());
  }

  /* Read advertised bandwidths of all server descriptors in
   * in/relay-descriptors/server-descriptors/ to memory.  Ideally, we'd
   * skip descriptors that we read before and obtain their advertised
   * bandwidths from some temp file.  This approach should do for now,
   * though. */
  private Map<String, Integer> advertisedBandwidths =
      new HashMap<String, Integer>();
  public void readRelayServerDescriptors() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File(
        "in/relay-descriptors/server-descriptors"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ServerDescriptor) {
            ServerDescriptor serverDescriptor =
                (ServerDescriptor) descriptor;
            String digest = serverDescriptor.getServerDescriptorDigest().
                toUpperCase();
            int advertisedBandwidth = Math.min(Math.min(
                serverDescriptor.getBandwidthBurst(),
                serverDescriptor.getBandwidthObserved()),
                serverDescriptor.getBandwidthRate());
            this.advertisedBandwidths.put(digest, advertisedBandwidth);
          }
        }
      }
    }
  }

  public void readRelayNetworkConsensuses() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/relay-descriptors/consensuses"));
    reader.setExcludeFiles(new File(
        "status/weights-relay-consensus-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof RelayNetworkStatusConsensus) {
            RelayNetworkStatusConsensus consensus =
                (RelayNetworkStatusConsensus) descriptor;
            long validAfterMillis = consensus.getValidAfterMillis(),
                freshUntilMillis = consensus.getFreshUntilMillis();
            SortedMap<String, double[]> pathSelectionWeights =
                this.calculatePathSelectionProbabilities(consensus);
            for (Map.Entry<String, double[]> e :
                pathSelectionWeights.entrySet()) {
              String fingerprint = e.getKey();
              double[] weights = e.getValue();
              this.addToHistory(fingerprint, validAfterMillis,
                  freshUntilMillis, weights);
            }
          }
        }
      }
    }
  }

  private SortedMap<String, double[]> calculatePathSelectionProbabilities(
      RelayNetworkStatusConsensus consensus) {
    double wgg = 1.0, wgd = 1.0, wmg = 1.0, wmm = 1.0, wme = 1.0,
        wmd = 1.0, wee = 1.0, wed = 1.0;
    SortedMap<String, Integer> bandwidthWeights =
        consensus.getBandwidthWeights();
    if (bandwidthWeights != null) {
      SortedSet<String> missingWeightKeys = new TreeSet<String>(
          Arrays.asList("Wgg,Wgd,Wmg,Wmm,Wme,Wmd,Wee,Wed".split(",")));
      missingWeightKeys.removeAll(bandwidthWeights.keySet());
      if (missingWeightKeys.isEmpty()) {
        wgg = ((double) bandwidthWeights.get("Wgg")) / 10000.0;
        wgd = ((double) bandwidthWeights.get("Wgd")) / 10000.0;
        wmg = ((double) bandwidthWeights.get("Wmg")) / 10000.0;
        wmm = ((double) bandwidthWeights.get("Wmm")) / 10000.0;
        wme = ((double) bandwidthWeights.get("Wme")) / 10000.0;
        wmd = ((double) bandwidthWeights.get("Wmd")) / 10000.0;
        wee = ((double) bandwidthWeights.get("Wee")) / 10000.0;
        wed = ((double) bandwidthWeights.get("Wed")) / 10000.0;
      }
    }
    SortedMap<String, Double>
        advertisedBandwidths = new TreeMap<String, Double>(),
        consensusWeights = new TreeMap<String, Double>(),
        guardWeights = new TreeMap<String, Double>(),
        middleWeights = new TreeMap<String, Double>(),
        exitWeights = new TreeMap<String, Double>();
    double totalAdvertisedBandwidth = 0.0;
    double totalConsensusWeight = 0.0;
    double totalGuardWeight = 0.0;
    double totalMiddleWeight = 0.0;
    double totalExitWeight = 0.0;
    for (NetworkStatusEntry relay :
        consensus.getStatusEntries().values()) {
      String fingerprint = relay.getFingerprint();
      if (!relay.getFlags().contains("Running")) {
        continue;
      }
      boolean isExit = relay.getFlags().contains("Exit") &&
          !relay.getFlags().contains("BadExit");
      boolean isGuard = relay.getFlags().contains("Guard");
      String serverDescriptorDigest = relay.getDescriptor().
          toUpperCase();
      double advertisedBandwidth = 0.0;
      if (this.advertisedBandwidths.containsKey(
          serverDescriptorDigest)) {
        advertisedBandwidth = (double) this.advertisedBandwidths.get(
            serverDescriptorDigest);
      }
      double consensusWeight = (double) relay.getBandwidth();
      double guardWeight = (double) relay.getBandwidth();
      double middleWeight = (double) relay.getBandwidth();
      double exitWeight = (double) relay.getBandwidth();
      if (isGuard && isExit) {
        guardWeight *= wgd;
        middleWeight *= wmd;
        exitWeight *= wed;
      } else if (isGuard) {
        guardWeight *= wgg;
        middleWeight *= wmg;
        exitWeight = 0.0;
      } else if (isExit) {
        guardWeight = 0.0;
        middleWeight *= wme;
        exitWeight *= wee;
      } else {
        guardWeight = 0.0;
        middleWeight *= wmm;
        exitWeight = 0.0;
      }
      advertisedBandwidths.put(fingerprint, advertisedBandwidth);
      consensusWeights.put(fingerprint, consensusWeight);
      guardWeights.put(fingerprint, guardWeight);
      middleWeights.put(fingerprint, middleWeight);
      exitWeights.put(fingerprint, exitWeight);
      totalAdvertisedBandwidth += advertisedBandwidth;
      totalConsensusWeight += consensusWeight;
      totalGuardWeight += guardWeight;
      totalMiddleWeight += middleWeight;
      totalExitWeight += exitWeight;
    }
    SortedMap<String, double[]> pathSelectionProbabilities =
        new TreeMap<String, double[]>();
    for (NetworkStatusEntry relay :
        consensus.getStatusEntries().values()) {
      String fingerprint = relay.getFingerprint();
      double[] probabilities = new double[] {
          advertisedBandwidths.get(fingerprint)
            / totalAdvertisedBandwidth,
          consensusWeights.get(fingerprint) / totalConsensusWeight,
          guardWeights.get(fingerprint) / totalGuardWeight,
          middleWeights.get(fingerprint) / totalMiddleWeight,
          exitWeights.get(fingerprint) / totalExitWeight };
      pathSelectionProbabilities.put(fingerprint, probabilities);
    }
    return pathSelectionProbabilities;
  }

  private void addToHistory(String fingerprint, long validAfterMillis,
      long freshUntilMillis, double[] weights) {
    SortedMap<long[], double[]> history =
        this.readHistoryFromDisk(fingerprint);
    long[] interval = new long[] { validAfterMillis, freshUntilMillis };
    if ((history.headMap(interval).isEmpty() ||
        history.headMap(interval).lastKey()[1] <= validAfterMillis) &&
        (history.tailMap(interval).isEmpty() ||
        history.tailMap(interval).firstKey()[0] >= freshUntilMillis)) {
      history.put(interval, weights);
      history = this.compressHistory(history);
      this.writeHistoryToDisk(fingerprint, history);
    }
  }

  private SortedMap<long[], double[]> readHistoryFromDisk(
      String fingerprint) {
    SortedMap<long[], double[]> history =
        new TreeMap<long[], double[]>(new Comparator<long[]>() {
      public int compare(long[] a, long[] b) {
        return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0;
      }
    });
    File historyFile = new File("status/weights", fingerprint);
    if (historyFile.exists()) {
      try {
        BufferedReader br = new BufferedReader(new FileReader(
            historyFile));
        String line;
        while ((line = br.readLine()) != null) {
          String[] parts = line.split(" ");
          if (parts.length != 9) {
            System.err.println("Illegal line '" + line + "' in history "
                + "file '" + historyFile.getAbsolutePath()
                + "'.  Skipping this line.");
            continue;
          }
          long validAfterMillis = this.dateTimeFormat.parse(parts[0]
              + " " + parts[1]).getTime();
          long freshUntilMillis = this.dateTimeFormat.parse(parts[2]
              + " " + parts[3]).getTime();
          long[] interval = new long[] { validAfterMillis,
              freshUntilMillis };
          double[] weights = new double[] {
              Double.parseDouble(parts[4]),
              Double.parseDouble(parts[5]),
              Double.parseDouble(parts[6]),
              Double.parseDouble(parts[7]),
              Double.parseDouble(parts[8]) };
          history.put(interval, weights);
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
    return history;
  }

  private long now = System.currentTimeMillis();
  private SortedMap<long[], double[]> compressHistory(
      SortedMap<long[], double[]> history) {
    SortedMap<long[], double[]> compressedHistory =
        new TreeMap<long[], double[]>(history.comparator());
    long lastStartMillis = 0L, lastEndMillis = 0L;
    double[] lastWeights = null;
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long startMillis = e.getKey()[0], endMillis = e.getKey()[1];
      double[] weights = e.getValue();
      long intervalLengthMillis;
      if (this.now - endMillis <= 7L * 24L * 60L * 60L * 1000L) {
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
        double lastIntervalInHours = (double) ((lastEndMillis
            - lastStartMillis) / 60L * 60L * 1000L);
        double currentIntervalInHours = (double) ((endMillis
            - startMillis) / 60L * 60L * 1000L);
        double newIntervalInHours = (double) ((endMillis
            - lastStartMillis) / 60L * 60L * 1000L);
        for (int i = 0; i < lastWeights.length; i++) {
          lastWeights[i] *= lastIntervalInHours;
          lastWeights[i] += weights[i] * currentIntervalInHours;
          lastWeights[i] /= newIntervalInHours;
        }
        lastEndMillis = endMillis;
      } else {
        if (lastStartMillis > 0L) {
          compressedHistory.put(new long[] { lastStartMillis,
              lastEndMillis }, lastWeights);
        }
        lastStartMillis = startMillis;
        lastEndMillis = endMillis;
        lastWeights = weights;
      }
    }
    if (lastStartMillis > 0L) {
      compressedHistory.put(new long[] { lastStartMillis, lastEndMillis },
          lastWeights);
    }
    return compressedHistory;
  }

  private void writeHistoryToDisk(String fingerprint,
      SortedMap<long[], double[]> history) {
    File historyFile = new File("status/weights", fingerprint);
    try {
      historyFile.getParentFile().mkdirs();
      BufferedWriter bw = new BufferedWriter(new FileWriter(historyFile));
      for (Map.Entry<long[], double[]> e : history.entrySet()) {
        long[] fresh = e.getKey();
        double[] weights = e.getValue();
        bw.write(this.dateTimeFormat.format(fresh[0]) + " "
            + this.dateTimeFormat.format(fresh[1]));
        for (double weight : weights) {
          bw.write(String.format(" %.12f", weight));
        }
        bw.write("\n");
      }
      bw.close();
    } catch (IOException e) {
      System.err.println("Could not write weights file '"
          + historyFile.getAbsolutePath() + "'.  Skipping.");
    }
  }

  private File weightsFileDirectory = new File("out/weights");
  public void writeWeightsDataFiles() {
    for (String fingerprint : this.currentFingerprints) {
      SortedMap<long[], double[]> history =
          this.readHistoryFromDisk(fingerprint);
      if (history.isEmpty() || history.lastKey()[1] < this.now
          - 7L * 24L * 60L * 60L * 1000L) {
        /* Don't write weights data file to disk. */
        continue;
      }
      String historyString = this.formatHistoryString(fingerprint,
          history);
      File weightsFile = new File(weightsFileDirectory, fingerprint);
      try {
        weightsFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            weightsFile));
        bw.write(historyString);
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not write weights data file '"
            + weightsFile.getAbsolutePath() + "'.  Skipping.");
      }
    }
  }

  private String[] graphTypes = new String[] {
      "advertised_bandwidth_fraction",
      "consensus_weight_fraction",
      "guard_probability",
      "middle_probability",
      "exit_probability"
  };

  private String[] graphNames = new String[] {
      "1_week",
      "1_month",
      "3_months",
      "1_year",
      "5_years" };

  private long[] graphIntervals = new long[] {
      7L * 24L * 60L * 60L * 1000L,
      31L * 24L * 60L * 60L * 1000L,
      92L * 24L * 60L * 60L * 1000L,
      366L * 24L * 60L * 60L * 1000L,
      5L * 366L * 24L * 60L * 60L * 1000L };

  private long[] dataPointIntervals = new long[] {
      60L * 60L * 1000L,
      4L * 60L * 60L * 1000L,
      12L * 60L * 60L * 1000L,
      2L * 24L * 60L * 60L * 1000L,
      10L * 24L * 60L * 60L * 1000L };

  private String formatHistoryString(String fingerprint,
      SortedMap<long[], double[]> history) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"fingerprint\":\"" + fingerprint + "\"");
    for (int graphTypeIndex = 0; graphTypeIndex < this.graphTypes.length;
        graphTypeIndex++) {
      String graphType = this.graphTypes[graphTypeIndex];
      sb.append(",\n\"" + graphType + "\":{");
      int graphIntervalsWritten = 0;
      for (int graphIntervalIndex = 0; graphIntervalIndex <
          this.graphIntervals.length; graphIntervalIndex++) {
        String timeline = this.formatTimeline(graphTypeIndex,
            graphIntervalIndex, history);
        if (timeline != null) {
          sb.append((graphIntervalsWritten++ > 0 ? "," : "") + "\n"
            + timeline);
        }
      }
      sb.append("}");
    }
    sb.append("\n}\n");
    return sb.toString();
  }

  private String formatTimeline(int graphTypeIndex,
      int graphIntervalIndex, SortedMap<long[], double[]> history) {
    String graphName = this.graphNames[graphIntervalIndex];
    long graphInterval = this.graphIntervals[graphIntervalIndex];
    long dataPointInterval =
        this.dataPointIntervals[graphIntervalIndex];
    List<Double> dataPoints = new ArrayList<Double>();
    long intervalStartMillis = ((this.now - graphInterval)
        / dataPointInterval) * dataPointInterval;
    long totalMillis = 0L;
    double totalWeightTimesMillis = 0.0;
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long startMillis = e.getKey()[0], endMillis = e.getKey()[1];
      double weight = e.getValue()[graphTypeIndex];
      if (endMillis < intervalStartMillis) {
        continue;
      }
      while ((intervalStartMillis / dataPointInterval) !=
          (endMillis / dataPointInterval)) {
        dataPoints.add(totalMillis * 5L < dataPointInterval
            ? -1.0 : totalWeightTimesMillis / (double) totalMillis);
        totalWeightTimesMillis = 0.0;
        totalMillis = 0L;
        intervalStartMillis += dataPointInterval;
      }
      totalWeightTimesMillis += weight
          * ((double) (endMillis - startMillis));
      totalMillis += (endMillis - startMillis);
    }
    dataPoints.add(totalMillis * 5L < dataPointInterval
        ? -1.0 : totalWeightTimesMillis / (double) totalMillis);
    double maxValue = 0.0;
    int firstNonNullIndex = -1, lastNonNullIndex = -1;
    for (int dataPointIndex = 0; dataPointIndex < dataPoints.size();
        dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (firstNonNullIndex < 0) {
          firstNonNullIndex = dataPointIndex;
        }
        lastNonNullIndex = dataPointIndex;
        if (dataPoint > maxValue) {
          maxValue = dataPoint;
        }
      }
    }
    if (firstNonNullIndex < 0) {
      return null;
    }
    long firstDataPointMillis = (((this.now - graphInterval)
        / dataPointInterval) + firstNonNullIndex) * dataPointInterval
        + dataPointInterval / 2L;
    if (graphIntervalIndex > 0 && firstDataPointMillis >=
        this.now - graphIntervals[graphIntervalIndex - 1]) {
      /* Skip weights history object, because it doesn't contain
       * anything new that wasn't already contained in the last
       * weights history object(s). */
      return null;
    }
    long lastDataPointMillis = firstDataPointMillis
        + (lastNonNullIndex - firstNonNullIndex) * dataPointInterval;
    double factor = ((double) maxValue) / 999.0;
    int count = lastNonNullIndex - firstNonNullIndex + 1;
    StringBuilder sb = new StringBuilder();
    sb.append("\"" + graphName + "\":{"
        + "\"first\":\""
        + this.dateTimeFormat.format(firstDataPointMillis) + "\","
        + "\"last\":\""
        + this.dateTimeFormat.format(lastDataPointMillis) + "\","
        + "\"interval\":" + String.valueOf(dataPointInterval / 1000L)
        + ",\"factor\":" + String.format(Locale.US, "%.9f", factor)
        + ",\"count\":" + String.valueOf(count) + ",\"values\":[");
    int dataPointsWritten = 0, previousNonNullIndex = -2;
    boolean foundTwoAdjacentDataPoints = false;
    for (int dataPointIndex = firstNonNullIndex; dataPointIndex <=
        lastNonNullIndex; dataPointIndex++) {
      double dataPoint = dataPoints.get(dataPointIndex);
      if (dataPoint >= 0.0) {
        if (dataPointIndex - previousNonNullIndex == 1) {
          foundTwoAdjacentDataPoints = true;
        }
        previousNonNullIndex = dataPointIndex;
      }
      sb.append((dataPointsWritten++ > 0 ? "," : "")
          + (dataPoint < 0.0 ? "null" :
          String.valueOf((long) ((dataPoint * 999.0) / maxValue))));
    }
    sb.append("]}");
    if (foundTwoAdjacentDataPoints) {
      return sb.toString();
    } else {
      return null;
    }
  }

  public void deleteObsoleteWeightsDataFiles() {
    SortedMap<String, File> obsoleteWeightsFiles =
        new TreeMap<String, File>();
    if (weightsFileDirectory.exists() &&
        weightsFileDirectory.isDirectory()) {
      for (File file : weightsFileDirectory.listFiles()) {
        if (file.getName().length() == 40) {
          obsoleteWeightsFiles.put(file.getName(), file);
        }
      }
    }
    for (String fingerprint : this.currentFingerprints) {
      if (obsoleteWeightsFiles.containsKey(fingerprint)) {
        obsoleteWeightsFiles.remove(fingerprint);
      }
    }
    for (File weightsFile : obsoleteWeightsFiles.values()) {
      weightsFile.delete();
    }
  }
}

