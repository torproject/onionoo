/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

public abstract class Document {

  private transient String documentString;
  public void setDocumentString(String documentString) {
    this.documentString = documentString;
  }
  public String getDocumentString() {
    return this.documentString;
  }

  public void setFromDocumentString(String documentString) {
    /* Subclasses may override this method to parse documentString. */
  }

  public String toDocumentString() {
    /* Subclasses may override this method to write documentString. */
    return null;
  }
}

