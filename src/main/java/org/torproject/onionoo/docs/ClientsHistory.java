/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ClientsHistory implements Comparable<ClientsHistory> {

  private long startMillis;
  public long getStartMillis() {
    return this.startMillis;
  }

  private long endMillis;
  public long getEndMillis() {
    return this.endMillis;
  }

  private double totalResponses;
  public double getTotalResponses() {
    return this.totalResponses;
  }

  private SortedMap<String, Double> responsesByCountry;
  public SortedMap<String, Double> getResponsesByCountry() {
    return this.responsesByCountry;
  }

  private SortedMap<String, Double> responsesByTransport;
  public SortedMap<String, Double> getResponsesByTransport() {
    return this.responsesByTransport;
  }

  private SortedMap<String, Double> responsesByVersion;
  public SortedMap<String, Double> getResponsesByVersion() {
    return this.responsesByVersion;
  }

  public ClientsHistory(long startMillis, long endMillis,
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
    long startMillis = DateTimeHelper.parse(parts[0] + " " + parts[1]);
    long endMillis = DateTimeHelper.parse(parts[2] + " " + parts[3]);
    if (startMillis < 0L || endMillis < 0L) {
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
    sb.append(DateTimeHelper.format(startMillis));
    sb.append(" " + DateTimeHelper.format(endMillis));
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