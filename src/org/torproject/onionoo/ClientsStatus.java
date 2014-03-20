/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

class ClientsHistory implements Comparable<ClientsHistory> {

  long startMillis;

  long endMillis;

  double totalResponses;

  SortedMap<String, Double> responsesByCountry;

  SortedMap<String, Double> responsesByTransport;

  SortedMap<String, Double> responsesByVersion;

  ClientsHistory(long startMillis, long endMillis,
      double totalResponses,
      SortedMap<String, Double> responsesByCountry,
      SortedMap<String, Double> responsesByTransport,
      SortedMap<String, Double> responsesByVersion) {
    this.startMillis = startMillis;
    this.endMillis = endMillis;
    this.totalResponses = totalResponses;
    this.responsesByCountry = responsesByCountry;
    this.responsesByTransport = responsesByTransport;
    this.responsesByVersion = responsesByVersion;
  }

  public static ClientsHistory fromString(
      String responseHistoryString) {
    String[] parts = responseHistoryString.split(" ", 8);
    if (parts.length != 8) {
      return null;
    }
    long startMillis = -1L, endMillis = -1L;
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    try {
      startMillis = dateTimeFormat.parse(parts[0] + " " + parts[1]).
          getTime();
      endMillis = dateTimeFormat.parse(parts[2] + " " + parts[3]).
          getTime();
    } catch (ParseException e) {
      return null;
    }
    if (startMillis >= endMillis) {
      return null;
    }
    double totalResponses = 0.0;
    try {
      totalResponses = Double.parseDouble(parts[4]);
    } catch (NumberFormatException e) {
      return null;
    }
    SortedMap<String, Double> responsesByCountry =
        parseResponses(parts[5]);
    SortedMap<String, Double> responsesByTransport =
        parseResponses(parts[6]);
    SortedMap<String, Double> responsesByVersion =
        parseResponses(parts[7]);
    if (responsesByCountry == null || responsesByTransport == null ||
        responsesByVersion == null) {
      return null;
    }
    return new ClientsHistory(startMillis, endMillis, totalResponses,
        responsesByCountry, responsesByTransport, responsesByVersion);
  }

  private static SortedMap<String, Double> parseResponses(
      String responsesString) {
    SortedMap<String, Double> responses = new TreeMap<String, Double>();
    if (responsesString.length() > 0) {
      for (String pair : responsesString.split(",")) {
        String[] keyValue = pair.split("=");
        if (keyValue.length != 2) {
          return null;
        }
        double value = 0.0;
        try {
          value = Double.parseDouble(keyValue[1]);
        } catch (NumberFormatException e) {
          return null;
        }
        responses.put(keyValue[0], value);
      }
    }
    return responses;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setLenient(false);
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    sb.append(dateTimeFormat.format(startMillis));
    sb.append(" " + dateTimeFormat.format(endMillis));
    sb.append(" " + String.format("%.3f", this.totalResponses));
    this.appendResponses(sb, this.responsesByCountry);
    this.appendResponses(sb, this.responsesByTransport);
    this.appendResponses(sb, this.responsesByVersion);
    return sb.toString();
  }

  private void appendResponses(StringBuilder sb,
      SortedMap<String, Double> responses) {
    sb.append(" ");
    int written = 0;
    for (Map.Entry<String, Double> e : responses.entrySet()) {
      sb.append((written++ > 0 ? "," : "") + e.getKey() + "="
          + String.format("%.3f", e.getValue()));
    }
  }

  public void addResponses(ClientsHistory other) {
    this.totalResponses += other.totalResponses;
    this.addResponsesByCategory(this.responsesByCountry,
        other.responsesByCountry);
    this.addResponsesByCategory(this.responsesByTransport,
        other.responsesByTransport);
    this.addResponsesByCategory(this.responsesByVersion,
        other.responsesByVersion);
    if (this.startMillis > other.startMillis) {
      this.startMillis = other.startMillis;
    }
    if (this.endMillis < other.endMillis) {
      this.endMillis = other.endMillis;
    }
  }

  private void addResponsesByCategory(
      SortedMap<String, Double> thisResponses,
      SortedMap<String, Double> otherResponses) {
    for (Map.Entry<String, Double> e : otherResponses.entrySet()) {
      if (thisResponses.containsKey(e.getKey())) {
        thisResponses.put(e.getKey(), thisResponses.get(e.getKey())
            + e.getValue());
      } else {
        thisResponses.put(e.getKey(), e.getValue());
      }
    }
  }

  public int compareTo(ClientsHistory other) {
    return this.startMillis < other.startMillis ? -1 :
        this.startMillis > other.startMillis ? 1 : 0;
  }

  public boolean equals(Object other) {
    return other instanceof ClientsHistory &&
        this.startMillis == ((ClientsHistory) other).startMillis;
  }

  public int hashCode() {
    return (int) this.startMillis;
  }
}

class ClientsStatus extends Document {

  SortedSet<ClientsHistory> history = new TreeSet<ClientsHistory>();

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

