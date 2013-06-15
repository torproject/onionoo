/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;
import java.util.Date;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Initializing descriptor source.");
    DescriptorSource dso = new DescriptorSource(new File("in"),
        new File("status"));
    printStatusTime("Initialized descriptor source");

    printStatus("Initializing document store.");
    DocumentStore ds = new DocumentStore(new File("status"),
        new File("out"));
    printStatusTime("Initialized document store");

    printStatus("Updating internal node list.");
    CurrentNodes cn = new CurrentNodes(dso, ds);
    cn.readStatusSummary();
    printStatusTime("Read status summary");
    cn.readRelayNetworkConsensuses();
    printStatusTime("Read network status consensuses");
    cn.setRelayRunningBits();
    printStatusTime("Set relay running bits");
    cn.lookUpCitiesAndASes();
    printStatusTime("Looked up cities and ASes");
    cn.readBridgeNetworkStatuses();
    printStatusTime("Read bridge network statuses");
    cn.setBridgeRunningBits();
    printStatusTime("Set bridge running bits");
    cn.writeStatusSummary();
    printStatusTime("Wrote status summary");
    // TODO Could write statistics here, too.

    printStatus("Updating detail data.");
    DetailDataWriter ddw = new DetailDataWriter(dso, ds);
    ddw.setCurrentRelays(cn.getCurrentRelays());
    printStatusTime("Set current relays");
    ddw.setCurrentBridges(cn.getCurrentBridges());
    printStatusTime("Set current bridges");
    ddw.startReverseDomainNameLookups();
    printStatusTime("Started reverse domain name lookups");
    ddw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    ddw.calculatePathSelectionProbabilities(cn.getLastBandwidthWeights());
    printStatusTime("Calculated path selection probabilities");
    ddw.readExitLists();
    printStatusTime("Read exit lists");
    ddw.readBridgeServerDescriptors();
    printStatusTime("Read bridge server descriptors");
    ddw.readBridgePoolAssignments();
    printStatusTime("Read bridge-pool assignments");
    ddw.finishReverseDomainNameLookups();
    printStatusTime("Finished reverse domain name lookups");
    ddw.writeOutDetails();
    printStatusTime("Wrote detail data files");
    // TODO Could write statistics here, too.

    printStatus("Updating bandwidth data.");
    BandwidthDataWriter bdw = new BandwidthDataWriter(dso, ds);
    bdw.setCurrentRelays(cn.getCurrentRelays());
    printStatusTime("Set current relays");
    bdw.setCurrentBridges(cn.getCurrentBridges());
    printStatusTime("Set current bridges");
    bdw.readExtraInfoDescriptors();
    printStatusTime("Read extra-info descriptors");
    bdw.deleteObsoleteBandwidthFiles();
    printStatusTime("Deleted obsolete bandwidth files");
    // TODO Could write statistics here, too.

    printStatus("Updating weights data.");
    WeightsDataWriter wdw = new WeightsDataWriter(dso, ds);
    wdw.setCurrentRelays(cn.getCurrentRelays());
    printStatusTime("Set current relays");
    wdw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    wdw.readRelayNetworkConsensuses();
    printStatusTime("Read relay network consensuses");
    wdw.writeWeightsDataFiles();
    printStatusTime("Wrote weights data files");
    wdw.deleteObsoleteWeightsDataFiles();
    printStatusTime("Deleted obsolete weights files");
    // TODO Could write statistics here, too.

    printStatus("Updating summary data.");
    cn.writeOutSummary();
    printStatusTime("Wrote out summary");
    // TODO Could write statistics here, too.

    printStatus("Shutting down descriptor source.");
    dso.writeHistoryFiles();
    printStatusTime("Wrote parse histories");
    printStatistics(dso.getStatsString());
    printStatusTime("Shut down descriptor source");

    printStatus("Shutting down document store.");
    printStatistics(ds.getStatsString());
    printStatusTime("Shut down document store");

    printStatus("Terminating.");
  }

  private static long printedLastStatusMessage =
      System.currentTimeMillis();

  private static void printStatus(String message) {
    System.out.println(new Date() + ": " + message);
    printedLastStatusMessage = System.currentTimeMillis();
  }

  private static void printStatistics(String message) {
    System.out.print("  Statistics:\n" + message);
  }

  private static void printStatusTime(String message) {
    long now = System.currentTimeMillis();
    long millis = now - printedLastStatusMessage;
    System.out.println("  " + message + " (" + formatMillis(millis)
        + ").");
    printedLastStatusMessage = now;
  }

  // TODO This method should go into a utility class.
  private static String formatMillis(long millis) {
    return String.format("%02d:%02d.%03d minutes",
        millis / (1000L * 60L), (millis / 1000L) % 60L, millis % 1000L);
  }
}

