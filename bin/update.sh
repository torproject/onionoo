#!/bin/bash
rsync -az --delete --exclude 'relay-descriptors/votes' --exclude 'relay-descriptors/microdescs' --exclude 'relay-descriptors/server-descriptors' --exclude 'relay-descriptors/extra-infos' --exclude 'bridge-descriptors/server-descriptors' --exclude 'bridge-descriptors/extra-infos' --exclude 'torperf' metrics.torproject.org::metrics-recent in
ant run >> log

