# Changes in version 4.3-1.7.0 - 2017-1?-??

 * Medium changes
   - Support quoted qualified search terms.
   - Skip unrecognized descriptors when importing archives rather than
     aborting the entire import.
   - Add new "host_name" parameter to filter by host name.
   - Add new "unreachable_or_addresses" field with declared but
     unreachable OR addresses.


# Changes in version 4.2-1.6.1 - 2017-10-26

 * Medium changes
   - Fix two NullPointerExceptions caused by accessing optional parts
     of relay server descriptors and consensuses without checking
     first whether they're available or not.


# Changes in version 4.2-1.6.0 - 2017-10-09

 * Medium changes
   - Only set the "running" field in a bridge's details document to
     true if the bridge is both contained in the last known bridge
     network status and has the "Running" flag assigned there.
   - Add build_revision to documents, if available.
   - Update to metrics-lib 2.1.1.

 * Minor changes
   - Remove placeholder page on index.html.


# Changes in version 4.1-1.5.0 - 2017-09-15

 * Major changes
   - Update to metrics-lib 2.1.0 and to Java 8.


# Changes in version 4.1-1.4.1 - 2017-08-31

 * Medium changes
   - Fix a NullPointerException in the recently added "version"
     parameter.


# Changes in version 4.1-1.4.0 - 2017-08-30

 * Medium changes
   - Reset IPv6 exit-policy summary in details status if a newer
     server descriptor doesn't contain such a summary anymore.
   - Remove optional fields "countries", "transports", and "versions"
     from clients objects which were still labeled as beta.
   - Add new "version" parameter to filter for Tor version.

 * Minor changes
   - Switch from our own CollecTor downloader to metrics-lib's
     DescriptorCollector.
   - Add a new Java property "onionoo.basedir" to re-configure the
     base directory used by the web server component.


# Changes in version 4.0-1.3.0 - 2017-08-04

 * Medium changes
   - Add a parse history for imported descriptor archives.
   - Upgrade to Jetty9 and other Debian stretch dependencies.


# Changes in version 4.0-1.2.0 - 2017-02-28

 * Medium changes
   - Accept searches by IPv6 addresses even without leading or
     enclosing square brackets.


# Changes in version 3.2-1.1.0 - 2017-01-27

 * Major changes
   - Fix a bug where we'd believe that we have first seen a bridge on
     January 1, 1970 when in fact we have never seen it in a bridge
     network status and only learned about it from its self-published
     bridge server descriptor.

 * Medium changes
   - Unify the build process by adding git-submodule metrics-base in
     src/build and removing all centralized parts of the build
     process.
   - Accept the same characters in qualified search terms as in their
     parameter equivalents.
   - Exclude bandwidth history values from the future.
   - Extend order parameter to "first_seen".
   - Add response meta data fields "relays_skipped",
     "relays_truncated", "bridges_skipped", and "bridges_truncated".

 * Minor changes
   - Include XZ binaries in release binaries.


# Changes in version 3.1-1.0.0 - 2016-11-23

 * Major changes
   - This is the initial release after over five years of development.

