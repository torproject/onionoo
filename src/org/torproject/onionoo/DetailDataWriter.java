/* Copyright 2011, 2012 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;

import org.torproject.descriptor.BridgePoolAssignment;
import org.torproject.descriptor.Descriptor;
import org.torproject.descriptor.DescriptorFile;
import org.torproject.descriptor.DescriptorReader;
import org.torproject.descriptor.DescriptorSourceFactory;
import org.torproject.descriptor.ServerDescriptor;

/* Write updated detail data files to disk and delete files of relays or
 * bridges that fell out of the summary list.
 *
 * The parts of details files coming from server descriptors always come
 * from the last known descriptor of a relay or bridge, not from the
 * descriptor that was last referenced in a network status. */
public class DetailDataWriter {

  private SortedMap<String, Node> relays;
  public void setCurrentRelays(SortedMap<String, Node> currentRelays) {
    this.relays = currentRelays;
  }

  private SortedMap<String, Node> bridges;
  public void setCurrentBridges(SortedMap<String, Node> currentBridges) {
    this.bridges = currentBridges;
  }

  private Map<String, ServerDescriptor> relayServerDescriptors =
      new HashMap<String, ServerDescriptor>();
  public void readRelayServerDescriptors() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File(
        "in/relay-descriptors/server-descriptors"));
    reader.setExcludeFiles(new File("status/relay-serverdesc-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ServerDescriptor) {
            ServerDescriptor serverDescriptor =
                (ServerDescriptor) descriptor;
            String fingerprint = serverDescriptor.getFingerprint();
            if (!this.relayServerDescriptors.containsKey(fingerprint) ||
                this.relayServerDescriptors.get(fingerprint).
                getPublishedMillis()
                < serverDescriptor.getPublishedMillis()) {
              this.relayServerDescriptors.put(fingerprint,
                  serverDescriptor);
            }
          }
        }
      }
    }
  }

  private Map<String, ServerDescriptor> bridgeServerDescriptors =
      new HashMap<String, ServerDescriptor>();
  public void readBridgeServerDescriptors() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File(
        "in/bridge-descriptors/server-descriptors"));
    reader.setExcludeFiles(new File("status/bridge-serverdesc-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof ServerDescriptor) {
            ServerDescriptor serverDescriptor =
                (ServerDescriptor) descriptor;
            String fingerprint = serverDescriptor.getFingerprint();
            if (!this.bridgeServerDescriptors.containsKey(fingerprint) ||
                this.bridgeServerDescriptors.get(fingerprint).
                getPublishedMillis()
                < serverDescriptor.getPublishedMillis()) {
              this.bridgeServerDescriptors.put(fingerprint,
                  serverDescriptor);
            }
          }
        }
      }
    }
  }

  private Map<String, String> bridgePoolAssignments =
      new HashMap<String, String>();
  public void readBridgePoolAssignments() {
    DescriptorReader reader =
        DescriptorSourceFactory.createDescriptorReader();
    reader.addDirectory(new File("in/bridge-pool-assignments"));
    reader.setExcludeFiles(new File("status/bridge-poolassign-history"));
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      if (descriptorFile.getDescriptors() != null) {
        for (Descriptor descriptor : descriptorFile.getDescriptors()) {
          if (descriptor instanceof BridgePoolAssignment) {
            BridgePoolAssignment bridgePoolAssignment =
                (BridgePoolAssignment) descriptor;
            for (Map.Entry<String, String> e :
                bridgePoolAssignment.getEntries().entrySet()) {
              String fingerprint = e.getKey();
              String details = e.getValue();
              this.bridgePoolAssignments.put(fingerprint, details);
            }
          }
        }
      }
    }
  }

  public void writeDetailDataFiles() {
    SortedMap<String, File> remainingDetailsFiles =
        this.listAllDetailsFiles();
    remainingDetailsFiles = this.updateRelayDetailsFiles(
        remainingDetailsFiles);
    remainingDetailsFiles = this.updateBridgeDetailsFiles(
        remainingDetailsFiles);
    this.deleteDetailsFiles(remainingDetailsFiles);
  }

  private File detailsFileDirectory = new File("out/details");
  private SortedMap<String, File> listAllDetailsFiles() {
    SortedMap<String, File> result = new TreeMap<String, File>();
    if (detailsFileDirectory.exists() &&
        detailsFileDirectory.isDirectory()) {
      for (File file : detailsFileDirectory.listFiles()) {
        if (file.getName().length() == 40) {
          result.put(file.getName(), file);
        }
      }
    }
    return result;
  }

  private SortedMap<String, File> updateRelayDetailsFiles(
      SortedMap<String, File> remainingDetailsFiles) {
    SortedMap<String, File> result =
        new TreeMap<String, File>(remainingDetailsFiles);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (Map.Entry<String, Node> relay : this.relays.entrySet()) {
      String fingerprint = relay.getKey();

      /* Read details file for this relay if it exists. */
      String descriptorParts = null;
      long publishedMillis = -1L;
      boolean containsLastRestartedLine = false;
      if (result.containsKey(fingerprint)) {
        File detailsFile = result.remove(fingerprint);
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              detailsFile));
          String line;
          boolean copyLines = false;
          StringBuilder sb = new StringBuilder();
          while ((line = br.readLine()) != null) {
            if (line.startsWith("\"desc_published\":")) {
              String published = line.substring(
                  "\"desc_published\":\"".length(),
                  "\"desc_published\":\"1970-01-01 00:00:00".length());
              publishedMillis = dateTimeFormat.parse(published).getTime();
              copyLines = true;
            } else if (line.startsWith("\"last_restarted\":")) {
              containsLastRestartedLine = true;
            }
            if (copyLines) {
              sb.append(line + "\n");
            }
          }
          br.close();
          descriptorParts = sb.toString();
        } catch (IOException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        } catch (ParseException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        }
      }

      /* Generate new descriptor-specific part if we have a more recent
       * descriptor or if the part we read didn't contain a last_restarted
       * line. */
      if (this.relayServerDescriptors.containsKey(fingerprint) &&
          (this.relayServerDescriptors.get(fingerprint).
          getPublishedMillis() > publishedMillis ||
          !containsLastRestartedLine)) {
        ServerDescriptor descriptor = this.relayServerDescriptors.get(
            fingerprint);
        StringBuilder sb = new StringBuilder();
        String publishedDateTime = dateTimeFormat.format(
            descriptor.getPublishedMillis());
        String lastRestartedString = dateTimeFormat.format(
            descriptor.getPublishedMillis()
            - descriptor.getUptime() * 1000L);
        int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
            Math.min(descriptor.getBandwidthBurst(),
            descriptor.getBandwidthObserved()));
        sb.append("\"desc_published\":\"" + publishedDateTime + "\",\n"
            + "\"uptime\":" + descriptor.getUptime() + ",\n"
            + "\"last_restarted\":\"" + lastRestartedString + "\",\n"
            + "\"advertised_bandwidth\":" + advertisedBandwidth + ",\n"
            + "\"exit_policy\":[");
        int written = 0;
        for (String exitPolicyLine : descriptor.getExitPolicyLines()) {
          sb.append((written++ > 0 ? "," : "") + "\n  \"" + exitPolicyLine
              + "\"");
        }
        sb.append("\n]");
        if (descriptor.getContact() != null) {
          sb.append(",\n\"contact\":\"" + descriptor.getContact() + "\"");
        }
        if (descriptor.getPlatform() != null) {
          sb.append(",\n\"platform\":\"" + descriptor.getPlatform()
              + "\"");
        }
        if (descriptor.getFamilyEntries() != null) {
          sb.append(",\n\"family\":[");
          written = 0;
          for (String familyEntry : descriptor.getFamilyEntries()) {
            sb.append((written++ > 0 ? "," : "") + "\n  \"" + familyEntry
                + "\"");
          }
          sb.append("\n]");
        }
        sb.append("\n}\n");
        descriptorParts = sb.toString();
      }

      /* Generate network-status-specific part. */
      Node entry = relay.getValue();
      String nickname = entry.getNickname();
      String address = entry.getAddress();
      String running = entry.getRunning() ? "true" : "false";
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      String country = entry.getCountry();
      StringBuilder sb = new StringBuilder();
      sb.append("{\"version\":1,\n"
          + "\"nickname\":\"" + nickname + "\",\n"
          + "\"fingerprint\":\"" + fingerprint + "\",\n"
          + "\"or_address\":[\"" + address + "\"],\n"
          + "\"or_port\":" + orPort + ",\n"
          + "\"or_addresses\":[\"" + address + ":" + orPort + "\"],\n"
          + "\"dir_port\":" + dirPort + ",\n"
          + "\"dir_address\":\"" + address + ":" + dirPort + "\",\n"
          + "\"running\":" + running + ",\n");
      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append("\"flags\":[");
        int written = 0;
        for (String relayFlag : relayFlags) {
          sb.append((written++ > 0 ? "," : "") + "\"" + relayFlag + "\"");
        }
        sb.append("]");
      }
      if (country != null) {
        sb.append(",\n\"country\":\"" + country + "\"");
      }
      String statusParts = sb.toString();

      /* Write details file to disk. */
      File detailsFile = new File(detailsFileDirectory, fingerprint);
      try {
        detailsFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            detailsFile));
        bw.write(statusParts);
        if (descriptorParts != null) {
          bw.write(",\n" + descriptorParts);
        } else {
          bw.write("\n}\n");
        }
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not write details file '"
            + detailsFile.getAbsolutePath() + "'.  This file may now be "
            + "broken.  Ignoring.");
        e.printStackTrace();
      }
    }

    /* Return the files that we didn't update. */
    return result;
  }

  private SortedMap<String, File> updateBridgeDetailsFiles(
      SortedMap<String, File> remainingDetailsFiles) {
    SortedMap<String, File> result =
        new TreeMap<String, File>(remainingDetailsFiles);
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss");
    dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    for (Map.Entry<String, Node> bridge : this.bridges.entrySet()) {
      String fingerprint = bridge.getKey();

      /* Read details file for this bridge if it exists. */
      String descriptorParts = null, bridgePoolAssignment = null;
      long publishedMillis = -1L;
      if (result.containsKey(fingerprint)) {
        File detailsFile = result.remove(fingerprint);
        try {
          BufferedReader br = new BufferedReader(new FileReader(
              detailsFile));
          String line;
          boolean copyDescriptorParts = false;
          StringBuilder sb = new StringBuilder();
          while ((line = br.readLine()) != null) {
            if (line.startsWith("\"desc_published\":")) {
              String published = line.substring(
                  "\"desc_published\":\"".length(),
                  "\"desc_published\":\"1970-01-01 00:00:00".length());
              publishedMillis = dateTimeFormat.parse(published).getTime();
              copyDescriptorParts = true;
            } else if (line.startsWith("\"pool_assignment\":")) {
              bridgePoolAssignment = line;
              copyDescriptorParts = false;
            } else if (line.equals("}")) {
              copyDescriptorParts = false;
            }
            if (copyDescriptorParts) {
              sb.append(line + "\n");
            }
          }
          br.close();
          descriptorParts = sb.toString();
          if (descriptorParts.endsWith(",\n")) {
            descriptorParts = descriptorParts.substring(0,
                descriptorParts.length() - 2);
          } else if (descriptorParts.endsWith("\n")) {
            descriptorParts = descriptorParts.substring(0,
                descriptorParts.length() - 1);
          }
        } catch (IOException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        } catch (ParseException e) {
          System.err.println("Could not read '"
              + detailsFile.getAbsolutePath() + "'.  Skipping");
          e.printStackTrace();
          publishedMillis = -1L;
          descriptorParts = null;
        }
      }

      /* Generate new descriptor-specific part if we have a more recent
       * descriptor. */
      if (this.bridgeServerDescriptors.containsKey(fingerprint) &&
          this.bridgeServerDescriptors.get(fingerprint).
          getPublishedMillis() > publishedMillis) {
        ServerDescriptor descriptor = this.bridgeServerDescriptors.get(
            fingerprint);
        StringBuilder sb = new StringBuilder();
        String publishedDateTime = dateTimeFormat.format(
            descriptor.getPublishedMillis());
        String lastRestartedString = dateTimeFormat.format(
            descriptor.getPublishedMillis()
            - descriptor.getUptime() * 1000L);
        int advertisedBandwidth = Math.min(descriptor.getBandwidthRate(),
            Math.min(descriptor.getBandwidthBurst(),
            descriptor.getBandwidthObserved()));
        sb.append("\"desc_published\":\"" + publishedDateTime + "\",\n"
            + "\"uptime\":" + descriptor.getUptime() + ",\n"
            + "\"last_restarted\":\"" + lastRestartedString + "\",\n"
            + "\"advertised_bandwidth\":" + advertisedBandwidth + ",\n"
            + "\"exit_policy\":[");
        int written = 0;
        for (String exitPolicyLine : descriptor.getExitPolicyLines()) {
          sb.append((written++ > 0 ? "," : "") + "\n  \"" + exitPolicyLine
              + "\"");
        }
        sb.append("\n],\n\"platform\":\"" + descriptor.getPlatform()
            + "\"");
        if (descriptor.getFamilyEntries() != null) {
          sb.append(",\n\"family\":[");
          written = 0;
          for (String familyEntry : descriptor.getFamilyEntries()) {
            sb.append((written++ > 0 ? "," : "") + "\n  \"" + familyEntry
                + "\"");
          }
          sb.append("\n]");
        }
        descriptorParts = sb.toString();
      }

      /* Look up bridge pool assignment. */
      if (this.bridgePoolAssignments.containsKey(fingerprint)) {
        bridgePoolAssignment = "\"pool_assignment\":\""
            + this.bridgePoolAssignments.get(fingerprint) + "\"";
      }

      /* Generate network-status-specific part. */
      Node entry = bridge.getValue();
      String running = entry.getRunning() ? "true" : "false";
      String address = entry.getAddress();
      int orPort = entry.getOrPort();
      int dirPort = entry.getDirPort();
      StringBuilder sb = new StringBuilder();
      sb.append("{\"version\":1,\n"
          + "\"hashed_fingerprint\":\"" + fingerprint + "\",\n"
          + "\"or_port\":" + orPort + ",\n"
          + "\"or_addresses\":[\"" + address + ":" + orPort + "\"],\n"
          + "\"dir_port\":" + dirPort + ",\n"
          + "\"running\":" + running + ",");
      SortedSet<String> relayFlags = entry.getRelayFlags();
      if (!relayFlags.isEmpty()) {
        sb.append("\n\"flags\":[");
        int written = 0;
        for (String relayFlag : relayFlags) {
          sb.append((written++ > 0 ? "," : "") + "\"" + relayFlag + "\"");
        }
        sb.append("]");
      }

      /* Append descriptor and bridge pool assignment parts. */
      if (descriptorParts != null) {
        sb.append(",\n" + descriptorParts);
      }
      if (bridgePoolAssignment != null) {
        sb.append(",\n" + bridgePoolAssignment);
      }
      sb.append("\n}\n");
      String detailsLines = sb.toString();

      /* Write details file to disk. */
      File detailsFile = new File(detailsFileDirectory, fingerprint);
      try {
        detailsFile.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(
            detailsFile));
        bw.write(detailsLines);
        bw.close();
      } catch (IOException e) {
        System.err.println("Could not write details file '"
            + detailsFile.getAbsolutePath() + "'.  This file may now be "
            + "broken.  Ignoring.");
        e.printStackTrace();
      }
    }

    /* Return the files that we didn't update. */
    return result;
  }

  private void deleteDetailsFiles(
      SortedMap<String, File> remainingDetailsFiles) {
    for (File detailsFile : remainingDetailsFiles.values()) {
      detailsFile.delete();
    }
  }
}

