#!/bin/bash
rsync -arz --delete metrics.torproject.org::metrics-recent in
ant run >> log

