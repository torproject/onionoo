/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating internal node list.");
    CurrentNodes cn = new CurrentNodes();
    cn.readRelaySearchDataFile();
    cn.readRelayNetworkConsensuses();
    cn.setRelayRunningBits();
    cn.lookUpCountries();
    cn.lookUpASes();
    cn.readBridgeNetworkStatuses();
    cn.setBridgeRunningBits();
    cn.writeRelaySearchDataFile();

    printStatus("Updating detail data.");
    DetailDataWriter ddw = new DetailDataWriter();
    ddw.setCurrentRelays(cn.getCurrentRelays());
    ddw.setCurrentBridges(cn.getCurrentBridges());
    ddw.readRelayServerDescriptors();
    ddw.readExitLists();
    ddw.readBridgeServerDescriptors();
    ddw.readBridgePoolAssignments();
    ddw.writeDetailDataFiles();

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setCurrentRelays(cn.getCurrentRelays());
    bdw.setCurrentBridges(cn.getCurrentBridges());
    bdw.readExtraInfoDescriptors();
    bdw.deleteObsoleteBandwidthFiles();

    printStatus("Updating summary data.");
    SummaryDataWriter sdw = new SummaryDataWriter();
    sdw.setLastValidAfterMillis(cn.getLastValidAfterMillis());
    sdw.setLastPublishedMillis(cn.getLastPublishedMillis());
    sdw.setCurrentRelays(cn.getCurrentRelays());
    sdw.setCurrentBridges(cn.getCurrentBridges());
    sdw.writeSummaryDataFile();

    printStatus("Terminating.");
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

