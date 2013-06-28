/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class LookupService {

  File geoipDir;
  File geoLiteCityBlocksCsvFile;
  File geoLiteCityLocationCsvFile;
  File iso3166CsvFile;
  File regionCsvFile;
  File geoIPASNum2CsvFile;
  private boolean hasAllFiles = false;
  public LookupService(File geoipDir) {
    this.geoipDir = geoipDir;
    this.findRequiredCsvFiles();
  }

  /* Make sure we have all required .csv files. */
  private void findRequiredCsvFiles() {
    File[] geoLiteCityBlocksCsvFiles = new File[] {
        new File(this.geoipDir, "Manual-GeoLiteCity-Blocks.csv"),
        new File(this.geoipDir, "Automatic-GeoLiteCity-Blocks.csv"),
        new File(this.geoipDir, "GeoLiteCity-Blocks.csv") };
    for (File file : geoLiteCityBlocksCsvFiles) {
      if (file.exists()) {
        this.geoLiteCityBlocksCsvFile = file;
        break;
      }
    }
    if (this.geoLiteCityBlocksCsvFile == null) {
      System.err.println("No *GeoLiteCity-Blocks.csv file in geoip/.");
      return;
    }
    this.geoLiteCityLocationCsvFile = new File(this.geoipDir,
        "GeoLiteCity-Location.csv");
    if (!this.geoLiteCityLocationCsvFile.exists()) {
      System.err.println("No GeoLiteCity-Location.csv file in geoip/.");
      return;
    }
    this.iso3166CsvFile = new File(this.geoipDir, "iso3166.csv");
    if (!this.iso3166CsvFile.exists()) {
      System.err.println("No iso3166.csv file in geoip/.");
      return;
    }
    this.regionCsvFile = new File(this.geoipDir, "region.csv");
    if (!this.regionCsvFile.exists()) {
      System.err.println("No region.csv file in geoip/.");
      return;
    }
    this.geoIPASNum2CsvFile = new File(this.geoipDir, "GeoIPASNum2.csv");
    if (!this.geoIPASNum2CsvFile.exists()) {
      System.err.println("No GeoIPASNum2.csv file in geoip/.");
      return;
    }
    this.hasAllFiles = true;
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
    Pattern ipv4Pattern = Pattern.compile("^[0-9\\.]{7,15}$");
    for (String addressString : addressStrings) {
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
      if (addressNumber >= 0L) {
        addressStringNumbers.put(addressString, addressNumber);
      }
    }
    if (addressStringNumbers.isEmpty()) {
      return lookupResults;
    }

    /* Obtain a map from IP address numbers to blocks. */
    Map<Long, Long> addressNumberBlocks = new HashMap<Long, Long>();
    try {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      BufferedReader br = new BufferedReader(new FileReader(
          geoLiteCityBlocksCsvFile));
      String line;
      long previousStartIpNum = -1L;
      while ((line = br.readLine()) != null) {
        if (!line.startsWith("\"")) {
          continue;
        }
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          System.err.println("Illegal line '" + line + "' in "
              + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        try {
          long startIpNum = Long.parseLong(parts[0]);
          if (startIpNum <= previousStartIpNum) {
            System.err.println("Line '" + line + "' not sorted in "
                + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
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
            long blockNumber = Long.parseLong(parts[2]);
            addressNumberBlocks.put(firstAddressNumber, blockNumber);
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
              + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoLiteCityBlocksCsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Obtain a map from relevant blocks to location lines. */
    Map<Long, String> blockLocations = new HashMap<Long, String>();
    try {
      Set<Long> blockNumbers = new HashSet<Long>(
          addressNumberBlocks.values());
      BufferedReader br = new BufferedReader(new FileReader(
          geoLiteCityLocationCsvFile));
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("C") || line.startsWith("l")) {
          continue;
        }
        String[] parts = line.replaceAll("\"", "").split(",", 9);
        if (parts.length != 9) {
          System.err.println("Illegal line '" + line + "' in "
              + geoLiteCityLocationCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        try {
          long locId = Long.parseLong(parts[0]);
          if (blockNumbers.contains(locId)) {
            blockLocations.put(locId, line);
          }
        }
        catch (NumberFormatException e) {
          System.err.println("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoLiteCityLocationCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + geoLiteCityLocationCsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Read country names to memory. */
    Map<String, String> countryNames = new HashMap<String, String>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(
          iso3166CsvFile));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 2);
        if (parts.length != 2) {
          System.err.println("Illegal line '" + line + "' in "
              + iso3166CsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        countryNames.put(parts[0].toLowerCase(), parts[1]);
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + iso3166CsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Read region names to memory. */
    Map<String, String> regionNames = new HashMap<String, String>();
    try {
      BufferedReader br = new BufferedReader(new FileReader(
          regionCsvFile));
      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          System.err.println("Illegal line '" + line + "' in "
              + regionCsvFile.getAbsolutePath() + ".");
          br.close();
          return lookupResults;
        }
        regionNames.put(parts[0].toLowerCase() + ","
            + parts[1].toLowerCase(), parts[2]);
      }
      br.close();
    } catch (IOException e) {
      System.err.println("I/O exception while reading "
          + regionCsvFile.getAbsolutePath() + ".");
      return lookupResults;
    }

    /* Obtain a map from IP address numbers to ASN. */
    Map<Long, String> addressNumberASN = new HashMap<Long, String>();
    try {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      BufferedReader br = new BufferedReader(new FileReader(
          geoIPASNum2CsvFile));
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
          !addressNumberASN.containsKey(addressNumber)) {
        continue;
      }
      LookupResult lookupResult = new LookupResult();
      if (addressNumberBlocks.containsKey(addressNumber)) {
        long blockNumber = addressNumberBlocks.get(addressNumber);
        if (blockLocations.containsKey(blockNumber)) {
          String[] parts = blockLocations.get(blockNumber).
              replaceAll("\"", "").split(",", -1);
          String countryCode = parts[1].toLowerCase();
          lookupResult.countryCode = countryCode;
          if (countryNames.containsKey(countryCode)) {
            lookupResult.countryName = countryNames.get(countryCode);
          }
          String regionCode = countryCode + "," + parts[2].toLowerCase();
          if (regionNames.containsKey(regionCode)) {
            lookupResult.regionName = regionNames.get(regionCode);
          }
          if (parts[3].length() > 0) {
            lookupResult.cityName = parts[3];
          }
          lookupResult.latitude = parts[5];
          lookupResult.longitude = parts[6];
        }
      }
      if (addressNumberASN.containsKey(addressNumber)) {
        String[] parts = addressNumberASN.get(addressNumber).split(" ",
            2);
        lookupResult.aSNumber = parts[0];
        lookupResult.aSName = parts[1];
      }
      lookupResults.put(addressString, lookupResult);
    }

    /* Keep statistics. */
    this.addressesLookedUp += addressStrings.size();
    this.addressesResolved += lookupResults.size();

    return lookupResults;
  }

  class LookupResult {
    String countryCode;
    String countryName;
    String regionName;
    String cityName;
    String latitude;
    String longitude;
    String aSNumber;
    String aSName;
  }

  int addressesLookedUp = 0, addressesResolved = 0;

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + formatDecimalNumber(addressesLookedUp)
        + " addresses looked up\n");
    sb.append("    " + formatDecimalNumber(addressesResolved)
        + " addresses resolved\n");
    return sb.toString();
  }

  //TODO This method should go into a utility class.
  private static String formatDecimalNumber(long decimalNumber) {
    return String.format("%,d", decimalNumber);
  }
}
