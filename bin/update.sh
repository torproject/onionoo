#!/bin/bash
rsync -arz --delete --exclude 'relay-descriptors/votes' --exclude 'relay-descriptors/microdescs' metrics.torproject.org::metrics-recent in
ant run >> log

