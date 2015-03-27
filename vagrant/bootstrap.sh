#!/usr/bin/env bash

echo "Starting provisioning."

echo "Updating package list and upgrading existing packages."
apt-get update
apt-get -y upgrade

echo "Removing Java 6 packages."
apt-get purge -y openjdk-6-jdk default-jre

echo "Installing required packages."
apt-get install -y openjdk-7-jdk
apt-get install -y libcommons-codec-java libcommons-compress-java \
libcommons-lang3-java libgoogle-gson-java junit4 libservlet3.0-java \
ant libslf4j-java liblogback-java unzip libjetty8-java

echo "Setting up paths and creating symbolic links."
mkdir -p /srv/onionoo.torproject.org/onionoo/
cd /srv/onionoo.torproject.org/onionoo/
ln -s /vagrant/build.xml
ln -s /vagrant/deps
ln -s /vagrant/etc
ln -s /vagrant/geoip
ln -s /vagrant/src
ln -s /vagrant/web
chown -R vagrant:vagrant /srv/onionoo.torproject.org/

echo "Done provisioning."

