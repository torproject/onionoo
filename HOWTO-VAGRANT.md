How to use Vagrant to build and test Onionoo
============================================

Before using Vagrant, make sure that Onionoo builds correctly on the host system.  This may require running `git submodule init && git submodule update`, as well as providing all required libraries.

Create a Debian Wheezy 64 bit instance:

```
vagrant up
```

This command downloads the virtual machine imagine, unless it has been downloaded before, creates a new virtual machine, and runs the bootstrap script in `vagrant/bootstrap.sh`.  This may take a few minutes.

Once this is all done, log into the virtual machine and change to the Onionoo working directory:

```
vagrant ssh
cd /srv/onionoo.torproject.org/onionoo/
```

Important: better avoid runninng Ant in the `/vagrant/` directory (which is shared with the host), or the guest system will write directly to the host system, which performs not really well.

Compile Onionoo, run the unit tests and then the cron part of it:

```
ant compile
ant test
ant run
```

This step may take an hour or more.  Onionoo downloads the last three days of Tor descriptors, which is about 2 GiB, and processes them.

Once these steps are done, deploy the servlet to the local Tomcat server:

```
ant war
```

Test the Onionoo service using a browser on the host (port 8080 on the guest is forwarded to the host).  Sample URL:

http://localhost:8080/onionoo/summary?limit=2

