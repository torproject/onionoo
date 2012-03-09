#!/bin/bash
rsync -arz --delete --exclude 'relay-descriptors/votes' metrics.torproject.org::metrics-recent in
ant run >> log

