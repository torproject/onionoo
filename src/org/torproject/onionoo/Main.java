/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;
import java.util.Date;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating internal node list.");
    CurrentNodes cn = new CurrentNodes();
    cn.readRelaySearchDataFile(new File("status/summary"));
    printStatusTime("Read status/summary");
    cn.readRelayNetworkConsensuses();
    printStatusTime("Read network status consensuses");
    cn.setRelayRunningBits();
    printStatusTime("Set relay running bits");
    cn.lookUpCitiesAndASes();
    printStatusTime("Looked up cities and ASes");
    cn.readBridgeNetworkStatuses();
    printStatusTime("Read bridge network statuses");
    cn.setBridgeRunningBits();
    printStatusTime("Set bridge running bits");
    cn.writeRelaySearchDataFile(new File("status/summary"), true);
    printStatusTime("Wrote status/summary");

    printStatus("Updating detail data.");
    DetailDataWriter ddw = new DetailDataWriter();
    ddw.setCurrentRelays(cn.getCurrentRelays());
    printStatusTime("Set current relays");
    ddw.setCurrentBridges(cn.getCurrentBridges());
    printStatusTime("Set current bridges");
    ddw.startReverseDomainNameLookups();
    printStatusTime("Started reverse domain name lookups");
    ddw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    ddw.calculatePathSelectionProbabilities(cn.getLastBandwidthWeights());
    printStatusTime("Calculated path selection probabilities");
    ddw.readExitLists();
    printStatusTime("Read exit lists");
    ddw.readBridgeServerDescriptors();
    printStatusTime("Read bridge server descriptors");
    ddw.readBridgePoolAssignments();
    printStatusTime("Read bridge-pool assignments");
    ddw.finishReverseDomainNameLookups();
    printStatusTime("Finished reverse domain name lookups");
    ddw.writeDetailDataFiles();
    printStatusTime("Wrote detail data files");

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setCurrentRelays(cn.getCurrentRelays());
    printStatusTime("Set current relays");
    bdw.setCurrentBridges(cn.getCurrentBridges());
    printStatusTime("Set current bridges");
    bdw.readExtraInfoDescriptors();
    printStatusTime("Read extra-info descriptors");
    bdw.deleteObsoleteBandwidthFiles();
    printStatusTime("Deleted obsolete bandwidth files");

    printStatus("Updating weights data.");
    WeightsDataWriter wdw = new WeightsDataWriter();
    wdw.setCurrentRelays(cn.getCurrentRelays());
    printStatusTime("Set current relays");
    wdw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    wdw.readRelayNetworkConsensuses();
    printStatusTime("Read relay network consensuses");
    wdw.writeWeightsDataFiles();
    printStatusTime("Wrote weights data files");
    wdw.deleteObsoleteWeightsDataFiles();
    printStatusTime("Deleted obsolete weights files");

    printStatus("Updating summary data.");
    cn.writeRelaySearchDataFile(new File("out/summary"), false);
    printStatusTime("Wrote out/summary");

    printStatus("Terminating.");
  }

  private static long printedLastStatusMessage =
      System.currentTimeMillis();

  private static void printStatus(String message) {
    System.out.println(new Date() + ": " + message);
    printedLastStatusMessage = System.currentTimeMillis();
  }

  private static void printStatusTime(String message) {
    long now = System.currentTimeMillis();
    System.out.println("  " + message + " ("
        + (now - printedLastStatusMessage) + " millis).");
    printedLastStatusMessage = now;
  }
}

