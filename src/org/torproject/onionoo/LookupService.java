/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

class LookupResult {

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

public class LookupService {

  private File geoipDir;
  private File geoLite2CityBlocksCsvFile;
  private File geoLite2CityLocationsCsvFile;
  private File geoIPASNum2CsvFile;
  private boolean hasAllFiles = false;
  public LookupService(File geoipDir) {
    this.geoipDir = geoipDir;
    this.findRequiredCsvFiles();
  }

  /* Make sure we have all required .csv files. */
  private void findRequiredCsvFiles() {
    this.geoLite2CityBlocksCsvFile = new File(this.geoipDir,
        "GeoLite2-City-Blocks.csv");
    if (!this.geoLite2CityBlocksCsvFile.exists()) {
      System.err.println("No GeoLite2-City-Blocks.csv file in geoip/.");
      return;
    }
    this.geoLite2CityLocationsCsvFile = new File(this.geoipDir,
        "GeoLite2-City-Locations.csv");
    if (!this.geoLite2CityLocationsCsvFile.exists()) {
      System.err.println("No GeoLite2-City-Locations.csv file in "
          + "geoip/.");
      return;
    }
    this.geoIPASNum2CsvFile = new File(this.geoipDir, "GeoIPASNum2.csv");
    if (!this.geoIPASNum2CsvFile.exists()) {
      System.err.println("No GeoIPASNum2.csv file in geoip/.");
      return;
    }
    this.hasAllFiles = true;
  }

  private Pattern ipv4Pattern = Pattern.compile("^[0-9\\.]{7,15}$");
  private long parseAddressString(String addressString) {
    long addressNumber = -1L;
    if (ipv4Pattern.matcher(addressString).matches()) {
      String[] parts = addressString.split("\\.", 4);
      if (parts.length == 4) {
        addressNumber = 0L;
        for (int i = 0; i < 4; i++) {
          addressNumber *= 256L;
          int octetValue = -1;
          try {
            octetValue = Integer.parseInt(parts[i]);
          } catch (NumberFormatException e) {
          }
          if (octetValue < 0 || octetValue > 255) {
            addressNumber = -1L;
            break;
          }
          addressNumber += octetValue;
        }
      }
    }
    return addressNumber;
  }

