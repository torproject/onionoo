/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;
import org.torproject.descriptor.*;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating search data.");
    SearchDataWriter sedw = new SearchDataWriter();
    CurrentNodes cn = sedw.readRelaySearchDataFile();
    cn.updateRelayNetworkConsensuses();
    cn.updateBridgeNetworkStatuses();
    sedw.writeRelaySearchDataFile(cn);

    printStatus("Updating status data.");
    StatusDataWriter stdw = new StatusDataWriter();
    stdw.setRelays(cn.getRelays());
    stdw.setBridges(cn.getBridges());
    stdw.updateRelayServerDescriptors();
    stdw.updateBridgeServerDescriptors();
    stdw.updateBridgePoolAssignments();
    stdw.writeStatusDataFiles();

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

