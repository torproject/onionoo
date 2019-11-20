/* Copyright 2013--2018 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.metrics.onionoo.updater;

import org.torproject.metrics.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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

  private static final Logger log = LoggerFactory.getLogger(
      LookupService.class);

  private File geoipDir;

  private File geoLite2CityBlocksIPv4CsvFile;

  private File geoLite2CityLocationsEnCsvFile;

  private File geoLite2AsnBlocksIpv4CsvFile;

  private boolean hasAllFiles = false;

  public LookupService(File geoipDir) {
    this.geoipDir = geoipDir;
    this.findRequiredCsvFiles();
  }

  /* Make sure we have all required .csv files. */
  private void findRequiredCsvFiles() {
    this.geoLite2CityBlocksIPv4CsvFile = new File(this.geoipDir,
        "GeoLite2-City-Blocks-IPv4.csv");
    if (!this.geoLite2CityBlocksIPv4CsvFile.exists()) {
      log.error("No GeoLite2-City-Blocks-IPv4.csv file in geoip/.");
      return;
    }
    this.geoLite2CityLocationsEnCsvFile = new File(this.geoipDir,
        "GeoLite2-City-Locations-en.csv");
    if (!this.geoLite2CityLocationsEnCsvFile.exists()) {
      log.error("No GeoLite2-City-Locations-en.csv file in "
          + "geoip/.");
      return;
    }
    this.geoLite2AsnBlocksIpv4CsvFile = new File(this.geoipDir,
        "GeoLite2-ASN-Blocks-IPv4.csv");
    if (!this.geoLite2AsnBlocksIpv4CsvFile.exists()) {
      log.error("No GeoLite2-ASN-Blocks-IPv4.csv file in geoip/.");
      return;
    }
    this.hasAllFiles = true;
  }

  private Pattern ipv4Pattern = Pattern.compile("^[0-9.]{7,15}$");

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
            /* Handled below, because octetValue will still be -1. */
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

  /** Looks up address strings in the configured
   * {@code GeoLite2-City-*.csv} and {@code GeoIPASNum2.csv}
   * files and returns all lookup results. */
  public SortedMap<String, LookupResult> lookup(
      SortedSet<String> addressStrings) {

    SortedMap<String, LookupResult> lookupResults = new TreeMap<>();

    if (!this.hasAllFiles) {
      return lookupResults;
    }

    /* Obtain a map from relay IP address strings to numbers. */
    Map<String, Long> addressStringNumbers = new HashMap<>();
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
    Map<Long, Long> addressNumberBlocks = new HashMap<>();
    Map<Long, Float[]> addressNumberLatLong = new HashMap<>();
    try (BufferedReader br = this.createBufferedReaderFromUtf8File(
        this.geoLite2CityBlocksIPv4CsvFile)) {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<>(
          addressStringNumbers.values());
      String line;
      br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(",", -1);
        if (parts.length < 9) {
          log.error("Illegal line '{}' in {}.", line,
              this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath());
          return lookupResults;
        }
        try {
          String[] networkAddressAndMask = parts[0].split("/");
          String startAddressString = networkAddressAndMask[0];
          long startIpNum = this.parseAddressString(startAddressString);
          if (startIpNum < 0L) {
            log.error("Illegal IP address in '{}' in {}.", line,
                this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath());
            return lookupResults;
          }
          int networkMaskLength = networkAddressAndMask.length < 2 ? 0
              : Integer.parseInt(networkAddressAndMask[1]);
          if (networkMaskLength < 8 || networkMaskLength > 32) {
            log.error("Missing or illegal network mask in '{}' in {}.", line,
                this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath());
            return lookupResults;
          }
          if (parts[1].length() == 0 && parts[2].length() == 0) {
            continue;
          }
          long endIpNum = startIpNum + (1 << (32 - networkMaskLength))
              - 1;
          for (long addressNumber : sortedAddressNumbers
              .tailSet(startIpNum).headSet(endIpNum + 1L)) {
            String blockString = parts[1].length() > 0 ? parts[1]
                : parts[2];
            long blockNumber = Long.parseLong(blockString);
            addressNumberBlocks.put(addressNumber, blockNumber);
            if (parts[7].length() > 0 && parts[8].length() > 0) {
              addressNumberLatLong.put(addressNumber,
                  new Float[] { Float.parseFloat(parts[7]),
                  Float.parseFloat(parts[8]) });
            }
          }
        } catch (NumberFormatException e) {
          log.error("Number format exception while parsing line '{}' in {}.",
              line, this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath(), e);
          return lookupResults;
        }
      }
    } catch (IOException e) {
      log.error("I/O exception while reading {}: {}",
          this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath(), e);
      return lookupResults;
    }

    /* Obtain a map from relevant blocks to location lines. */
    Map<Long, String> blockLocations = new HashMap<>();
    try (BufferedReader br = this.createBufferedReaderFromUtf8File(
        this.geoLite2CityLocationsEnCsvFile)) {
      Set<Long> blockNumbers = new HashSet<>(addressNumberBlocks.values());
      String line;
      br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 13);
        if (parts.length != 13) {
          log.error("Illegal line '{}' in {}.", line,
              this.geoLite2CityLocationsEnCsvFile.getAbsolutePath());
          return lookupResults;
        }

        try {
          long locId = Long.parseLong(parts[0]);
          if (blockNumbers.contains(locId)) {
            blockLocations.put(locId, line);
          }
        } catch (NumberFormatException e) {
          log.error("Number format exception while parsing line '{}' in {}.",
              line, this.geoLite2CityLocationsEnCsvFile.getAbsolutePath());
          return lookupResults;
        }
      }
    } catch (IOException e) {
      log.error("I/O exception while reading {}: {}",
          this.geoLite2CityLocationsEnCsvFile.getAbsolutePath(), e);
      return lookupResults;
    }

    /* Obtain a map from IP address numbers to ASN. */
    Map<Long, String[]> addressNumberAsn = new HashMap<>();
    try (BufferedReader br = this.createBufferedReaderFromUtf8File(
        this.geoLite2AsnBlocksIpv4CsvFile)) {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      String line;
      br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          log.error("Illegal line '{}' in {}.", line,
              this.geoLite2AsnBlocksIpv4CsvFile.getAbsolutePath());
          return lookupResults;
        }
        try {
          String[] networkAddressAndMask = parts[0].split("/");
          String startAddressString = networkAddressAndMask[0];
          long startIpNum = this.parseAddressString(startAddressString);
          if (startIpNum < 0L) {
            log.error("Illegal IP address in '{}' in {}.", line,
                this.geoLite2AsnBlocksIpv4CsvFile.getAbsolutePath());
            return lookupResults;
          }
          int networkMaskLength = networkAddressAndMask.length < 2 ? 0
              : Integer.parseInt(networkAddressAndMask[1]);
          if (networkMaskLength < 8 || networkMaskLength > 32) {
            log.error("Missing or illegal network mask in '{}' in {}.", line,
                this.geoLite2AsnBlocksIpv4CsvFile.getAbsolutePath());
            return lookupResults;
          }
          String asNumber = "AS" + Integer.parseInt(parts[1]);
          String asName = parts[2];
          while (firstAddressNumber < startIpNum
              && firstAddressNumber != -1L) {
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          long endIpNum = startIpNum + (1 << (32 - networkMaskLength)) - 1;
          while (firstAddressNumber <= endIpNum
              && firstAddressNumber != -1L) {
            addressNumberAsn.put(firstAddressNumber,
                new String[] { asNumber, asName });
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
        } catch (NumberFormatException e) {
          log.error("Number format exception while parsing line '{}' in {}.",
              line, this.geoLite2AsnBlocksIpv4CsvFile.getAbsolutePath());
          return lookupResults;
        }
      }
    } catch (IOException e) {
      log.error("I/O exception while reading {}: {}",
          this.geoLite2AsnBlocksIpv4CsvFile.getAbsolutePath(), e);
      return lookupResults;
    }

    /* Finally, put together lookup results. */
    for (String addressString : addressStrings) {
      if (!addressStringNumbers.containsKey(addressString)) {
        continue;
      }
      long addressNumber = addressStringNumbers.get(addressString);
      if (!addressNumberBlocks.containsKey(addressNumber)
          && !addressNumberLatLong.containsKey(addressNumber)
          && !addressNumberAsn.containsKey(addressNumber)) {
        continue;
      }
      LookupResult lookupResult = new LookupResult();
      if (addressNumberBlocks.containsKey(addressNumber)) {
        long blockNumber = addressNumberBlocks.get(addressNumber);
        if (blockLocations.containsKey(blockNumber)) {
          String[] parts = blockLocations.get(blockNumber)
              .replaceAll("\"", "").split(",", -1);
          if (parts[4].length() > 0) {
            lookupResult.setCountryCode(parts[4].toLowerCase());
          }
          if (parts[5].length() > 0) {
            lookupResult.setCountryName(parts[5]);
          }
          if (parts[7].length() > 0) {
            lookupResult.setRegionName(parts[7]);
          }
          if (parts[10].length() > 0) {
            lookupResult.setCityName(parts[10]);
          }
        }
      }
      if (addressNumberLatLong.containsKey(addressNumber)) {
        Float[] latLong = addressNumberLatLong.get(addressNumber);
        lookupResult.setLatitude(latLong[0]);
        lookupResult.setLongitude(latLong[1]);
      }
      if (addressNumberAsn.containsKey(addressNumber)) {
        String[] parts = addressNumberAsn.get(addressNumber);
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

  private BufferedReader createBufferedReaderFromUtf8File(File utf8File)
      throws FileNotFoundException {
    return this.createBufferedReaderFromFile(utf8File,
        StandardCharsets.UTF_8.newDecoder());
  }

  private BufferedReader createBufferedReaderFromFile(File file,
      CharsetDecoder dec) throws FileNotFoundException {
    dec.onMalformedInput(CodingErrorAction.REPORT);
    dec.onUnmappableCharacter(CodingErrorAction.REPORT);
    return new BufferedReader(new InputStreamReader(
        new FileInputStream(file), dec));
  }

  private int addressesLookedUp = 0;

  private int addressesResolved = 0;

  /** Returns a string with the number of addresses looked up and
   * resolved. */
  public String getStatsString() {
    return String.format(
        "    %s addresses looked up\n"
        + "    %s addresses resolved\n",
        FormattingUtils.formatDecimalNumber(addressesLookedUp),
        FormattingUtils.formatDecimalNumber(addressesResolved));
  }
}

