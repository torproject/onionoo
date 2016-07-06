/* Copyright 2013 The Tor Project
 * See LICENSE for licensing information */

package org.torproject.onionoo.updater;

import org.torproject.onionoo.util.FormattingUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
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

  private File geoIPASNum2CsvFile;

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
    this.geoIPASNum2CsvFile = new File(this.geoipDir, "GeoIPASNum2.csv");
    if (!this.geoIPASNum2CsvFile.exists()) {
      log.error("No GeoIPASNum2.csv file in geoip/.");
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
    try (BufferedReader br = this.createBufferedReaderFromUtf8File(
        this.geoLite2CityBlocksIPv4CsvFile)) {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      String line = br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.split(",", -1);
        if (parts.length < 9) {
          log.error("Illegal line '" + line + "' in "
              + this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath()
              + ".");
          return lookupResults;
        }
        try {
          String[] networkAddressAndMask = parts[0].split("/");
          String startAddressString = networkAddressAndMask[0];
          long startIpNum = this.parseAddressString(startAddressString);
          if (startIpNum < 0L) {
            log.error("Illegal IP address in '" + line + "' in "
                + this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath()
                + ".");
            return lookupResults;
          }
          int networkMaskLength = networkAddressAndMask.length < 2 ? 0
              : Integer.parseInt(networkAddressAndMask[1]);
          if (networkMaskLength < 8 || networkMaskLength > 32) {
            log.error("Missing or illegal network mask in '" + line
                + "' in "
                + this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath()
                + ".");
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
          log.error("Number format exception while parsing line '" + line
              + "' in "
              + this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath()
              + ".", e);
          return lookupResults;
        }
      }
    } catch (IOException e) {
      log.error("I/O exception while reading "
          + this.geoLite2CityBlocksIPv4CsvFile.getAbsolutePath()
          + ": " + e);
      return lookupResults;
    }

    /* Obtain a map from relevant blocks to location lines. */
    Map<Long, String> blockLocations = new HashMap<Long, String>();
    try (BufferedReader br = this.createBufferedReaderFromUtf8File(
        this.geoLite2CityLocationsEnCsvFile)) {
      Set<Long> blockNumbers = new HashSet<Long>(
          addressNumberBlocks.values());
      String line = br.readLine();
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 13);
        if (parts.length != 13) {
          log.error("Illegal line '" + line + "' in "
              + this.geoLite2CityLocationsEnCsvFile.getAbsolutePath()
              + ".");
          return lookupResults;
        }

        try {
          long locId = Long.parseLong(parts[0]);
          if (blockNumbers.contains(locId)) {
            blockLocations.put(locId, line);
          }
        } catch (NumberFormatException e) {
          log.error("Number format exception while parsing line "
              + "'" + line + "' in "
              + this.geoLite2CityLocationsEnCsvFile.getAbsolutePath()
              + ".");
          return lookupResults;
        }
      }
    } catch (IOException e) {
      log.error("I/O exception while reading "
          + this.geoLite2CityLocationsEnCsvFile.getAbsolutePath()
          + ": " + e);
      return lookupResults;
    }

    /* Obtain a map from IP address numbers to ASN. */
    Map<Long, String> addressNumberASN = new HashMap<Long, String>();
    try (BufferedReader br = this.createBufferedReaderFromIso88591File(
        this.geoIPASNum2CsvFile)) {
      SortedSet<Long> sortedAddressNumbers = new TreeSet<Long>(
          addressStringNumbers.values());
      long firstAddressNumber = sortedAddressNumbers.first();
      String line;
      long previousStartIpNum = -1L;
      while ((line = br.readLine()) != null) {
        String[] parts = line.replaceAll("\"", "").split(",", 3);
        if (parts.length != 3) {
          log.error("Illegal line '" + line + "' in "
              + geoIPASNum2CsvFile.getAbsolutePath() + ".");
          return lookupResults;
        }
        try {
          long startIpNum = Long.parseLong(parts[0]);
          if (startIpNum <= previousStartIpNum) {
            log.error("Line '" + line + "' not sorted in "
                + geoIPASNum2CsvFile.getAbsolutePath() + ".");
            return lookupResults;
          }
          previousStartIpNum = startIpNum;
          while (firstAddressNumber < startIpNum
              && firstAddressNumber != -1L) {
            sortedAddressNumbers.remove(firstAddressNumber);
            if (sortedAddressNumbers.isEmpty()) {
              firstAddressNumber = -1L;
            } else {
              firstAddressNumber = sortedAddressNumbers.first();
            }
          }
          long endIpNum = Long.parseLong(parts[1]);
          while (firstAddressNumber <= endIpNum
              && firstAddressNumber != -1L) {
            if (parts[2].startsWith("AS")) {
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
        } catch (NumberFormatException e) {
          log.error("Number format exception while parsing line "
              + "'" + line + "' in "
              + geoIPASNum2CsvFile.getAbsolutePath() + ".");
          return lookupResults;
        }
      }
    } catch (IOException e) {
      log.error("I/O exception while reading "
          + geoIPASNum2CsvFile.getAbsolutePath() + ": " + e);
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
          && !addressNumberASN.containsKey(addressNumber)) {
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
      if (addressNumberASN.containsKey(addressNumber)) {
        String[] parts = addressNumberASN.get(addressNumber).split(" ",
            2);
        lookupResult.setAsNumber(parts[0]);
        lookupResult.setAsName(parts.length == 2 ? parts[1] : "");
      }
      lookupResults.put(addressString, lookupResult);
    }

    /* Keep statistics. */
    this.addressesLookedUp += addressStrings.size();
    this.addressesResolved += lookupResults.size();

    return lookupResults;
  }

  private BufferedReader createBufferedReaderFromUtf8File(File utf8File)
      throws FileNotFoundException, CharacterCodingException {
    return this.createBufferedReaderFromFile(utf8File,
        StandardCharsets.UTF_8.newDecoder());
  }

  private BufferedReader createBufferedReaderFromIso88591File(
      File iso88591File) throws FileNotFoundException,
      CharacterCodingException {
    return this.createBufferedReaderFromFile(iso88591File,
        StandardCharsets.ISO_8859_1.newDecoder());
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

  public String getStatsString() {
    StringBuilder sb = new StringBuilder();
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        addressesLookedUp) + " addresses looked up\n");
    sb.append("    " + FormattingUtils.formatDecimalNumber(
        addressesResolved) + " addresses resolved\n");
    return sb.toString();
  }
}

