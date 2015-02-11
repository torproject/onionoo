/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.torproject.onionoo.updater.LookupResult;
import org.torproject.onionoo.updater.LookupService;

public class LookupServiceTest {

  private List<String> geoLite2CityBlocksIPv4Lines,
      geoLite2CityLocationsEnLines, geoipASNum2Lines;

  private LookupService lookupService;

  private SortedSet<String> addressStrings = new TreeSet<String>();

  private SortedMap<String, LookupResult> lookupResults;

  private void populateLines() {
    this.geoLite2CityBlocksIPv4Lines = new ArrayList<String>();
    this.geoLite2CityBlocksIPv4Lines.add("network,geoname_id,"
        + "registered_country_geoname_id,represented_country_geoname_id,"
        + "is_anonymous_proxy,is_satellite_provider,postal_code,latitude,"
        + "longitude");
    this.geoLite2CityBlocksIPv4Lines.add("8.8.0.0/21,6252001,6252001,,0,"
        + "0,,38.0000,-97.0000");
    this.geoLite2CityBlocksIPv4Lines.add("8.8.8.0/24,5375480,6252001,,0,"
        + "0,94035,37.3860,-122.0838");
    this.geoLite2CityBlocksIPv4Lines.add("8.8.9.0/24,6252001,6252001,,0,"
        + "0,,38.0000,-97.0000");
    this.geoLite2CityLocationsEnLines = new ArrayList<String>();
    this.geoLite2CityLocationsEnLines.add("geoname_id,locale_code,"
        + "continent_code,continent_name,country_iso_code,country_name,"
        + "subdivision_1_iso_code,subdivision_1_name,"
        + "subdivision_2_iso_code,subdivision_2_name,city_name,"
        + "metro_code,time_zone");
    this.geoLite2CityLocationsEnLines.add("6252001,en,NA,"
        + "\"North America\",US,\"United States\",,,,,,,");
    this.geoLite2CityLocationsEnLines.add("5375480,en,NA,"
        + "\"North America\",US,\"United States\",CA,California,,,"
        + "\"Mountain View\",807,America/Los_Angeles");
    this.geoipASNum2Lines = new ArrayList<String>();
    this.geoipASNum2Lines.add("134743296,134744063,\"AS3356 Level 3 "
        + "Communications\"");
    this.geoipASNum2Lines.add("134744064,134744319,\"AS15169 Google "
        + "Inc.\"");
    this.geoipASNum2Lines.add("134744320,134750463,\"AS3356 Level 3 "
        + "Communications\"");
  }

