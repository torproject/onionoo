package org.torproject.onionoo;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class DummyDocumentStore extends DocumentStore {

  private long lastModified;

  public DummyDocumentStore(long lastModified) {
    this.lastModified = lastModified;
  }

  private SortedMap<String, NodeStatus> nodeStatuses =
      new TreeMap<String, NodeStatus>();
  void addNodeStatus(String nodeStatusString) {
    NodeStatus nodeStatus = NodeStatus.fromString(nodeStatusString);
    this.nodeStatuses.put(nodeStatus.getFingerprint(), nodeStatus);
  }

  public void flushDocumentCache() {
    throw new RuntimeException("Not implemented.");
  }

  public String getStatsString() {
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> SortedSet<String> list(
      Class<T> documentType, boolean includeArchive) {
    if (documentType.equals(NodeStatus.class)) {
      return new TreeSet<String>(this.nodeStatuses.keySet());
    }
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> boolean remove(Class<T> documentType) {
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> boolean remove(Class<T> documentType,
      String fingerprint) {
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse) {
    if (documentType.equals(UpdateStatus.class)) {
      UpdateStatus updateStatus = new UpdateStatus();
      updateStatus.setDocumentString(String.valueOf(this.lastModified));
      return documentType.cast(updateStatus);
    }
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse, String fingerprint) {
    if (documentType.equals(NodeStatus.class)) {
      return documentType.cast(this.nodeStatuses.get(fingerprint));
    }
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> boolean store(T document) {
    throw new RuntimeException("Not implemented.");
  }

  public <T extends Document> boolean store(T document,
      String fingerprint) {
    throw new RuntimeException("Not implemented.");
  }
}

