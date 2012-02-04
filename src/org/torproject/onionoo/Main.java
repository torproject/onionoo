/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating summary data.");
    SummaryDataWriter sdw = new SummaryDataWriter();
    CurrentNodes cn = sdw.readRelaySearchDataFile();
    cn.readRelayNetworkConsensuses();
    cn.setRelayRunningBits();
    cn.lookUpCountries();
    cn.readBridgeNetworkStatuses();
    cn.setBridgeRunningBits();
    sdw.writeRelaySearchDataFile(cn);

    printStatus("Updating detail data.");
    DetailDataWriter ddw = new DetailDataWriter();
    ddw.setCurrentRelays(cn.getCurrentRelays());
    ddw.setCurrentBridges(cn.getCurrentBridges());
    ddw.readRelayServerDescriptors();
    ddw.readBridgeServerDescriptors();
    ddw.readBridgePoolAssignments();
    ddw.writeDetailDataFiles();

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setCurrentRelays(cn.getCurrentRelays());
    bdw.setCurrentBridges(cn.getCurrentBridges());
    bdw.readExtraInfoDescriptors();
    bdw.deleteObsoleteBandwidthFiles();

    printStatus("Terminating.");
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

