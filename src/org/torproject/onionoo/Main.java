/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;
import java.util.Date;
import java.util.SortedMap;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    printStatus("Initializing.");
    DescriptorSource dso = new DescriptorSource(new File("in"),
        new File("status"));
    printStatusTime("Initialized descriptor source");
    DocumentStore ds = new DocumentStore(new File("status"),
        new File("out"));
    printStatusTime("Initialized document store");
    LookupService ls = new LookupService(new File("geoip"));
    printStatusTime("Initialized Geoip lookup service");
    ReverseDomainNameResolver rdnr = new ReverseDomainNameResolver();
    printStatusTime("Initialized reverse domain name resolver");
    NodeDataWriter ndw = new NodeDataWriter(dso, ls, ds);
    printStatusTime("Initialized node data writer");
    DetailsDataWriter ddw = new DetailsDataWriter(dso, rdnr, ds);
    printStatusTime("Initialized details data writer");
    BandwidthDataWriter bdw = new BandwidthDataWriter(dso, ds);
    printStatusTime("Initialized bandwidth data writer");
    WeightsDataWriter wdw = new WeightsDataWriter(dso, ds);
    printStatusTime("Initialized weights data writer");

    // TODO Instead of creating nine, partly overlapping descriptor
    // queues, register for descriptor type and let DescriptorSource
    // parse everything just once.
    printStatus("Reading descriptors.");
    ndw.readRelayNetworkConsensuses();
    printStatusTime("Read network status consensuses");
    ndw.readBridgeNetworkStatuses();
    printStatusTime("Read bridge network statuses");
    ddw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    ddw.readExitLists();
    printStatusTime("Read exit lists");
    ddw.readBridgeServerDescriptors();
    printStatusTime("Read bridge server descriptors");
    ddw.readBridgePoolAssignments();
    printStatusTime("Read bridge-pool assignments");
    bdw.readExtraInfoDescriptors();
    printStatusTime("Read extra-info descriptors");
    wdw.readRelayServerDescriptors();
    printStatusTime("Read relay server descriptors");
    wdw.readRelayNetworkConsensuses();
    printStatusTime("Read relay network consensuses");

    printStatus("Updating internal node list.");
    ndw.lookUpCitiesAndASes();
    printStatusTime("Looked up cities and ASes");
    ndw.setRunningBits();
    printStatusTime("Set running bits");
    ndw.writeStatusSummary();
    printStatusTime("Wrote status summary");
    ndw.writeOutSummary();
    printStatusTime("Wrote out summary");
    SortedMap<String, NodeStatus> currentNodes = ndw.getCurrentNodes();
    SortedMap<String, Integer> lastBandwidthWeights =
        ndw.getLastBandwidthWeights();

    printStatus("Updating detail data.");
    // TODO Instead of using ndw's currentNodes and lastBandwidthWeights,
    // parse statuses once again, keeping separate parse history.  Allows
    // us to run ndw and ddw in parallel in the future.  Alternatively,
    // merge ndw and ddw, because they're doing similar things anyway.
    ddw.setCurrentNodes(currentNodes);
    printStatusTime("Set current node fingerprints");
    ddw.startReverseDomainNameLookups();
    printStatusTime("Started reverse domain name lookups");
    ddw.calculatePathSelectionProbabilities(lastBandwidthWeights);
    printStatusTime("Calculated path selection probabilities");
    ddw.finishReverseDomainNameLookups();
    printStatusTime("Finished reverse domain name lookups");
    ddw.writeOutDetails();
    printStatusTime("Wrote detail data files");

    printStatus("Updating bandwidth data.");
    bdw.setCurrentNodes(currentNodes);
    printStatusTime("Set current node fingerprints");
    // TODO Evaluate overhead of not deleting obsolete bandwidth files.
    // An advantage would be that we don't need ndw's currentNodes
    // anymore, which allows us to run ndw and bdw in parallel in the
    // future.
    bdw.deleteObsoleteBandwidthFiles();
    printStatusTime("Deleted obsolete bandwidth files");

    printStatus("Updating weights data.");
    wdw.setCurrentNodes(currentNodes);
    printStatusTime("Set current node fingerprints");
    wdw.writeWeightsDataFiles();
    printStatusTime("Wrote weights data files");
    // TODO Evaluate overhead of not deleting obsolete weights files.  An
    // advantage would be that we don't need ndw's currentNodes anymore,
    // which allows us to run ndw and wdw in parallel in the future.
    wdw.deleteObsoleteWeightsDataFiles();
    printStatusTime("Deleted obsolete weights files");

    printStatus("Shutting down.");
    dso.writeHistoryFiles();
    printStatusTime("Wrote parse histories");
    ds.flushDocumentCache();
    printStatusTime("Flushed document cache");

    printStatus("Gathering statistics.");
    printStatistics("Node data writer", ndw.getStatsString());
    /* TODO Add statistics to remaining *Writers. */
    //printStatistics("Details data writer", ddw.getStatsString());
    //printStatistics("Bandwidth data writer", bdw.getStatsString());
    //printStatistics("Weights data writer", wdw.getStatsString());
    printStatistics("Descriptor source", dso.getStatsString());
    printStatistics("Document store", ds.getStatsString());
    printStatistics("GeoIP lookup service", ls.getStatsString());
    printStatistics("Reverse domain name resolver",
        rdnr.getStatsString());

    printStatus("Terminating.");
  }

  private static long printedLastStatusMessage =
      System.currentTimeMillis();

  private static void printStatus(String message) {
    System.out.println(new Date() + ": " + message);
    printedLastStatusMessage = System.currentTimeMillis();
  }

  private static void printStatistics(String component, String message) {
    System.out.print("  " + component + " statistics:\n" + message);
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

