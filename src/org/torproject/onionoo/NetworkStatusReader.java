/* Copyright 2011 The Tor Project
 * See LICENSE for licensing information */
package org.torproject.onionoo;

import java.io.*;
import java.text.*;
import java.util.*;
import org.torproject.descriptor.*;

/* Read network statuses from disk. */
public class NetworkStatusReader {
  private File relayDescriptorsDirectory = new File(
      "in/relay-descriptors");
  public NetworkStatusReader() {
    RelayDescriptorReader reader =
        DescriptorSourceFactory.createRelayDescriptorReader();
    reader.addDirectory(this.relayDescriptorsDirectory);
    Iterator<DescriptorFile> descriptorFiles = reader.readDescriptors();
    while (descriptorFiles.hasNext()) {
      DescriptorFile descriptorFile = descriptorFiles.next();
      for (Descriptor descriptor : descriptorFile.getDescriptors()) {
        if (descriptor instanceof RelayNetworkStatusConsensus) {
          this.loadedRelayNetworkStatusConsensuses.add(
              (RelayNetworkStatusConsensus) descriptor);
        } else if (descriptor instanceof RelayServerDescriptor) {
          RelayServerDescriptor serverDescriptor =
              (RelayServerDescriptor) descriptor;
          this.loadedRelayServerDescriptors.put(
              serverDescriptor.getFingerprint(), serverDescriptor);
        }
      }
    }
    /* TODO Read bridge network statuses and bridge pool assignments from
     * disk, too. */
  }
  private Set<RelayNetworkStatusConsensus>
      loadedRelayNetworkStatusConsensuses =
      new HashSet<RelayNetworkStatusConsensus>();
  public Set<RelayNetworkStatusConsensus>
      getLoadedRelayNetworkStatusConsensuses() {
    return this.loadedRelayNetworkStatusConsensuses;
  }
  private Map<String, RelayServerDescriptor>
      loadedRelayServerDescriptors =
      new HashMap<String, RelayServerDescriptor>();
  public Map<String, RelayServerDescriptor>
      getLoadedRelayServerDescriptors() {
    return this.loadedRelayServerDescriptors;
  }
  private Set<BridgeNetworkStatus> loadedBridgeNetworkStatuses =
      new HashSet<BridgeNetworkStatus>();
  public Set<BridgeNetworkStatus> getLoadedBridgeNetworkStatuses() {
    return this.loadedBridgeNetworkStatuses;
  }
}

