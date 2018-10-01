/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.server;

public class NodeIndexerFactory {

  private static NodeIndexer nodeIndexerInstance;

  /** Sets a custom singleton node indexer instance that will be returned
   * by {@link #getNodeIndexer()} rather than creating an instance upon
   * first invocation. */
  public static void setNodeIndexer(NodeIndexer nodeIndexer) {
    nodeIndexerInstance = nodeIndexer;
  }

  /** Returns the singleton node indexer instance that gets created upon
   * first invocation of this method. */
  public static NodeIndexer getNodeIndexer() {
    if (nodeIndexerInstance == null) {
      nodeIndexerInstance = new NodeIndexer();
    }
    return nodeIndexerInstance;
  }
}

