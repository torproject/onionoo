/* Copyright 2011--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.cron;

import org.torproject.metrics.onionoo.docs.DocumentStore;
import org.torproject.metrics.onionoo.docs.DocumentStoreFactory;
import org.torproject.metrics.onionoo.updater.DescriptorSource;
import org.torproject.metrics.onionoo.updater.DescriptorSourceFactory;
import org.torproject.metrics.onionoo.updater.StatusUpdateRunner;
import org.torproject.metrics.onionoo.writer.DocumentWriterRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/* Update search data and status data files. */
public class Main implements Runnable {

  private Main() {}

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  /** Executes a single update run or partial update run, or initiates
   * hourly executions, depending on the given command-line arguments. */
  public static void main(String[] args) {
    Locale.setDefault(Locale.US);
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Main main = new Main();
    main.parseArgsOrExit(args);
    main.runOrScheduleExecutions();
  }

  boolean defaultMode = false;

  boolean singleRun = false;

  boolean downloadOnly = false;

  boolean updateOnly = false;

  boolean writeOnly = false;

  /* TODO Parsing command-line arguments is only a workaround until we're
   * more certain what kind of options we want to support.  We should then
   * switch to some library that parses options for us. */
  private void parseArgsOrExit(String[] args) {
    boolean validArgs = true;
    if (args.length == 0) {
      this.defaultMode = true;
    } else if (args.length == 1) {
      switch (args[0]) {
        case "--help":
          this.printUsageAndExit(0);
          break;
        case "--single-run":
          this.singleRun = true;
          break;
        case "--download-only":
          this.downloadOnly = true;
          break;
        case "--update-only":
          this.updateOnly = true;
          break;
        case "--write-only":
          this.writeOnly = true;
          break;
        default:
          validArgs = false;
      }
    } else if (args.length > 1) {
      validArgs = false;
    }
    if (!validArgs) {
      this.printUsageAndExit(1);
    }
  }

  private void printUsageAndExit(int status) {
    System.err.println("Please provide only a single execution:");
    System.err.println("  [no argument]    Run steps 1--3 repeatedly "
        + "once per hour.");
    System.err.println("  --single-run     Run steps 1--3 only for a "
        + "single time, then exit.");
    System.err.println("  --download-only  Only run step 1: download "
        + "recent descriptors, then exit.");
    System.err.println("  --update-only    Only run step 2: update "
        + "internal status files, then exit.");
    System.err.println("  --write-only     Only run step 3: write "
        + "output document files, then exit.");
    System.err.println("  --help           Print out this help message "
        + "and exit.");
    System.exit(status);
  }

  private void runOrScheduleExecutions() {
    if (!this.defaultMode) {
      logger.info("Going to run one-time updater ... ");
      this.run();
    } else {
      this.scheduleExecutions();
    }
  }

  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(1);

  private void scheduleExecutions() {
    logger.info("Periodic updater started.");
    final Runnable mainRunnable = this;
    int currentMinute = Calendar.getInstance().get(Calendar.MINUTE);
    int initialDelay = (75 - currentMinute + currentMinute % 5) % 60;

    /* Run after initialDelay delay and then every hour. */
    logger.info("Periodic updater will start every hour at minute {}.",
        (currentMinute + initialDelay) % 60);
    this.scheduler.scheduleAtFixedRate(mainRunnable, initialDelay, 60,
        TimeUnit.MINUTES);
  }

  @Override
  public void run() {
    this.initialize();
    this.downloadDescriptors();
    this.updateStatuses();
    this.writeDocuments();
    this.shutDown();
    this.gatherStatistics();
    this.cleanUp();
  }

  private DescriptorSource dso;

  private DocumentStore ds;

  private File outDir = new File("out");

  private StatusUpdateRunner sur;

  private DocumentWriterRunner dwr;

  private void initialize() {
    logger.debug("Started update ...");
    if (!this.writeOnly) {
      this.dso = DescriptorSourceFactory.getDescriptorSource();
      logger.info("Initialized descriptor source");
    }
    if (!this.downloadOnly) {
      this.ds = DocumentStoreFactory.getDocumentStore();
      logger.info("Initialized document store");
    }
    if (!this.downloadOnly && !this.writeOnly) {
      this.sur = new StatusUpdateRunner();
      logger.info("Initialized status update runner");
    }
    if (!this.downloadOnly && !this.updateOnly) {
      this.ds.setOutDir(outDir);
      this.dwr = new DocumentWriterRunner();
      logger.info("Initialized document writer runner");
    }
  }

  private void downloadDescriptors() {
    if (this.updateOnly || this.writeOnly) {
      return;
    }
    logger.info("Downloading descriptors.");
    this.dso.downloadDescriptors();
  }

  private void updateStatuses() {
    if (this.downloadOnly || this.writeOnly) {
      return;
    }
    logger.info("Reading descriptors.");
    this.dso.readDescriptors();
    logger.info("Updating internal status files.");
    this.sur.updateStatuses();
  }

  private void writeDocuments() {
    if (this.downloadOnly || this.updateOnly) {
      return;
    }
    logger.info("Updating document files.");
    this.dwr.writeDocuments();
  }

  private void shutDown() {
    logger.info("Shutting down.");
    if (this.dso != null) {
      this.dso.writeHistoryFiles();
      logger.info("Wrote parse histories");
    }
    if (this.ds != null) {
      this.ds.flushDocumentCache();
      logger.info("Flushed document cache");
    }
  }

  private void gatherStatistics() {
    logger.info("Gathering statistics.");
    if (this.sur != null) {
      this.sur.logStatistics();
    }
    if (this.dwr != null) {
      this.dwr.logStatistics();
    }
    if (this.dso != null) {
      logger.info("Descriptor source\n{}", this.dso.getStatsString());
    }
    if (this.ds != null) {
      logger.info("Document store\n{}", this.ds.getStatsString());
    }
  }

  private void cleanUp() {
    /* Clean up to prevent out-of-memory exception, and to ensure that the
     * next execution starts with a fresh descriptor source. */
    logger.info("Cleaning up.");
    if (this.ds != null) {
      this.ds.invalidateDocumentCache();
    }
    DocumentStoreFactory.setDocumentStore(null);
    DescriptorSourceFactory.setDescriptorSource(null);
    logger.info("Done.");
  }
}

