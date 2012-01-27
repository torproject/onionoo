#!/bin/bash
rsync -arz metrics.torproject.org::metrics-recent in
ant run >> log

