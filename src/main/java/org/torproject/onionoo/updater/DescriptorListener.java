package org.torproject.onionoo.updater;

import org.torproject.descriptor.Descriptor;

public interface DescriptorListener {
  abstract void processDescriptor(Descriptor descriptor, boolean relay);
}

