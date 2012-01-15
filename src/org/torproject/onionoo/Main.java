/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;
import org.torproject.descriptor.*;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating summary data.");
    SummaryDataWriter sdw = new SummaryDataWriter();
    CurrentNodes cn = sdw.readRelaySearchDataFile();
    cn.updateRelayNetworkConsensuses();
    cn.updateBridgeNetworkStatuses();
    sdw.writeRelaySearchDataFile(cn);

    printStatus("Updating status data.");
    DetailDataWriter ddw = new DetailDataWriter();
    ddw.setRelays(cn.getRelays());
    ddw.setBridges(cn.getBridges());
    ddw.updateRelayServerDescriptors();
    ddw.updateBridgeServerDescriptors();
    ddw.updateBridgePoolAssignments();
    ddw.writeStatusDataFiles();

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setCurrentFingerprints(cn.getCurrentFingerprints());
    bdw.updateExtraInfoDescriptors();
    bdw.deleteObsoleteBandwidthFiles();

    printStatus("Terminating.");
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

