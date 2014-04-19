/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

public class ApplicationFactory {

  private static Time timeInstance;
  public static void setTime(Time time) {
    timeInstance = time;
  }
  public static Time getTime() {
    if (timeInstance == null) {
      timeInstance = new Time();
    }
    return timeInstance;
  }

  private static DescriptorSource descriptorSourceInstance;
  public static void setDescriptorSource(
      DescriptorSource descriptorSource) {
    descriptorSourceInstance = descriptorSource;
  }
  public static DescriptorSource getDescriptorSource() {
    if (descriptorSourceInstance == null) {
      descriptorSourceInstance = new DescriptorSource();
    }
    return descriptorSourceInstance;
  }

  private static DocumentStore documentStoreInstance;
  public static void setDocumentStore(DocumentStore documentStore) {
    documentStoreInstance = documentStore;
  }
  public static DocumentStore getDocumentStore() {
    if (documentStoreInstance == null) {
      documentStoreInstance = new DocumentStore();
    }
    return documentStoreInstance;
  }

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
