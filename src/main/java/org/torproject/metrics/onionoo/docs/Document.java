/* Copyright 2013--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/* Use snake_case for naming fields rather than camelCase. */
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
/* Exclude fields that are null or empty. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
/* Only consider fields, no getters, setters, or constructors. */
@JsonAutoDetect(creatorVisibility = JsonAutoDetect.Visibility.NONE,
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
/* Ignore unknown properties including previously deprecated and later removed
 * fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
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

