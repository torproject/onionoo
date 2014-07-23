/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.cron;

import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.updater.DescriptorSource;
import org.torproject.onionoo.updater.StatusUpdateRunner;
import org.torproject.onionoo.util.ApplicationFactory;
import org.torproject.onionoo.util.LockFile;
import org.torproject.onionoo.util.Logger;
import org.torproject.onionoo.writer.DocumentWriterRunner;

/* Update search data and status data files. */
public class Main {

  private Main() {
  }

  public static void main(String[] args) {

    LockFile lf = new LockFile();
    Logger.setTime();
    Logger.printStatus("Initializing.");
    if (lf.acquireLock()) {
      Logger.printStatusTime("Acquired lock");
    } else {
      Logger.printErrorTime("Could not acquire lock.  Is Onionoo "
          + "already running?  Terminating");
      return;
    }

    DescriptorSource dso = ApplicationFactory.getDescriptorSource();
    Logger.printStatusTime("Initialized descriptor source");
    DocumentStore ds = ApplicationFactory.getDocumentStore();
    Logger.printStatusTime("Initialized document store");
    StatusUpdateRunner sur = new StatusUpdateRunner();
    Logger.printStatusTime("Initialized status update runner");
    DocumentWriterRunner dwr = new DocumentWriterRunner();
    Logger.printStatusTime("Initialized document writer runner");

    Logger.printStatus("Downloading descriptors.");
    dso.downloadDescriptors();

    Logger.printStatus("Reading descriptors.");
    dso.readDescriptors();

    Logger.printStatus("Updating internal status files.");
    sur.updateStatuses();

    Logger.printStatus("Updating document files.");
    dwr.writeDocuments();

    Logger.printStatus("Shutting down.");
    dso.writeHistoryFiles();
    Logger.printStatusTime("Wrote parse histories");
    ds.flushDocumentCache();
    Logger.printStatusTime("Flushed document cache");

    Logger.printStatus("Gathering statistics.");
    sur.logStatistics();
    dwr.logStatistics();
    Logger.printStatistics("Descriptor source", dso.getStatsString());
    Logger.printStatistics("Document store", ds.getStatsString());

    Logger.printStatus("Releasing lock.");
    if (lf.releaseLock()) {
      Logger.printStatusTime("Released lock");
    } else {
      Logger.printErrorTime("Could not release lock.  The next "
          + "execution may not start as expected");
    }

    Logger.printStatus("Terminating.");
  }
}

