How to use Vagrant to build and test Onionoo
============================================

Before using Vagrant, make sure that Onionoo builds correctly on the host
system.
This may require running `git submodule init && git submodule update`, as
well as providing all required libraries.

The given Vagrant file uses Version 2, i.e., a Vagrant installation
of version 1.1 or above is necessary.
The following was tested using 1.4.3 and VirtualBox 4.3.14.
(Wheezy stable only provides 1.0.3, Jessie provides 1.4.3)

Local changes to the Vagrantfile:
Tell Vagrant how much memory the virtual machine may use, i.e., change

```
vb.memory = 4096
```

to some value that makes sense on your machine.
Rule of thumb: less than half the RAM, but as much as you want to spare.

Create a Debian Wheezy 64 bit instance:

```
vagrant up
```

This command downloads the virtual machine imagine, unless it has been
downloaded before, creates a new virtual machine, and runs the bootstrap
script in `vagrant/bootstrap.sh`.  This may take a few minutes.

Once this is all done, log into the virtual machine and change to the
Onionoo working directory:

```
vagrant ssh
cd /srv/onionoo.torproject.org/onionoo/
```

Important: better avoid runninng Ant in the `/vagrant/` directory (which
is shared with the host), or the guest system will write directly to the
host system, which performs not really well.

Read the INSTALL file and make the appropriate changes to adapt everything
to your setup, e.g., memory settings.
Compile Onionoo, run the unit tests and then the cron part of it:

```
ant compile
ant test
ant run
```

This step may take an hour or more.  Onionoo downloads the last three days
of Tor descriptors, which is about 2 GiB, and processes them.

Once these steps are done, deploy the servlet to the local Tomcat server:

```
ant war
```

Test the Onionoo service using a browser on the host (port 8080 on the guest
is forwarded to the host).  Sample URL:

http://localhost:8080/onionoo/summary?limit=2

Note that Tomcat's default server.xml needs no changing for running in the
development environment.
See the INSTALL file for necessary changes in the production environment.

