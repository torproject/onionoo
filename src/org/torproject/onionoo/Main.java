/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.File;

/* Update search data and status data files. */
public class Main {
  public static void main(String[] args) {

    Time t = new Time();
    LockFile lf = new LockFile(new File("lock"), t);
    Logger.setTime(t);
    Logger.printStatus("Initializing.");
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
        new File("out"), t);
    Logger.printStatusTime("Initialized document store");
    LookupService ls = new LookupService(new File("geoip"));
    Logger.printStatusTime("Initialized Geoip lookup service");
    ReverseDomainNameResolver rdnr = new ReverseDomainNameResolver(t);
    Logger.printStatusTime("Initialized reverse domain name resolver");
    NodeDataWriter ndw = new NodeDataWriter(dso, rdnr, ls, ds, t);
    Logger.printStatusTime("Initialized node data writer");
    BandwidthDataWriter bdw = new BandwidthDataWriter(dso, ds, t);
    Logger.printStatusTime("Initialized bandwidth data writer");
    WeightsDataWriter wdw = new WeightsDataWriter(dso, ds, t);
    Logger.printStatusTime("Initialized weights data writer");
    ClientsDataWriter cdw = new ClientsDataWriter(dso, ds, t);
    Logger.printStatusTime("Initialized clients data writer");
    UptimeDataWriter udw = new UptimeDataWriter(dso, ds, t);
    Logger.printStatusTime("Initialized uptime data writer");
    StatusUpdater[] sus = new StatusUpdater[] { ndw, bdw, wdw, cdw, udw };
    DocumentWriter[] dws = new DocumentWriter[] { ndw, bdw, wdw, cdw,
        udw };

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

    Logger.printStatus("Updating internal status files.");
    for (StatusUpdater su : sus) {
      su.updateStatuses();
    }

    Logger.printStatus("Updating document files.");
    for (DocumentWriter dw : dws) {
      dw.writeDocuments();
    }

    Logger.printStatus("Shutting down.");
    dso.writeHistoryFiles();
    Logger.printStatusTime("Wrote parse histories");
    ds.flushDocumentCache();
    Logger.printStatusTime("Flushed document cache");

    Logger.printStatus("Gathering statistics.");
    for (StatusUpdater su : sus) {
      String statsString = su.getStatsString();
      if (statsString != null) {
        Logger.printStatistics(su.getClass().getSimpleName(),
            statsString);
      }
    }
    /* TODO Print status updater statistics once all data writers have
     * been separated. */
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

