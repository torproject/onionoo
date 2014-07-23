package org.torproject.onionoo;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.torproject.onionoo.docs.Document;
import org.torproject.onionoo.docs.DocumentStore;

public class DummyDocumentStore extends DocumentStore {

  private Map<Class<? extends Document>, SortedMap<String, Document>>
      storedDocuments = new HashMap<Class<? extends Document>,
      SortedMap<String, Document>>();

  private static final String FINGERPRINT_NULL = "";

  private <T extends Document> SortedMap<String, Document>
      getStoredDocumentsByClass(Class<T> documentType) {
    if (!this.storedDocuments.containsKey(documentType)) {
      this.storedDocuments.put(documentType,
          new TreeMap<String, Document>());
    }
    return this.storedDocuments.get(documentType);
  }

  public <T extends Document> void addDocument(T document,
      String fingerprint) {
    this.getStoredDocumentsByClass(document.getClass()).put(
        fingerprint == null ? FINGERPRINT_NULL : fingerprint, document);
  }

  public <T extends Document> T getDocument(Class<T> documentType,
      String fingerprint) {
    return documentType.cast(this.getStoredDocumentsByClass(documentType).
        get(fingerprint == null ? FINGERPRINT_NULL : fingerprint));
  }

  public void flushDocumentCache() {
    /* Nothing to do. */
  }

  public String getStatsString() {
    /* No statistics to return. */
    return null;
  }

  private int performedListOperations = 0;
  public int getPerformedListOperations() {
    return this.performedListOperations;
  }

  public <T extends Document> SortedSet<String> list(
      Class<T> documentType) {
    this.performedListOperations++;
    return new TreeSet<String>(this.getStoredDocumentsByClass(
        documentType).keySet());
  }

  private int performedRemoveOperations = 0;
  public int getPerformedRemoveOperations() {
    return this.performedRemoveOperations;
  }

  public <T extends Document> boolean remove(Class<T> documentType) {
    return this.remove(documentType, null);
  }

  public <T extends Document> boolean remove(Class<T> documentType,
      String fingerprint) {
    this.performedRemoveOperations++;
    return this.getStoredDocumentsByClass(documentType).remove(
        fingerprint) != null;
  }

  private int performedRetrieveOperations = 0;
  public int getPerformedRetrieveOperations() {
    return this.performedRetrieveOperations;
  }

  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse) {
    return this.retrieve(documentType, parse, null);
  }

  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse, String fingerprint) {
    this.performedRetrieveOperations++;
    return documentType.cast(this.getStoredDocumentsByClass(documentType).
        get(fingerprint == null ? FINGERPRINT_NULL : fingerprint));
  }

  private int performedStoreOperations = 0;
  public int getPerformedStoreOperations() {
    return this.performedStoreOperations;
  }

  public <T extends Document> boolean store(T document) {
    return this.store(document, null);
  }

  public <T extends Document> boolean store(T document,
      String fingerprint) {
    this.performedStoreOperations++;
    this.getStoredDocumentsByClass(document.getClass()).put(
        fingerprint == null ? FINGERPRINT_NULL : fingerprint, document);
    return true;
  }
}

