/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.updater;

import java.io.File;

import org.torproject.onionoo.util.Logger;

public class StatusUpdateRunner {

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
      su.updateStatuses();
      Logger.printStatusTime(su.getClass().getSimpleName()
          + " updated status files");
    }
  }

  public void logStatistics() {
    for (StatusUpdater su : this.statusUpdaters) {
      String statsString = su.getStatsString();
      if (statsString != null) {
        Logger.printStatistics(su.getClass().getSimpleName(),
            statsString);
      }
    }
    Logger.printStatistics("GeoIP lookup service",
        this.ls.getStatsString());
    Logger.printStatistics("Reverse domain name resolver",
        this.rdnr.getStatsString());
  }
}
