/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {

  private static final Logger log = LoggerFactory.getLogger(
      ServerMain.class);

  /** Starts the web server listening for incoming client connections. */
  public static void main(String[] args) {
    try {
      Resource onionooXml = Resource.newSystemResource("jetty.xml");
      log.info("Reading configuration from '{}'.", onionooXml);
      XmlConfiguration configuration = new XmlConfiguration(
          onionooXml.getInputStream());
      Server server = (Server) configuration.configure();
      server.start();
      server.join();
    } catch (Exception ex) {
      log.error("Exiting, because of: {}", ex.getMessage(), ex);
      System.exit(1);
    }
  }
}

