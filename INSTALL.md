# Onionoo Operator's Guide

Welcome to Onionoo!  Onionoo is a web-based protocol to learn about Tor relays
and bridges. Onionoo itself was not designed as a service for human beings—at
least not directly.  Onionoo provides the data for other applications and
websites which in turn present Tor network status information to humans.

This document describes how to set up your very own Onionoo instance.  It was
written with an audience in mind that has at least some experience with running
services and is comfortable with the command line.  It's not required that you
know how to read or even write Java code, though.

Before we go ahead with setting up your Onionoo instance, let us pause for a
moment and reflect why you'd want to do that as opposed to simply using data
from an existing Onionoo instance.

Onionoo is a service, and the best reason for running a Onionoo service
instance is to offer your collected Tor network data to others.

Another reason might be to aggregate Tor network data, possibly from your
test-network and provide it to your working or research group.  And of course
you might want to run an Onionoo instance for testing purposes.  In all these
cases, setting up an Onionoo instance might make sense.

However, if you only want to use Tor network data as a client, even as input for
another service you're developing, you don't have to and probably shouldn't run
an Onionoo instance.  In that case it's sufficient to use the [Onionoo protocol]
(https://onionoo.torproject.org/protocol.html) or even one of [Onionoo's
clients] (https://onionoo.torproject.org/index.html).


## Setting up the host

You'll need a host with at least 200G disk space and 8G RAM.

In the following we'll assume that your host runs Debian stable as operating
system.  Onionoo should run on any other Linux or possibly even *BSD, though
you'll be mostly on your own with those.  And as Java is available on a variety
of other operating systems, those might work, too, but, again, you'll be on your
own.

Onionoo does not require installing many or specific dependencies on the host
system.  All it needs are a Java Runtime Environment version 7 or higher.

The Onionoo service runs entirely under a non-privileged user account.  Any
user account will do, but feel free to create a new user account just for the
Onionoo service, if you prefer.

The Onionoo service requires running in a working directory where it can store
data and statistics files.  This working directory currently is
```/srv/onionoo.torproject.org/onionoo```.

Onionoo does not require setting up a database.

This concludes the host setup.  All following setup steps can be performed with
the non-privileged user account.


## Setting up the service

### Obtaining the code

Onionoo releases are available at:

```https://dist.torproject.org/onionoo/```

Choose the latest tarball and signature file, verify the signature on the
tarball, and extract the tarball in a location of your choice which will create
a subdirectory called `onionoo-<version>/`.


### Obtaining additional data

Onionoo uses an IP-to-city database and an IP-to-ASN database to provide
additional information about a relay's location.

MaxMind GeoLite2 City is available at:
https://geolite.maxmind.com/download/geoip/database/GeoLite2-City-CSV.zip

And the most recent MaxMind GeoLite ASN database file can be found at
https://www.maxmind.com/download/geoip/database/asnum/GeoIPASNum2.zip


### Planning the service setup

Currently Onionoo expects all files to be in the working folder
```/srv/onionoo.torproject.org/onionoo/```
This is not as strict as it seems as this path can be a symbolic link.

The updater needs to be run in `/srv/onionoo.torproject.org/onionoo/`,
but the web application war file can be started anywhere on the filesystem
as long as the executing user has access to everything in
`/srv/onionoo.torproject.org/onionoo/`.

The additional data downloaded above needs to be unzipped to
```/srv/onionoo.torproject.org/onionoo/geoip```

Onionoo consists of a background updater with an internal scheduler and
an embedded Jetty web module.

The release tarball contains an executable .jar file, the updater:

```onionoo-<version>/generated/dist/signed/onionoo-<version>.jar```

Copy this .jar file into the working directory and run it:

```java -jar collector-<version>.jar --help```

Onionoo will print the usage text for the updater.

There is also an executable war file, the web application with an embedded
Jetty:

```onionoo-<version>/generated/dist/signed/onionoo-<version>.war```

By default, Onionoo is configured to run the updater hourly and provide the
web pages on http://localhost:8080/.  The exact timing for the hourly update
runs is logged at start-up.


### Performing the initial run

When you have made a plan how to configure your Onionoo instance, take a
look at the usage imformation:

```Please provide only a single execution:
  [no argument]    Run steps 1--3 repeatedly once per hour.
  --single-run     Run steps 1--3 only for a single time, then exit.
  --download-only  Only run step 1: download recent descriptors, then exit.
  --update-only    Only run step 2: update internal status files, then exit.
  --write-only     Only run step 3: write output document files, then exit.
  --help           Print out this help message and exit.```


Run the Java process using:

```java -Xmx4g -DLOGBASE=<your-log-dir> -jar onionoo-<version>.jar --single-run```

The option `-Xmx4g` sets the maximum heap space to 4G, which is based on the
recommended 8G total RAM size for the host.  If you have more memory to spare,
feel free to adapt this option as needed.  Note that there is no option to limit
the amount of disk space used.

This may take a while.  Read the logs to learn if the run was successful.


### Scheduling periodic runs

The next step in setting up the Onionoo instance is to run continuously in the
background.  Just omit the argument above, i.e.:

```java -Xmx4g -DLOGBASE=<your-log-dir> -jar onionoo-<version>.jar```


### Setting up the website

Start the Onionoo server

```java -Xmx4g -DLOGBASE=<your-log-dir> -jar onionoo-<version>.war```

Check the logs in <your-log-dir>.  The Onionoo server should now be available
at http://localhost:8080/.  The Onionoo protocol is available on your new
instance http://localhost:8080/protocol.html and also has some example links
to query your Onionoo instance.

We recommend setting up an HTTP server that redirects to Onionoo.  Please
consult your favorite http server documentation as this topic is not in the
scope of this document.


## Maintaining the service

### Monitoring the service

The most important information about your Onionoo instance is whether it is
alive.  Otherwise, if it dies and you don't notice, you might be losing data
that is not available at the data sources anymore.  You should set up a
notification mechanism of your choice to be informed quickly when the background
updater dies.

Other than fatal issues, a good source for learning about issues with your
Onionoo instance are its logs.  Be sure to read the logs every now and then,
and look out for warnings and errors.  Maybe set up another notification to be
informed quickly of new warnings or errors.


### Changing logging options

Onionoo uses Logback for logging and comes with a default logging
configuration that creates a common log file that rotates once per day and a
separate log file only for error level log statements as well as a statistics
logfile.  If you want to change logging options, copy the default logging
configuration from `onionoo-<version>/src/main/resources/logback.xml` to your
working directory, edit your copy, and execute the .jar file as follows:

```java -Xmx4g -DLOGBASE=<your-log-dir> -jar -cp .:onionoo-<version>.jar
org.torproject.onionoo.cron.Main```

Internally, Onionoo uses the Simple Logging Facade for Java (SLF4J) and ships
with the Logback implementation for SLF4J.  If you prefer a different logging
framework, you can provide and use that instead.  For more detailed information,
or if you have different logging needs, please refer to the [Logback
documentation](http://logback.qos.ch/), and for switching to a different
framework to the [SFL4J website](http://www.slf4j.org/).


### Changing configuration options

Onionoo doesn't require configuration.  If you're not happy with the scheduling
settings, you can configure your own scheduling pattern by using the `single-run`
command line argument and supply this line to an external scheduler.

Settings for the embedded Jetty currently require usage of Ant, which is not
in scope for this document.  The Jetty configuration can be found in
`onionoo-<version>/src/main/resources/jetty.xml`
If you change anything, run `ant clean war` and use the resulting war file in
`onionoo-<version>/generated/dist`.


### Stopping the service

If you need to stop the background updater for some reason, like rebooting the
host, the Java process needs to be killed.  Make sure by looking at the log,
that the update process is finished and idle. But, even if a run needs to be
forcefully interrupted, all should be fine again after the next completed round.

The web application also just has to be terminated forcefully.


### Upgrading and downgrading

If you need to upgrade to a newer release or downgrade to a previous release,
download that tarball and extract it, and copy over the executable .jar and
.war files.  Stop the current service version as described above, possibly
adapt settings if necessary, and restart the Java processes using the new
.jar and .war files.  Don't forget to update the version number in the command
that ensures that the executable files get executed automatically after a reboot.
Watch the logs to see if the upgrade or downgrade was successful.


### Backing up data and settings

A backup of your Onionoo instance should include the directories `status` and
`out` in `/srv/onionoo.torproject.org/onionoo/`.


### Performing recurring tasks

Most of Onionoo is designed to just run in the background forever.  One
exception are the files in `/srv/onionoo.torproject.org/onionoo/geoip` that
should be updated once per month.


### Resolving common issues

Onionoo might run into issues from time to time, but fortunately there are no
known issues that operators would have to be aware of.

