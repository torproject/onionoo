/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Update search data and status data files by reading local network
 * status consensuses and downloading the current network status from the
 * directory authorities. */
public class Main {
  public static void main(String[] args) {

    printStatus("Reading the internal search data file from disk (if "
        + "present).");
    SearchDataWriter sedw = new SearchDataWriter();
    SearchData sd = sedw.readRelaySearchDataFile();

    printStatus("Reading network statuses from disk (if present).");
    NetworkStatusReader nsr = new NetworkStatusReader();
    Set<NetworkStatusData> loadedConsensuses = nsr.loadConsensuses();
    Set<BridgeNetworkStatusData> loadedBridgeNetworkStatuses =
        nsr.loadBridgeNetworkStatuses();

    printStatus("Downloading current network status consensus from "
        + "directory authorities.");
    NetworkStatusDownloader nsd = new NetworkStatusDownloader();
    NetworkStatusData downloadedConsensus = nsd.downloadConsensus();

    printStatus("Updating search data.");
    sd.updateAll(loadedConsensuses);
    sd.updateBridgeNetworkStatuses(loadedBridgeNetworkStatuses);
    sd.update(downloadedConsensus);

    printStatus("(Over-)writing search data file on disk.");
    sedw.writeRelaySearchDataFile(sd);

    /* TODO Status files are not implemented yet.
    printStatus("(Over-)writing status data files on disk.");
    StatusDataWriter stdw = new StatusDataWriter();
    stdw.writeAll(loadedConsensuses);
    stdw.write(downloadedConsensus);

    printStatus("Deleting status data files that are older than two "
        + "weeks.");
    stdw.deleteOldStatusDataFiles();    
    */

    printStatus("Terminating.");
    System.exit(0);
  }

  private static void printStatus(String message) {
    System.out.println(message);
  }
}

