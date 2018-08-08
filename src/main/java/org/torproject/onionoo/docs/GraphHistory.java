/* Copyright 2014--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.docs;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

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
public class GraphHistory {

  private String first;

  public void setFirst(long first) {
    this.first = DateTimeHelper.format(first);
  }

  public long getFirst() {
    return DateTimeHelper.parse(this.first);
  }

  private String last;

  public void setLast(long last) {
    this.last = DateTimeHelper.format(last);
  }

  public long getLast() {
    return DateTimeHelper.parse(this.last);
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

