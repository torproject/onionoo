/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.cron;

import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

  private static final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(1);

  public static void main(String[] args) {
    boolean runOnce = "true".equals(System.getProperty(
        "onionoo.cron.runonce", "true"));
    if (runOnce){
      log.info("Going to run one-time updater ... ");
      LockFile lf = new LockFile();
      log.info("Initializing.");
      if (lf.acquireLock()) {
        log.info("Acquired lock");
      } else {
        log.error("Could not acquire lock.  Is Onionoo already running?  "
            + "Terminating");
        return;
      }
      new Updater().run();
      log.info("Releasing lock.");
      if (lf.releaseLock()) {
        log.info("Released lock");
      } else {
        log.error("Could not release lock.  The next execution may not "
            + "start as expected");
      }
      return;
    } else {
      log.info("Periodic updater started.");
      final Runnable updater = new Updater();
      int currentMinute = Calendar.getInstance().get(Calendar.MINUTE);
      int initialDelay = (75 - currentMinute + currentMinute % 5) % 60;

      /* Run after initialDelay delay and then every hour. */
      log.info("Periodic updater will start every hour at minute "
          + ((currentMinute + initialDelay) % 60) + ".");
      scheduler.scheduleAtFixedRate(updater, initialDelay, 60,
          TimeUnit.MINUTES);
    }
  }

  private static class Updater implements Runnable{

    private Logger log = LoggerFactory.getLogger(Main.class);

    public void run() {
      log.debug("Started update ...");

      DescriptorSource dso =
          DescriptorSourceFactory.getDescriptorSource();
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
      log.info("Descriptor source\n" + dso.getStatsString());
      log.info("Document store\n" + ds.getStatsString());

      /* Clean up to prevent out-of-memory exception, and to ensure that
       * the next execution starts with a fresh descriptor source. */
      log.info("Cleaning up.");
      ds.invalidateDocumentCache();
      DocumentStoreFactory.setDocumentStore(null);
      DescriptorSourceFactory.setDescriptorSource(null);

      log.info("Done.");
    }
  }
}

