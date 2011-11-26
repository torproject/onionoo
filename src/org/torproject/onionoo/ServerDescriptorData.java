/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.util.*;

/* Store data of a relay server descriptor. */
public class ServerDescriptorData {
  private String fingerprint;
  public String getFingerprint() {
    return this.fingerprint;
  }
  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }
  private long publishedMillis;
  public long getPublishedMillis() {
    return this.publishedMillis;
  }
  public void setPublishedMillis(long publishedMillis) {
    this.publishedMillis = publishedMillis;
  }
  private String contactLine;
  public String getContactLine() {
    return this.contactLine;
  }
  public void setContactLine(String contactLine) {
    this.contactLine = contactLine;
  }
  private List<String> exitPolicyLines;
  public List<String> getExitPolicyLines() {
    return this.exitPolicyLines;
  }
  public void setExitPolicyLines(List<String> exitPolicyLines) {
    this.exitPolicyLines = exitPolicyLines;
  }
  private String platformLine;
  public String getPlatformLine() {
    return this.platformLine;
  }
  public void setPlatformLine(String platformLine) {
    this.platformLine = platformLine;
  }
  private List<String> family;
  public List<String> getFamily() {
    return this.family;
  }
  public void setFamily(List<String> family) {
    this.family = family;
  }
}

