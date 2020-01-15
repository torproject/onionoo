/* Copyright 2013--2020 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

public class LookupResult {

  private String countryCode;

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public String getCountryCode() {
    return this.countryCode;
  }

  private String countryName;

  public void setCountryName(String countryName) {
    this.countryName = countryName;
  }

  public String getCountryName() {
    return this.countryName;
  }

  private String regionName;

  public void setRegionName(String regionName) {
    this.regionName = regionName;
  }

  public String getRegionName() {
    return this.regionName;
  }

  private String cityName;

  public void setCityName(String cityName) {
    this.cityName = cityName;
  }

  public String getCityName() {
    return this.cityName;
  }

  private Float latitude;

  public void setLatitude(Float latitude) {
    this.latitude = latitude;
  }

  public Float getLatitude() {
    return this.latitude;
  }

  private Float longitude;

  public void setLongitude(Float longitude) {
    this.longitude = longitude;
  }

  public Float getLongitude() {
    return this.longitude;
  }

  private String asNumber;

  public void setAsNumber(String asNumber) {
    this.asNumber = asNumber;
  }

  public String getAsNumber() {
    return this.asNumber;
  }

  private String asName;

  public void setAsName(String asName) {
    this.asName = asName;
  }

  public String getAsName() {
    return this.asName;
  }
}

