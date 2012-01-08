/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Update search data and status data files by reading local network
 * status consensuses and downloading descriptors from the directory
 * authorities. */
public class Main {
  public static void main(String[] args) {

    printStatus("Reading descriptors from disk.");
    NetworkStatusReader nsr = new NetworkStatusReader();
    Set<NetworkStatusData> loadedConsensuses = nsr.loadConsensuses();
    Map<String, ServerDescriptorData> loadedRelayServerDescriptors =
        nsr.loadRelayServerDescriptors();
    Set<BridgeNetworkStatusData> loadedBridgeNetworkStatuses =
        nsr.loadBridgeNetworkStatuses();

    printStatus("Updating search data.");
    SearchDataWriter sedw = new SearchDataWriter();
    SearchData sd = sedw.readRelaySearchDataFile();
    sd.updateAll(loadedConsensuses);
    sd.updateBridgeNetworkStatuses(loadedBridgeNetworkStatuses);
    sedw.writeRelaySearchDataFile(sd);

    printStatus("Updating status data.");
    StatusDataWriter stdw = new StatusDataWriter();
    stdw.setValidAfterMillis(sd.getLastValidAfterMillis());
    stdw.setFreshUntilMillis(sd.getLastFreshUntilMillis());
    stdw.setRelays(sd.getRelays());
    stdw.setBridges(sd.getBridges());
    stdw.updateRelayServerDescriptors(loadedRelayServerDescriptors);
    stdw.writeStatusDataFiles();

    printStatus("Terminating.");
    System.exit(0);
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

