/* Copyright 2012--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.descriptor.ServerDescriptor;

public class WeightsStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  private long now;

  public WeightsStatusUpdater() {
    this.descriptorSource = ApplicationFactory.getDescriptorSource();
    this.documentStore = ApplicationFactory.getDocumentStore();
    this.now = ApplicationFactory.getTime().currentTimeMillis();
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_CONSENSUSES);
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_SERVER_DESCRIPTORS);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof ServerDescriptor) {
      this.processRelayServerDescriptor((ServerDescriptor) descriptor);
    } else if (descriptor instanceof RelayNetworkStatusConsensus) {
      this.processRelayNetworkConsensus(
          (RelayNetworkStatusConsensus) descriptor);
    }
  }

  public void updateStatuses() {
    /* Nothing to do. */
  }

  private void processRelayNetworkConsensus(
      RelayNetworkStatusConsensus consensus) {
    long validAfterMillis = consensus.getValidAfterMillis(),
        freshUntilMillis = consensus.getFreshUntilMillis();
    SortedMap<String, double[]> pathSelectionWeights =
        this.calculatePathSelectionProbabilities(consensus);
    this.updateWeightsHistory(validAfterMillis, freshUntilMillis,
        pathSelectionWeights);
  }

  private void processRelayServerDescriptor(
      ServerDescriptor serverDescriptor) {
    String digest = serverDescriptor.getServerDescriptorDigest().
        toUpperCase();
    int advertisedBandwidth = Math.min(Math.min(
        serverDescriptor.getBandwidthBurst(),
        serverDescriptor.getBandwidthObserved()),
        serverDescriptor.getBandwidthRate());
    String fingerprint = serverDescriptor.getFingerprint();
    WeightsStatus weightsStatus = this.documentStore.retrieve(
        WeightsStatus.class, true, fingerprint);
    if (weightsStatus == null) {
      weightsStatus = new WeightsStatus();
    }
    weightsStatus.getAdvertisedBandwidths().put(digest,
        advertisedBandwidth);
    this.documentStore.store(weightsStatus, fingerprint);
}

  private void updateWeightsHistory(long validAfterMillis,
      long freshUntilMillis,
      SortedMap<String, double[]> pathSelectionWeights) {
    String fingerprint = null;
    double[] weights = null;
    do {
      fingerprint = null;
      synchronized (pathSelectionWeights) {
        if (!pathSelectionWeights.isEmpty()) {
          fingerprint = pathSelectionWeights.firstKey();
          weights = pathSelectionWeights.remove(fingerprint);
        }
      }
      if (fingerprint != null) {
        this.addToHistory(fingerprint, validAfterMillis,
            freshUntilMillis, weights);
      }
    } while (fingerprint != null);
  }

  private SortedMap<String, double[]> calculatePathSelectionProbabilities(
      RelayNetworkStatusConsensus consensus) {
    boolean containsBandwidthWeights = false;
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
        containsBandwidthWeights = true;
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
      String digest = relay.getDescriptor().toUpperCase();
      WeightsStatus weightsStatus = this.documentStore.retrieve(
          WeightsStatus.class, true, fingerprint);
      if (weightsStatus != null &&
          weightsStatus.getAdvertisedBandwidths() != null &&
          weightsStatus.getAdvertisedBandwidths().containsKey(digest)) {
        /* Read advertised bandwidth from weights status file.  Server
         * descriptors are parsed before consensuses, so we're sure that
         * if there's a server descriptor for this relay, it'll be
         * contained in the weights status file by now. */
        double advertisedBandwidth =
            (double) weightsStatus.getAdvertisedBandwidths().get(digest);
        advertisedBandwidths.put(fingerprint, advertisedBandwidth);
        totalAdvertisedBandwidth += advertisedBandwidth;
      }
      if (relay.getBandwidth() >= 0L) {
        double consensusWeight = (double) relay.getBandwidth();
        consensusWeights.put(fingerprint, consensusWeight);
        totalConsensusWeight += consensusWeight;
        if (containsBandwidthWeights) {
          double guardWeight = (double) relay.getBandwidth();
          double middleWeight = (double) relay.getBandwidth();
          double exitWeight = (double) relay.getBandwidth();
          boolean isExit = relay.getFlags().contains("Exit") &&
              !relay.getFlags().contains("BadExit");
          boolean isGuard = relay.getFlags().contains("Guard");
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
          guardWeights.put(fingerprint, guardWeight);
          middleWeights.put(fingerprint, middleWeight);
          exitWeights.put(fingerprint, exitWeight);
          totalGuardWeight += guardWeight;
          totalMiddleWeight += middleWeight;
          totalExitWeight += exitWeight;
        }
      }
    }
    SortedMap<String, double[]> pathSelectionProbabilities =
        new TreeMap<String, double[]>();
    SortedSet<String> fingerprints = new TreeSet<String>();
    fingerprints.addAll(consensusWeights.keySet());
    fingerprints.addAll(advertisedBandwidths.keySet());
    for (String fingerprint : fingerprints) {
      double[] probabilities = new double[] { -1.0, -1.0, -1.0, -1.0,
          -1.0, -1.0, -1.0 };
      if (consensusWeights.containsKey(fingerprint) &&
          totalConsensusWeight > 0.0) {
        probabilities[1] = consensusWeights.get(fingerprint) /
            totalConsensusWeight;
        probabilities[6] = consensusWeights.get(fingerprint);
      }
      if (guardWeights.containsKey(fingerprint) &&
          totalGuardWeight > 0.0) {
        probabilities[2] = guardWeights.get(fingerprint) /
            totalGuardWeight;
      }
      if (middleWeights.containsKey(fingerprint) &&
          totalMiddleWeight > 0.0) {
        probabilities[3] = middleWeights.get(fingerprint) /
            totalMiddleWeight;
      }
      if (exitWeights.containsKey(fingerprint) &&
          totalExitWeight > 0.0) {
        probabilities[4] = exitWeights.get(fingerprint) /
            totalExitWeight;
      }
      if (advertisedBandwidths.containsKey(fingerprint) &&
          totalAdvertisedBandwidth > 0.0) {
        probabilities[0] = advertisedBandwidths.get(fingerprint)
            / totalAdvertisedBandwidth;
        probabilities[5] = advertisedBandwidths.get(fingerprint);
      }
      pathSelectionProbabilities.put(fingerprint, probabilities);
    }
    return pathSelectionProbabilities;
  }

  private void addToHistory(String fingerprint, long validAfterMillis,
      long freshUntilMillis, double[] weights) {
    WeightsStatus weightsStatus = this.documentStore.retrieve(
        WeightsStatus.class, true, fingerprint);
    if (weightsStatus == null) {
      weightsStatus = new WeightsStatus();
    }
    SortedMap<long[], double[]> history = weightsStatus.getHistory();
    long[] interval = new long[] { validAfterMillis, freshUntilMillis };
    if ((history.headMap(interval).isEmpty() ||
        history.headMap(interval).lastKey()[1] <= validAfterMillis) &&
        (history.tailMap(interval).isEmpty() ||
        history.tailMap(interval).firstKey()[0] >= freshUntilMillis)) {
      history.put(interval, weights);
      this.compressHistory(weightsStatus);
      this.documentStore.store(weightsStatus, fingerprint);
    }
  }

  private void compressHistory(WeightsStatus weightsStatus) {
    SortedMap<long[], double[]> history = weightsStatus.getHistory();
    SortedMap<long[], double[]> compressedHistory =
        new TreeMap<long[], double[]>(history.comparator());
    long lastStartMillis = 0L, lastEndMillis = 0L;
    double[] lastWeights = null;
    String lastMonthString = "1970-01";
    int lastMissingValues = -1;
    for (Map.Entry<long[], double[]> e : history.entrySet()) {
      long startMillis = e.getKey()[0], endMillis = e.getKey()[1];
      double[] weights = e.getValue();
      long intervalLengthMillis;
      if (this.now - endMillis <= DateTimeHelper.ONE_WEEK) {
        intervalLengthMillis = DateTimeHelper.ONE_HOUR;
      } else if (this.now - endMillis <=
          DateTimeHelper.ROUGHLY_ONE_MONTH) {
        intervalLengthMillis = DateTimeHelper.FOUR_HOURS;
      } else if (this.now - endMillis <=
          DateTimeHelper.ROUGHLY_THREE_MONTHS) {
        intervalLengthMillis = DateTimeHelper.TWELVE_HOURS;
      } else if (this.now - endMillis <=
          DateTimeHelper.ROUGHLY_ONE_YEAR) {
        intervalLengthMillis = DateTimeHelper.TWO_DAYS;
      } else {
        intervalLengthMillis = DateTimeHelper.TEN_DAYS;
      }
      String monthString = DateTimeHelper.format(startMillis,
          DateTimeHelper.ISO_YEARMONTH_FORMAT);
      int missingValues = 0;
      for (int i = 0; i < weights.length; i++) {
        if (weights[i] < -0.5) {
          missingValues += 1 << i;
        }
      }
      if (lastEndMillis == startMillis &&
          ((lastEndMillis - 1L) / intervalLengthMillis) ==
          ((endMillis - 1L) / intervalLengthMillis) &&
          lastMonthString.equals(monthString) &&
          lastMissingValues == missingValues) {
        double lastIntervalInHours = (double) ((lastEndMillis
            - lastStartMillis) / DateTimeHelper.ONE_HOUR);
        double currentIntervalInHours = (double) ((endMillis
            - startMillis) / DateTimeHelper.ONE_HOUR);
        double newIntervalInHours = (double) ((endMillis
            - lastStartMillis) / DateTimeHelper.ONE_HOUR);
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
      lastMonthString = monthString;
      lastMissingValues = missingValues;
    }
    if (lastStartMillis > 0L) {
      compressedHistory.put(new long[] { lastStartMillis, lastEndMillis },
          lastWeights);
    }
    weightsStatus.setHistory(compressedHistory);
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}

