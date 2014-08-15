/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.server;

public class NodeIndexerFactory {

  private static NodeIndexer nodeIndexerInstance;
  public static void setNodeIndexer(NodeIndexer nodeIndexer) {
    nodeIndexerInstance = nodeIndexer;
  }
  public static NodeIndexer getNodeIndexer() {
    if (nodeIndexerInstance == null) {
      nodeIndexerInstance = new NodeIndexer();
    }
    return nodeIndexerInstance;
  }
}

