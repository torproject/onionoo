/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.torproject.onionoo.docs.DocumentStore;
import org.torproject.onionoo.docs.DocumentStoreFactory;
import org.torproject.onionoo.updater.DescriptorSource;
import org.torproject.onionoo.updater.DescriptorSourceFactory;
import org.torproject.onionoo.updater.StatusUpdateRunner;
import org.torproject.onionoo.util.LockFile;
import org.torproject.onionoo.writer.DocumentWriterRunner;

/* Update search data and status data files. */
public class Main {

  private static Logger log = LoggerFactory.getLogger(Main.class);

  private Main() {
  }

  public static void main(String[] args) {
    log.debug("Started ...");
    LockFile lf = new LockFile();
    log.info("Initializing.");
    if (lf.acquireLock()) {
      log.info("Acquired lock");
    } else {
      log.error("Could not acquire lock.  Is Onionoo "
          + "already running?  Terminating");
      return;
    }
    log.debug(" ... running .... ");

    DescriptorSource dso = DescriptorSourceFactory.getDescriptorSource();
    log.info("Initialized descriptor source");
    DocumentStore ds = DocumentStoreFactory.getDocumentStore();
    log.info("Initialized document store");
    StatusUpdateRunner sur = new StatusUpdateRunner();
    log.info("Initialized status update runner");
    DocumentWriterRunner dwr = new DocumentWriterRunner();
    log.info("Initialized document writer runner");

    log.info("Downloading descriptors.");
    dso.downloadDescriptors();

    log.info("Reading descriptors.");
    dso.readDescriptors();

    log.info("Updating internal status files.");
    sur.updateStatuses();

    log.info("Updating document files.");
    dwr.writeDocuments();

    log.info("Shutting down.");
    dso.writeHistoryFiles();
    log.info("Wrote parse histories");
    ds.flushDocumentCache();
    log.info("Flushed document cache");

    log.info("Gathering statistics.");
    sur.logStatistics();
    dwr.logStatistics();
    log.info("Descriptor source", dso.getStatsString());
    log.info("Document store", ds.getStatsString());

    log.info("Releasing lock.");
    if (lf.releaseLock()) {
      log.info("Released lock");
    } else {
      log.error("Could not release lock.  The next "
          + "execution may not start as expected");
    }

    log.info("Terminating.");
  }
}

