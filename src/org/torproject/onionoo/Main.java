/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;
import org.torproject.descriptor.*;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Updating search data.");
    SearchDataWriter sedw = new SearchDataWriter();
    SearchData sd = sedw.readRelaySearchDataFile();
    sd.updateRelayNetworkConsensuses();
    sd.updateBridgeNetworkStatuses();
    sedw.writeRelaySearchDataFile(sd);

    printStatus("Updating status data.");
    StatusDataWriter stdw = new StatusDataWriter();
    stdw.setRelays(sd.getRelays());
    stdw.setBridges(sd.getBridges());
    stdw.updateRelayServerDescriptors();

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setCurrentFingerprints(sd.getCurrentFingerprints());
    bdw.updateExtraInfoDescriptors();
    bdw.deleteObsoleteBandwidthFiles();

    printStatus("Terminating.");
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

