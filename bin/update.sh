#!/bin/bash
rsync -az --delete metrics.torproject.org::'metrics-recent/relay-descriptors/consensuses/ metrics-recent/relay-descriptors/server-descriptors/ metrics-recent/relay-descriptors/extra-infos/ metrics-recent/bridge-descriptors/statuses/ metrics-recent/bridge-descriptors/server-descriptors/ metrics-recent/bridge-descriptors/extra-infos/ metrics-recent/bridge-pool-assignments/ metrics-recent/exit-lists' in
ant run >> log