  public SortedMap<String, LookupResult> lookup(
      SortedSet<String> addressStrings) {

    SortedMap<String, LookupResult> lookupResults =
        new TreeMap<String, LookupResult>();

    if (!this.hasAllFiles) {
      return lookupResults;
    }

    /* Obtain a map from relay IP address strings to numbers. */
    Map<String, Long> addressStringNumbers = new HashMap<String, Long>();
    for (String addressString : addressStrings) {
      long addressNumber = this.parseAddressString(addressString);
      if (addressNumber >= 0L) {
        addressStringNumbers.put(addressString, addressNumber);
      }
    }
    if (addressStringNumbers.isEmpty()) {
      return lookupResults;
    }

    /* Obtain a map from IP address numbers to blocks and to latitudes and
       longitudes. */
    Map<Long, Long> addressNumberBlocks = new HashMap<Long, Long>();
    Map<Long, Float[]> addressNumberLatLong =
        new HashMap<Long, Float[]>();
    try {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      BufferedReader br = new BufferedReader(new InputStreamReader(
          new FileInputStream(geoLite2CityBlocksCsvFile), "ISO-8859-1"));
      String line = br.readLine();
      while ((line = br.readLine()) != null) {
        if (!line.startsWith("::ffff:")) {
          /* TODO Make this less hacky and IPv6-ready at some point. */
          continue;
        }
        String[] parts = line.replaceAll("\"", "").split(",", 10);
        if (parts.length != 10) {
          System.err.println("Illegal line '" + line + "' in "
              + geoLite2CityBlocksCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        try {
          String startAddressString = parts[0].substring(7); /* ::ffff: */
          long startIpNum = this.parseAddressString(startAddressString);
          if (startIpNum < 0L) {
            System.err.println("Illegal IP address in '" + line
                + "' in " + geoLite2CityBlocksCsvFile.getAbsolutePath()
                + ".");
            br.close();
            return lookupResults;
          }
          int networkMaskLength = Integer.parseInt(parts[1]);
          if (networkMaskLength < 96 || networkMaskLength > 128) {
            System.err.println("Illegal network mask in '" + line
                + "' in " + geoLite2CityBlocksCsvFile.getAbsolutePath()
                + ".");
            br.close();
            return lookupResults;
          }
          if (parts[2].length() == 0 && parts[3].length() == 0) {
            continue;
          }
          long endIpNum = startIpNum + (1 << (128 - networkMaskLength))
              - 1;
          for (long addressNumber : sortedAddressNumbers.
              tailSet(startIpNum).headSet(endIpNum + 1L)) {
            String blockString = parts[2].length() > 0 ? parts[2] :
                parts[3];
            long blockNumber = Long.parseLong(blockString);
            addressNumberBlocks.put(addressNumber, blockNumber);
            if (parts[6].length() > 0 && parts[7].length() > 0) {
              addressNumberLatLong.put(addressNumber,
                  new Float[] { Float.parseFloat(parts[6]),
                  Float.parseFloat(parts[7]) });
            }
          }
        } catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoLite2CityBlocksCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoLite2CityBlocksCsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Obtain a map from relevant blocks to location lines. */
    Map<Long, String> blockLocations = new HashMap<Long, String>();
    try {
      Set<Long> blockNumbers = new HashSet<Long>(
          addressNumberBlocks.values());
      BufferedReader br = new BufferedReader(new InputStreamReader(
          new FileInputStream(geoLite2CityLocationsCsvFile),
          "ISO-8859-1"));
      String line = br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 10);
        if (parts.length != 10) {
          System.err.println("Illegal line '" + line + "' in "
              + geoLite2CityLocationsCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        try {
          long locId = Long.parseLong(parts[0]);
          if (blockNumbers.contains(locId)) {
            blockLocations.put(locId, line);
          }
        } catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoLite2CityLocationsCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoLite2CityLocationsCsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Obtain a map from IP address numbers to ASN. */
    Map<Long, String> addressNumberASN = new HashMap<Long, String>();
    try {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      BufferedReader br = new BufferedReader(new InputStreamReader(
          new FileInputStream(geoIPASNum2CsvFile), "ISO-8859-1"));
      String line;
      long previousStartIpNum = -1L;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          System.err.println("Illegal line '" + line + "' in "
              + geoIPASNum2CsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        try {
          long startIpNum = Long.parseLong(parts[0]);
          if (startIpNum <= previousStartIpNum) {
            System.err.println("Line '" + line + "' not sorted in "
                + geoIPASNum2CsvFile.getAbsolutePath() + ".");
            br.close();
            return lookupResults;
          }
          previousStartIpNum = startIpNum;
          while (firstAddressNumber < startIpNum &&
              firstAddressNumber != -1L) {
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          long endIpNum = Long.parseLong(parts[1]);
          while (firstAddressNumber <= endIpNum &&
              firstAddressNumber != -1L) {
            if (parts[2].startsWith("AS") &&
                parts[2].split(" ", 2).length == 2) {
              addressNumberASN.put(firstAddressNumber, parts[2]);
            }
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          if (firstAddressNumber == -1L) {
            break;
          }
        }
        catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoIPASNum2CsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoIPASNum2CsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Finally, put together lookup results. */
    for (String addressString : addressStrings) {
      if (!addressStringNumbers.containsKey(addressString)) {
        continue;
      }
      long addressNumber = addressStringNumbers.get(addressString);
      if (!addressNumberBlocks.containsKey(addressNumber) &&
          !addressNumberLatLong.containsKey(addressNumber) &&
          !addressNumberASN.containsKey(addressNumber)) {
        continue;
      }
      LookupResult lookupResult = new LookupResult();
      if (addressNumberBlocks.containsKey(addressNumber)) {
        long blockNumber = addressNumberBlocks.get(addressNumber);
        if (blockLocations.containsKey(blockNumber)) {
          String[] parts = blockLocations.get(blockNumber).
              replaceAll("\"", "").split(",", -1);
          lookupResult.setCountryCode(parts[3].toLowerCase());
          if (parts[4].length() > 0) {
            lookupResult.setCountryName(parts[4]);
          }
          if (parts[6].length() > 0) {
            lookupResult.setRegionName(parts[6]);
          }
          if (parts[7].length() > 0) {
            lookupResult.setCityName(parts[7]);
          }
        }
      }
      if (addressNumberLatLong.containsKey(addressNumber)) {
        Float[] latLong = addressNumberLatLong.get(addressNumber);
        lookupResult.setLatitude(latLong[0]);
        lookupResult.setLongitude(latLong[1]);
      }
      if (addressNumberASN.containsKey(addressNumber)) {
        String[] parts = addressNumberASN.get(addressNumber).split(" ",
            2);
        lookupResult.setAsNumber(parts[0]);
        lookupResult.setAsName(parts[1]);
      }
      lookupResults.put(addressString, lookupResult);
    }

    /* Keep statistics. */
    this.addressesLookedUp += addressStrings.size();
    this.addressesResolved += lookupResults.size();

    return lookupResults;
  }

  private int addressesLookedUp = 0, addressesResolved = 0;

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + Logger.formatDecimalNumber(addressesLookedUp)
        + " addresses looked up\n");
    sb.append("    " + Logger.formatDecimalNumber(addressesResolved)
        + " addresses resolved\n");
    return sb.toString();
  }
}
