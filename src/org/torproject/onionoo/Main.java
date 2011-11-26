/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Update search data and status data files by reading local network
 * status consensuses and downloading descriptors from the directory
 * authorities. */
public class Main {
  public static void main(String[] args) {

    printStatus("Reading network statuses from disk.");
    NetworkStatusReader nsr = new NetworkStatusReader();
    Set<NetworkStatusData> loadedConsensuses = nsr.loadConsensuses();
    Set<BridgeNetworkStatusData> loadedBridgeNetworkStatuses =
        nsr.loadBridgeNetworkStatuses();

    printStatus("Downloading descriptors from directory authorities.");
    NetworkStatusDownloader nsd = new NetworkStatusDownloader();
    NetworkStatusData downloadedConsensus = nsd.downloadConsensus();

    printStatus("Updating search data.");
    SearchDataWriter sedw = new SearchDataWriter();
    SearchData sd = sedw.readRelaySearchDataFile();
    sd.updateAll(loadedConsensuses);
    sd.updateBridgeNetworkStatuses(loadedBridgeNetworkStatuses);
    sd.update(downloadedConsensus);
    sedw.writeRelaySearchDataFile(sd);

    /* TODO Status files are not implemented yet.
    printStatus("Updating status data.");
    StatusDataWriter stdw = new StatusDataWriter();
    stdw.writeAll(loadedConsensuses);
    stdw.write(downloadedConsensus);
    stdw.deleteOldStatusDataFiles();    
    */

    printStatus("Terminating.");
    System.exit(0);
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

