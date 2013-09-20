/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    Logger.printStatus("Initializing.");

    LockFile lf = new LockFile(new File("lock"));
    if (lf.acquireLock()) {
      Logger.printStatusTime("Acquired lock");
    } else {
      Logger.printStatusTime("Could not acquire lock.  Is Onionoo "
          + "already running?  Terminating");
      return;
    }

    DescriptorSource dso = new DescriptorSource(new File("in"),
        new File("status"));
    Logger.printStatusTime("Initialized descriptor source");
    DocumentStore ds = new DocumentStore(new File("status"),
        new File("out"));
    Logger.printStatusTime("Initialized document store");
    LookupService ls = new LookupService(new File("geoip"));
    Logger.printStatusTime("Initialized Geoip lookup service");
    ReverseDomainNameResolver rdnr = new ReverseDomainNameResolver();
    Logger.printStatusTime("Initialized reverse domain name resolver");
    NodeDataWriter ndw = new NodeDataWriter(dso, rdnr, ls, ds);
    Logger.printStatusTime("Initialized node data writer");
    BandwidthDataWriter bdw = new BandwidthDataWriter(dso, ds);
    Logger.printStatusTime("Initialized bandwidth data writer");
    WeightsDataWriter wdw = new WeightsDataWriter(dso, ds);
    Logger.printStatusTime("Initialized weights data writer");

    Logger.printStatus("Reading descriptors.");
    dso.readRelayNetworkConsensuses();
    Logger.printStatusTime("Read relay network consensuses");
    dso.readRelayServerDescriptors();
    Logger.printStatusTime("Read relay server descriptors");
    dso.readRelayExtraInfos();
    Logger.printStatusTime("Read relay extra-info descriptors");
    dso.readExitLists();
    Logger.printStatusTime("Read exit lists");
    dso.readBridgeNetworkStatuses();
    Logger.printStatusTime("Read bridge network statuses");
    dso.readBridgeServerDescriptors();
    Logger.printStatusTime("Read bridge server descriptors");
    dso.readBridgeExtraInfos();
    Logger.printStatusTime("Read bridge extra-info descriptors");
    dso.readBridgePoolAssignments();
    Logger.printStatusTime("Read bridge-pool assignments");

    Logger.printStatus("Updating internal node list.");
    ndw.readStatusSummary();
    Logger.printStatusTime("Read status summary");
    ndw.setCurrentNodes();
    Logger.printStatusTime("Set current node fingerprints");
    ndw.startReverseDomainNameLookups();
    Logger.printStatusTime("Started reverse domain name lookups");
    ndw.lookUpCitiesAndASes();
    Logger.printStatusTime("Looked up cities and ASes");
    ndw.setRunningBits();
    Logger.printStatusTime("Set running bits");
    ndw.calculatePathSelectionProbabilities();
    Logger.printStatusTime("Calculated path selection probabilities");
    ndw.finishReverseDomainNameLookups();
    Logger.printStatusTime("Finished reverse domain name lookups");
    ndw.writeStatusSummary();
    Logger.printStatusTime("Wrote status summary");
    ndw.writeOutSummary();
    Logger.printStatusTime("Wrote out summary");
    ndw.writeOutDetails();
    Logger.printStatusTime("Wrote detail data files");

    Logger.printStatus("Updating bandwidth data.");

    Logger.printStatus("Updating weights data.");
    wdw.updateWeightsHistories();
    Logger.printStatusTime("Updated weights histories");
    wdw.updateWeightsStatuses();
    Logger.printStatusTime("Updated weights status files");
    wdw.writeWeightsDataFiles();
    Logger.printStatusTime("Wrote weights document files");

    Logger.printStatus("Shutting down.");
    dso.writeHistoryFiles();
    Logger.printStatusTime("Wrote parse histories");
    ds.flushDocumentCache();
    Logger.printStatusTime("Flushed document cache");

    Logger.printStatus("Gathering statistics.");
    Logger.printStatistics("Node data writer", ndw.getStatsString());
    /* TODO Add statistics to remaining *Writers. */
    //printStatistics("Details data writer", ddw.getStatsString());
    //printStatistics("Bandwidth data writer", bdw.getStatsString());
    //printStatistics("Weights data writer", wdw.getStatsString());
    Logger.printStatistics("Descriptor source", dso.getStatsString());
    Logger.printStatistics("Document store", ds.getStatsString());
    Logger.printStatistics("GeoIP lookup service", ls.getStatsString());
    Logger.printStatistics("Reverse domain name resolver",
        rdnr.getStatsString());

    Logger.printStatus("Releasing lock.");
    if (lf.releaseLock()) {
      Logger.printStatusTime("Released lock");
    } else {
      Logger.printStatusTime("Could not release lock.  The next "
          + "execution may not start as expected");
    }

    Logger.printStatus("Terminating.");
  }
}

