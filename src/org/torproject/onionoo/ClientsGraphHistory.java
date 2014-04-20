/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

public class ClientsGraphHistory {

  private String first;
  public void setFirst(String first) {
    this.first = first;
  }
  public String getFirst() {
    return this.first;
  }

  private String last;
  public void setLast(String last) {
    this.last = last;
  }
  public String getLast() {
    return this.last;
  }

  private Integer interval;
  public void setInterval(Integer interval) {
    this.interval = interval;
  }
  public Integer getInterval() {
    return this.interval;
  }

  private Double factor;
  public void setFactor(Double factor) {
    this.factor = factor;
  }
  public Double getFactor() {
    return this.factor;
  }

  private Integer count;
  public void setCount(Integer count) {
    this.count = count;
  }
  public Integer getCount() {
    return this.count;
  }

  private List<Integer> values = new ArrayList<Integer>();
  public void setValues(List<Integer> values) {
    this.values = values;
  }
  public List<Integer> getValues() {
    return this.values;
  }

  private SortedMap<String, Float> countries;
  public void setCountries(SortedMap<String, Float> countries) {
    this.countries = countries;
  }
  public SortedMap<String, Float> getCountries() {
    return this.countries;
  }

  private SortedMap<String, Float> transports;
  public void setTransports(SortedMap<String, Float> transports) {
    this.transports = transports;
  }
  public SortedMap<String, Float> getTransports() {
    return this.transports;
  }

  private SortedMap<String, Float> versions;
  public void setVersions(SortedMap<String, Float> versions) {
    this.versions = versions;
  }
  public SortedMap<String, Float> getVersions() {
    return this.versions;
  }
}

