/* Copyright 2012--2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.NetworkStatusEntry;
import org.torproject.descriptor.RelayNetworkStatusConsensus;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.docs.WeightsStatus;

public class WeightsStatusUpdater implements DescriptorListener,
    StatusUpdater {

  private DescriptorSource descriptorSource;

  private DocumentStore documentStore;

  public WeightsStatusUpdater() {
    this.descriptorSource = DescriptorSourceFactory.getDescriptorSource();
    this.documentStore = DocumentStoreFactory.getDocumentStore();
    this.registerDescriptorListeners();
  }

  private void registerDescriptorListeners() {
    this.descriptorSource.registerDescriptorListener(this,
        DescriptorType.RELAY_CONSENSUSES);
  }

  public void processDescriptor(Descriptor descriptor, boolean relay) {
    if (descriptor instanceof RelayNetworkStatusConsensus) {
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

  private void updateWeightsHistory(long validAfterMillis,
      long freshUntilMillis,
      SortedMap<String, double[]> pathSelectionWeights) {
    for (Map.Entry<String, double[]> e
        : pathSelectionWeights.entrySet()) {
      String fingerprint = e.getKey();
      WeightsStatus weightsStatus = this.documentStore.retrieve(
          WeightsStatus.class, true, fingerprint);
      if (weightsStatus == null) {
        weightsStatus = new WeightsStatus();
      }
      double[] weights = e.getValue();
      weightsStatus.addToHistory(validAfterMillis, freshUntilMillis,
          weights);
      if (weightsStatus.isDirty()) {
        weightsStatus.compressHistory();
        this.documentStore.store(weightsStatus, fingerprint);
        weightsStatus.clearDirty();
      }
    }
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
        consensusWeights = new TreeMap<String, Double>(),
        guardWeights = new TreeMap<String, Double>(),
        middleWeights = new TreeMap<String, Double>(),
        exitWeights = new TreeMap<String, Double>();
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
    for (String fingerprint : consensusWeights.keySet()) {
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
      pathSelectionProbabilities.put(fingerprint, probabilities);
    }
    return pathSelectionProbabilities;
  }

  public String getStatsString() {
    /* TODO Add statistics string. */
    return null;
  }
}

