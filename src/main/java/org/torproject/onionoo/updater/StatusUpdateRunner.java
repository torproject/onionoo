/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusUpdateRunner {

  private static final Logger log = LoggerFactory.getLogger(
      StatusUpdateRunner.class);

  private LookupService ls;

  private ReverseDomainNameResolver rdnr;

  private StatusUpdater[] statusUpdaters;

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

  public void updateStatuses() {
    for (StatusUpdater su : this.statusUpdaters) {
      log.debug("Begin update of " + su.getClass().getSimpleName());
      su.updateStatuses();
      log.info(su.getClass().getSimpleName()
          + " updated status files");
    }
  }

  public void logStatistics() {
    for (StatusUpdater su : this.statusUpdaters) {
      String statsString = su.getStatsString();
      if (statsString != null) {
        LoggerFactory.getLogger("statistics").info(
            su.getClass().getSimpleName(), statsString);
      }
    }
    LoggerFactory.getLogger("statistics").info("GeoIP lookup service",
        this.ls.getStatsString());
    LoggerFactory.getLogger("statistics").info("Reverse domain name "
        + "resolver", this.rdnr.getStatsString());
  }
}
