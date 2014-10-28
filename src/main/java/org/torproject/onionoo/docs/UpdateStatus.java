/* Copyright 2013, 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateStatus extends Document {

  private static Logger log = LoggerFactory.getLogger(UpdateStatus.class);

  private long updatedMillis;
  public void setUpdatedMillis(long updatedMillis) {
    this.updatedMillis = updatedMillis;
  }
  public long getUpdatedMillis() {
    return this.updatedMillis;
  }

  public void setFromDocumentString(String documentString) {
    try {
      this.updatedMillis = Long.parseLong(documentString.trim());
    } catch (NumberFormatException e) {
      log.error("Could not parse timestamp '" + documentString + "'.  "
          + "Setting to 1970-01-01 00:00:00.");
      this.updatedMillis = 0L;
    }
  }

  public String toDocumentString() {
    return String.valueOf(this.updatedMillis);
  }
}