  private void writeCsvFiles() {
    try {
      this.writeCsvFile(this.geoLite2CityBlocksIPv4Lines,
          "GeoLite2-City-Blocks-IPv4.csv");
      this.writeCsvFile(this.geoLite2CityLocationsEnLines,
          "GeoLite2-City-Locations-en.csv");
      this.writeCsvFile(this.geoipASNum2Lines, "GeoIPASNum2.csv");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeCsvFile(List<String> lines, String fileName)
      throws IOException {
    if (lines != null && !lines.isEmpty()) {
      BufferedWriter bw = new BufferedWriter(new FileWriter(
          new File(this.tempGeoipDir, fileName)));
      for (String line : lines) {
        bw.write(line + "\n");
      }
      bw.close();
    }
  }

  private void performLookups() {
    this.lookupService = new LookupService(this.tempGeoipDir);
    this.lookupResults = this.lookupService.lookup(this.addressStrings);
  }

  private void assertLookupResult(List<String> geoLite2CityBlocksLines,
      List<String> geoLite2CityLocationsLines,
      List<String> geoipASNum2Lines, String addressString,
      String countryCode, String countryName, String regionName,
      String cityName, Float latitude, Float longitude, String aSNumber,
      String aSName) {
    this.addressStrings.add(addressString);
    this.populateLines();
    if (geoLite2CityBlocksLines != null) {
      this.geoLite2CityBlocksIPv4Lines = geoLite2CityBlocksLines;
    }
    if (geoLite2CityLocationsLines != null) {
      this.geoLite2CityLocationsEnLines = geoLite2CityLocationsLines;
    }
    if (geoipASNum2Lines != null) {
      this.geoipASNum2Lines = geoipASNum2Lines;
    }
    this.writeCsvFiles();
    /* Disable log messages printed to System.err. */
    System.setErr(new PrintStream(new OutputStream() {
      public void write(int b) {
      }
    }));
    this.performLookups();
    if (countryCode == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getCountryCode() == null);
    } else {
      assertEquals(countryCode,
          this.lookupResults.get(addressString).getCountryCode());
    }
    if (countryName == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getCountryName() == null);
    } else {
      assertEquals(countryName,
          this.lookupResults.get(addressString).getCountryName());
    }
    if (regionName == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getRegionName() == null);
    } else {
      assertEquals(regionName,
          this.lookupResults.get(addressString).getRegionName());
    }
    if (cityName == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getCityName() == null);
    } else {
      assertEquals(cityName,
          this.lookupResults.get(addressString).getCityName());
    }
    if (latitude == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getLatitude() == null);
    } else {
      assertEquals(latitude,
          this.lookupResults.get(addressString).getLatitude(), 0.01);
    }
    if (longitude == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getLongitude() == null);
    } else {
      assertEquals(longitude,
          this.lookupResults.get(addressString).getLongitude(), 0.01);
    }
    if (aSNumber == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getAsNumber() == null);
    } else {
      assertEquals(aSNumber,
          this.lookupResults.get(addressString).getAsNumber());
    }
    if (aSName == null) {
      assertTrue(!this.lookupResults.containsKey(addressString) ||
          this.lookupResults.get(addressString).getAsName() == null);
    } else {
      assertEquals(aSName,
          this.lookupResults.get(addressString).getAsName());
    }
  }

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private File tempGeoipDir;

  @Before
  public void createTempGeoipDir() throws IOException {
    this.tempGeoipDir = this.tempFolder.newFolder("geoip");
  }

  @Test()
  public void testLookup8888() {
    this.assertLookupResult(null, null, null, "8.8.8.8", "us",
        "United States", "California", "Mountain View", 37.3860f,
        -122.0838f, "AS15169", "Google Inc.");
  }

  @Test()
  public void testLookup8880() {
    this.assertLookupResult(null, null, null, "8.8.8.0", "us",
        "United States", "California", "Mountain View", 37.3860f,
        -122.0838f, "AS15169", "Google Inc.");
  }

  @Test()
  public void testLookup888255() {
    this.assertLookupResult(null, null, null, "8.8.8.255", "us",
        "United States", "California", "Mountain View", 37.3860f,
        -122.0838f, "AS15169", "Google Inc.");
  }

  @Test()
  public void testLookup888256() {
    this.assertLookupResult(null, null, null, "8.8.8.256", null, null,
        null, null, null, null, null, null);
  }

  @Test()
  public void testLookup888Minus1() {
    this.assertLookupResult(null, null, null, "8.8.8.-1", null, null,
        null, null, null, null, null, null);
  }

  @Test()
  public void testLookup000() {
    this.assertLookupResult(null, null, null, "0.0.0.0", null, null, null,
        null, null, null, null, null);
  }

  @Test()
  public void testLookupNoBlocksLines() {
    this.assertLookupResult(new ArrayList<String>(), null, null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupNoLocationLines() {
    this.assertLookupResult(null, new ArrayList<String>(), null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupNoGeoipASNum2Lines() {
    this.assertLookupResult(null, null, new ArrayList<String>(),
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupNoCorrespondingLocation() {
    List<String> geoLite2CityLocationsEnLines = new ArrayList<String>();
    geoLite2CityLocationsEnLines.add("geoname_id,locale_code,"
        + "continent_code,continent_name,country_iso_code,country_name,"
        + "subdivision_1_iso_code,subdivision_1_name,"
        + "subdivision_2_iso_code,subdivision_2_name,city_name,"
        + "metro_code,time_zone");
    geoLite2CityLocationsEnLines.add("6252001,en,NA,"
        + "\"North America\",US,\"United States\",,,,,,,");
    this.assertLookupResult(null, geoLite2CityLocationsEnLines, null,
        "8.8.8.8", null, null, null, null, 37.3860f, -122.0838f,
        "AS15169", "Google Inc.");
  }

  @Test()
  public void testLookupBlocksStartNotANumber() {
    List<String> geoLite2CityBlocksIPv4Lines = new ArrayList<String>();
    geoLite2CityBlocksIPv4Lines.add("network,geoname_id,"
        + "registered_country_geoname_id,represented_country_geoname_id,"
        + "is_anonymous_proxy,is_satellite_provider,postal_code,latitude,"
        + "longitude");
    geoLite2CityBlocksIPv4Lines.add("one/24,5375480,6252001,,0,"
        + "0,94035,37.3860,-122.0838");
    this.assertLookupResult(
        geoLite2CityBlocksIPv4Lines, null, null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupBlocksLocationX() {
    List<String> geoLite2CityBlocksIPv4Lines = new ArrayList<String>();
    geoLite2CityBlocksIPv4Lines.add("network,geoname_id,"
        + "registered_country_geoname_id,represented_country_geoname_id,"
        + "is_anonymous_proxy,is_satellite_provider,postal_code,latitude,"
        + "longitude");
    geoLite2CityBlocksIPv4Lines.add("8.8.8.0/24,X,X,,0,0,94035,37.3860,"
        + "-122.0838");
    this.assertLookupResult(geoLite2CityBlocksIPv4Lines, null, null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupBlocksLocationEmpty() {
    List<String> geoLite2CityBlocksIPv4Lines = new ArrayList<String>();
    geoLite2CityBlocksIPv4Lines.add("network,geoname_id,"
        + "registered_country_geoname_id,represented_country_geoname_id,"
        + "is_anonymous_proxy,is_satellite_provider,postal_code,latitude,"
        + "longitude");
    geoLite2CityBlocksIPv4Lines.add("8.8.8.0/24,,,,0,0,,,");
    this.assertLookupResult(geoLite2CityBlocksIPv4Lines, null, null,
        "8.8.8.8", null, null, null, null, null, null, "AS15169",
        "Google Inc.");
  }

  @Test()
  public void testLookupBlocksTooFewFields() {
    List<String> geoLite2CityBlocksIPv4Lines = new ArrayList<String>();
    geoLite2CityBlocksIPv4Lines.add("network,geoname_id,"
        + "registered_country_geoname_id,represented_country_geoname_id,"
        + "is_anonymous_proxy,is_satellite_provider,postal_code,latitude,"
        + "longitude");
    geoLite2CityBlocksIPv4Lines.add("8.8.8.0/24,5375480,6252001,,0,"
        + "0,94035,37.3860");
    this.assertLookupResult(geoLite2CityBlocksIPv4Lines, null, null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupLocationLocIdNotANumber() {
    List<String> geoLite2CityLocationsEnLines = new ArrayList<String>();
    geoLite2CityLocationsEnLines.add("geoname_id,locale_code,"
        + "continent_code,continent_name,country_iso_code,country_name,"
        + "subdivision_1_iso_code,subdivision_1_name,"
        + "subdivision_2_iso_code,subdivision_2_name,city_name,"
        + "metro_code,time_zone");
    geoLite2CityLocationsEnLines.add("threetwoonenineone,en,NA,"
        + "\"North America\",US,\"United States\",CA,California,,,"
        + "\"Mountain View\",807,America/Los_Angeles");
    this.assertLookupResult(null, geoLite2CityLocationsEnLines, null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupLocationTooFewFields() {
    List<String> geoLite2CityLocationsEnLines = new ArrayList<String>();
    geoLite2CityLocationsEnLines.add("geoname_id,locale_code,"
        + "continent_code,continent_name,country_iso_code,country_name,"
        + "subdivision_1_iso_code,subdivision_1_name,"
        + "subdivision_2_iso_code,subdivision_2_name,city_name,"
        + "metro_code,time_zone");
    geoLite2CityLocationsEnLines.add("threetwoonenineone,en,NA,"
        + "\"North America\",US,\"United States\",CA,California,,,"
        + "\"Mountain View\",807");
    this.assertLookupResult(null, geoLite2CityLocationsEnLines, null,
        "8.8.8.8", null, null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupGeoipASNum2EndBeforeStart() {
    List<String> geoipASNum2Lines = new ArrayList<String>();
    geoipASNum2Lines.add("134743296,134744063,\"AS3356 Level 3 "
        + "Communications\"");
    geoipASNum2Lines.add("134744319,134744064,\"AS15169 Google Inc.\"");
    geoipASNum2Lines.add("134744320,134750463,\"AS3356 Level 3 "
        + "Communications\"");
    this.assertLookupResult(null, null, geoipASNum2Lines, "8.8.8.8", "us",
        "United States", "California", "Mountain View", 37.3860f,
        -122.0838f, null, null);
  }

  @Test()
  public void testLookupGeoipASNum2StartNotANumber() {
    List<String> geoipASNum2Lines = new ArrayList<String>();
    geoipASNum2Lines.add("one,134744319,\"AS15169 Google Inc.\"");
    this.assertLookupResult(null, null, geoipASNum2Lines, "8.8.8.8", null,
        null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupGeoipASNum2StartTooLarge() {
    List<String> geoipASNum2Lines = new ArrayList<String>();
    geoipASNum2Lines.add("1" + String.valueOf(Long.MAX_VALUE)
        + ",134744319,\"AS15169 Google Inc.\"");
    this.assertLookupResult(null, null, geoipASNum2Lines, "8.8.8.8", null,
        null, null, null, null, null, null, null);
  }

  @Test()
  public void testLookupGeoipASNum2TooFewFields() {
    List<String> geoipASNum2Lines = new ArrayList<String>();
    geoipASNum2Lines.add("134744064,134744319");
    this.assertLookupResult(null, null, geoipASNum2Lines, "8.8.8.8", null,
        null, null, null, null, null, null, null);
  }
}

