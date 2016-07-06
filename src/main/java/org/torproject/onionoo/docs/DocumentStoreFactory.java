/* Copyright 2014--2016 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

public class DocumentStoreFactory {

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
}

