/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;

public class ClientsStatus extends Document {

  private SortedSet<ClientsHistory> history =
      new TreeSet<ClientsHistory>();
  public void setHistory(SortedSet<ClientsHistory> history) {
    this.history = history;
  }
  public SortedSet<ClientsHistory> getHistory() {
    return this.history;
  }

  public void fromDocumentString(String documentString) {
    Scanner s = new Scanner(documentString);
    while (s.hasNextLine()) {
      String line = s.nextLine();
      ClientsHistory parsedLine = ClientsHistory.fromString(line);
      if (parsedLine != null) {
        this.history.add(parsedLine);
      } else {
        System.err.println("Could not parse clients history line '"
            + line + "'.  Skipping.");
      }
    }
    s.close();
  }

  public String toDocumentString() {
    StringBuilder sb = new StringBuilder();
    for (ClientsHistory interval : this.history) {
      sb.append(interval.toString() + "\n");
    }
    return sb.toString();
  }
}

