/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;
import org.torproject.descriptor.*;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Reading descriptors from disk.");
    NetworkStatusReader nsr = new NetworkStatusReader();
    Set<RelayNetworkStatusConsensus> loadedConsensuses =
        nsr.getLoadedRelayNetworkStatusConsensuses();
    Map<String, RelayServerDescriptor> loadedRelayServerDescriptors =
        nsr.getLoadedRelayServerDescriptors();
    Set<RelayExtraInfoDescriptor> loadedRelayExtraInfoDescriptors =
        nsr.getLoadedRelayExtraInfoDescriptors();
    Set<BridgeNetworkStatus> loadedBridgeNetworkStatuses =
        nsr.getLoadedBridgeNetworkStatuses();

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

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter();
    bdw.setValidAfterMillis(sd.getLastValidAfterMillis());
    bdw.setFreshUntilMillis(sd.getLastFreshUntilMillis());
    bdw.setRelays(sd.getRelays());
    bdw.setBridges(sd.getBridges());
    bdw.updateRelayExtraInfoDescriptors(loadedRelayExtraInfoDescriptors);
    bdw.deleteObsoleteBandwidthFiles();

    printStatus("Terminating.");
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

