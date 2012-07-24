/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating internal node list.");
    CurrentNodes cn = new CurrentNodes();
    cn.readRelaySearchDataFile(new File("out/summary"));
    cn.readRelayNetworkConsensuses();
    cn.setRelayRunningBits();
    cn.lookUpCountries();
    cn.lookUpASes();
    cn.readBridgeNetworkStatuses();
    cn.setBridgeRunningBits();

    printStatus("Updating detail data.");
    DetailDataWriter ddw = new DetailDataWriter();
    ddw.setCurrentRelays(cn.getCurrentRelays());
    ddw.setCurrentBridges(cn.getCurrentBridges());
    ddw.startReverseDomainNameLookups();
    ddw.readRelayServerDescriptors();
    ddw.calculatePathSelectionProbabilities(cn.getLastBandwidthWeights());
    ddw.readExitLists();
    ddw.readBridgeServerDescriptors();
    ddw.readBridgePoolAssignments();
    ddw.finishReverseDomainNameLookups();
    ddw.writeDetailDataFiles();

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setCurrentRelays(cn.getCurrentRelays());
    bdw.setCurrentBridges(cn.getCurrentBridges());
    bdw.readExtraInfoDescriptors();
    bdw.deleteObsoleteBandwidthFiles();

    printStatus("Updating weights data.");
    WeightsDataWriter wdw = new WeightsDataWriter();
    wdw.setCurrentRelays(cn.getCurrentRelays());
    wdw.readRelayServerDescriptors();
    wdw.readRelayNetworkConsensuses();
    wdw.writeWeightsDataFiles();
    wdw.deleteObsoleteWeightsDataFiles();

    printStatus("Updating summary data.");
    cn.writeRelaySearchDataFile();

    printStatus("Terminating.");
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

