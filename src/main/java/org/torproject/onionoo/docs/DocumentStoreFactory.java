/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

public class DocumentStoreFactory {

  private static DocumentStore documentStoreInstance;

  /** Sets a custom singleton document store instance that will be
   * returned by {@link #getDocumentStore()} rather than creating an
   * instance upon first invocation. */
  public static void setDocumentStore(DocumentStore documentStore) {
    documentStoreInstance = documentStore;
  }

  /** Returns the singleton document store instance that gets created upon
   * first invocation of this method. */
  public static DocumentStore getDocumentStore() {
    if (documentStoreInstance == null) {
      documentStoreInstance = new DocumentStore();
    }
    return documentStoreInstance;
  }
}

