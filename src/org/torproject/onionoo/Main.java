/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;
import java.util.Date;
import java.util.SortedMap;

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

    printStatus("Initializing lookup service.");
    LookupService ls = new LookupService(new File("geoip"));
    printStatusTime("Initialized Geoip lookup service");

    printStatus("Updating internal node list.");
    NodeDataWriter ndw = new NodeDataWriter(dso, ls, ds);
    ndw.readStatusSummary();
    printStatusTime("Read status summary");
    ndw.readRelayNetworkConsensuses();
    printStatusTime("Read network status consensuses");
    ndw.lookUpCitiesAndASes();
    printStatusTime("Looked up cities and ASes");
    ndw.readBridgeNetworkStatuses();
    printStatusTime("Read bridge network statuses");
    ndw.setRunningBits();
    printStatusTime("Set running bits");
    ndw.writeStatusSummary();
    printStatusTime("Wrote status summary");
    SortedMap<String, NodeStatus> currentNodes = ndw.getCurrentNodes();
    SortedMap<String, Integer> lastBandwidthWeights =
        ndw.getLastBandwidthWeights();
    // TODO Could write statistics here, too.

    printStatus("Updating detail data.");
    DetailsDataWriter ddw = new DetailsDataWriter(dso, ds);
    // TODO Instead of using ndw's currentNodes and lastBandwidthWeights,
    // parse statuses once again, keeping separate parse history.  Allows
    // us to run ndw and ddw in parallel in the future.  Alternatively,
    // merge ndw and ddw, because they're doing similar things anyway.
    ddw.setCurrentNodes(currentNodes);
    printStatusTime("Set current node fingerprints");
    ddw.startReverseDomainNameLookups();
    printStatusTime("Started reverse domain name lookups");
    ddw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    ddw.calculatePathSelectionProbabilities(lastBandwidthWeights);
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
    bdw.setCurrentNodes(currentNodes);
    printStatusTime("Set current node fingerprints");
    bdw.readExtraInfoDescriptors();
    printStatusTime("Read extra-info descriptors");
    // TODO Evaluate overhead of not deleting obsolete bandwidth files.
    // An advantage would be that we don't need ndw's currentNodes
    // anymore, which allows us to run ndw and bdw in parallel in the
    // future.
    bdw.deleteObsoleteBandwidthFiles();
    printStatusTime("Deleted obsolete bandwidth files");
    // TODO Could write statistics here, too.

    printStatus("Updating weights data.");
    WeightsDataWriter wdw = new WeightsDataWriter(dso, ds);
    wdw.setCurrentNodes(currentNodes);
    printStatusTime("Set current node fingerprints");
    wdw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    wdw.readRelayNetworkConsensuses();
    printStatusTime("Read relay network consensuses");
    wdw.writeWeightsDataFiles();
    printStatusTime("Wrote weights data files");
    // TODO Evaluate overhead of not deleting obsolete weights files.  An
    // advantage would be that we don't need ndw's currentNodes anymore,
    // which allows us to run ndw and wdw in parallel in the future.
    wdw.deleteObsoleteWeightsDataFiles();
    printStatusTime("Deleted obsolete weights files");
    // TODO Could write statistics here, too.

    printStatus("Updating summary data.");
    ndw.writeOutSummary();
    printStatusTime("Wrote out summary");
    // TODO Could write statistics here, too.

    // TODO "Shut down" lookup service and write statistics about number
    // of (successfully) looked up addresses.

    printStatus("Shutting down descriptor source.");
    dso.writeHistoryFiles();
    printStatusTime("Wrote parse histories");
    printStatistics(dso.getStatsString());
    printStatusTime("Shut down descriptor source");

    printStatus("Shutting down document store.");
    ds.flushDocumentCache();
    printStatusTime("Flushed document cache");
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

