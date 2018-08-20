/* Copyright 2015--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class DummyDocumentStore extends DocumentStore {

  public Map<Class<? extends Document>, SortedMap<String, Document>>
      storedDocuments = new HashMap<>();

  private static final String FINGERPRINT_NULL = "";

  private <T extends Document> SortedMap<String, Document>
      getStoredDocumentsByClass(Class<T> documentType) {
    if (!this.storedDocuments.containsKey(documentType)) {
      this.storedDocuments.put(documentType, new TreeMap<>());
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
    return documentType.cast(this.getStoredDocumentsByClass(documentType)
        .get(fingerprint == null ? FINGERPRINT_NULL : fingerprint));
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

  @SuppressWarnings("JavadocMethod")
  public <T extends Document> SortedSet<String> list(
      Class<T> documentType, long modifiedAfter) {
    return this.list(documentType);
  }

  @SuppressWarnings("JavadocMethod")
  public <T extends Document> SortedSet<String> list(
      Class<T> documentType) {
    this.performedListOperations++;
    SortedSet<String> fingerprints = new TreeSet<>(
        this.getStoredDocumentsByClass(documentType).keySet());
    fingerprints.remove(FINGERPRINT_NULL);
    return fingerprints;
  }

  private int performedRemoveOperations = 0;

  @SuppressWarnings("JavadocMethod")
  public int getPerformedRemoveOperations() {
    return this.performedRemoveOperations;
  }

  public <T extends Document> boolean remove(Class<T> documentType) {
    return this.remove(documentType, null);
  }

  @SuppressWarnings("JavadocMethod")
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

  @SuppressWarnings("JavadocMethod")
  public <T extends Document> T retrieve(Class<T> documentType,
      boolean parse, String fingerprint) {
    this.performedRetrieveOperations++;
    return documentType.cast(this.getStoredDocumentsByClass(documentType)
        .get(fingerprint == null ? FINGERPRINT_NULL : fingerprint));
  }

  private int performedStoreOperations = 0;

  public int getPerformedStoreOperations() {
    return this.performedStoreOperations;
  }

  public <T extends Document> boolean store(T document) {
    return this.store(document, null);
  }

  @SuppressWarnings("JavadocMethod")
  public <T extends Document> boolean store(T document,
      String fingerprint) {
    this.performedStoreOperations++;
    this.getStoredDocumentsByClass(document.getClass()).put(
        fingerprint == null ? FINGERPRINT_NULL : fingerprint, document);
    return true;
  }
}

