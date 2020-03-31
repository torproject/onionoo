/* Copyright 2014--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class StatusUpdateRunner {

  private static final Logger logger = LoggerFactory.getLogger(
      StatusUpdateRunner.class);

  private LookupService ls;

  private ReverseDomainNameResolver rdnr;

  private StatusUpdater[] statusUpdaters;

  /** Instantiates a new status update runner with newly created instances
   * of all known status updater implementations. */
  public StatusUpdateRunner() {
    this.ls = new LookupService(new File("geoip"));
    this.rdnr = new ReverseDomainNameResolver();
    NodeDetailsStatusUpdater ndsu = new NodeDetailsStatusUpdater(
        this.rdnr, this.ls);
    BandwidthStatusUpdater bsu = new BandwidthStatusUpdater();
    WeightsStatusUpdater wsu = new WeightsStatusUpdater();
    ClientsStatusUpdater csu = new ClientsStatusUpdater();
    UptimeStatusUpdater usu = new UptimeStatusUpdater();
    this.statusUpdaters = new StatusUpdater[] { ndsu, bsu, wsu, csu,
        usu };
  }

  /** Lets each configured status updater update its status files. */
  public void updateStatuses() {
    for (StatusUpdater su : this.statusUpdaters) {
      logger.debug("Begin update of {}", su.getClass().getSimpleName());
      su.updateStatuses();
      logger.info("{} updated status files", su.getClass().getSimpleName());
    }
  }

  /** Logs statistics of all configured status updaters. */
  public void logStatistics() {
    for (StatusUpdater su : this.statusUpdaters) {
      String statsString = su.getStatsString();
      if (statsString != null) {
        logger.info("{}\n{}", su.getClass().getSimpleName(), statsString);
      }
    }
    logger.info("GeoIP lookup service\n{}", this.ls.getStatsString());
    logger.info("Reverse domain name resolver\n{}", this.rdnr.getStatsString());
  }
}

