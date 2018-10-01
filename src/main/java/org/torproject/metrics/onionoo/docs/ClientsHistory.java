/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.docs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ClientsHistory implements Comparable<ClientsHistory> {

  private static final Logger log = LoggerFactory.getLogger(
      ClientsHistory.class);

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

  /** Instantiates a new clients history object with given interval start
   * and end, total responses, and responses by country, transport, and
   * version. */
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

  /** Instantiates a new clients history object from the given string that
   * may have been produced by {@link #toString()}. */
  public static ClientsHistory fromString(
      String responseHistoryString) {
    String[] parts = responseHistoryString.split(" ", 8);
    if (parts.length != 8) {
      log.warn("Invalid number of space-separated strings in clients history: "
          + "'{}'.  Skipping", responseHistoryString);
      return null;
    }
    long startMillis = DateTimeHelper.parse(parts[0] + " " + parts[1]);
    long endMillis = DateTimeHelper.parse(parts[2] + " " + parts[3]);
    if (startMillis < 0L || endMillis < 0L) {
      log.warn("Invalid start or end timestamp in clients history: '{}'. "
          + "Skipping.", responseHistoryString);
      return null;
    }
    if (startMillis >= endMillis) {
      log.warn("Start timestamp must be smaller than end timestamp in clients "
          + "history: '{}'.  Skipping.", responseHistoryString);
      return null;
    }
    double totalResponses;
    try {
      totalResponses = Double.parseDouble(parts[4]);
    } catch (NumberFormatException e) {
      log.warn("Invalid response number format in clients history: '{}'. "
          + "Skipping.", responseHistoryString);
      return null;
    }
    SortedMap<String, Double> responsesByCountry =
        parseResponses(parts[5]);
    SortedMap<String, Double> responsesByTransport =
        parseResponses(parts[6]);
    SortedMap<String, Double> responsesByVersion =
        parseResponses(parts[7]);
    if (responsesByCountry == null || responsesByTransport == null
        || responsesByVersion == null) {
      log.warn("Invalid format of responses by country, transport, or version "
          + "in clients history: '{}'.  Skipping.", responseHistoryString);
      return null;
    }
    return new ClientsHistory(startMillis, endMillis, totalResponses,
        responsesByCountry, responsesByTransport, responsesByVersion);
  }

  private static SortedMap<String, Double> parseResponses(
      String responsesString) {
    SortedMap<String, Double> responses = new TreeMap<>();
    if (responsesString.length() > 0) {
      for (String pair : responsesString.split(",")) {
        String[] keyValue = pair.split("=");
        if (keyValue.length != 2) {
          /* Logged by caller */
          return null;
        }
        double value;
        try {
          value = Double.parseDouble(keyValue[1]);
        } catch (NumberFormatException e) {
          /* Logged by caller */
          return null;
        }
        responses.put(keyValue[0], value);
      }
    }
    return responses;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(DateTimeHelper.format(startMillis));
    sb.append(" ").append(DateTimeHelper.format(endMillis));
    sb.append(" ").append(String.format("%.3f", this.totalResponses));
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
      sb.append(written++ > 0 ? "," : "").append(e.getKey()).append("=")
          .append(String.format("%.3f", e.getValue()));
    }
  }

  /** Adds responses from another clients history object to this one by
   * summing up response numbers and extending interval start and/or
   * end. */
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
      thisResponses.putIfAbsent(e.getKey(), 0.0);
      thisResponses.put(e.getKey(), thisResponses.get(e.getKey())
          + e.getValue());
    }
  }

  @Override
  public int compareTo(ClientsHistory other) {
    return Long.compare(this.startMillis, other.startMillis);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ClientsHistory
        && this.startMillis == ((ClientsHistory) other).startMillis;
  }

  @Override
  public int hashCode() {
    return (int) this.startMillis;
  }
}

