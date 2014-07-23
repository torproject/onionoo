/* Copyright 2014 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo.docs;

import java.util.List;

public class GraphHistory {

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

  private List<Integer> values;
  public void setValues(List<Integer> values) {
    this.values = values;
  }
  public List<Integer> getValues() {
    return this.values;
  }
}
