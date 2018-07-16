# Changes in version 6.1-1.15.0 - 2018-07-16

 * Medium changes
   - Provide more accurate DNS results in "verified_host_names" and
     "unverified_host_names".
   - Allow filtering by operating system using the new "os" parameter.

 * Minor changes
   - Index relays with no known country code or autonomous system
     number using the special values "xz" and "AS0" respectively.
   - Avoid running into an IllegalStateException when CollecTor is
     missing a whole descriptor directory.


# Changes in version 6.0-1.14.0 - 2018-05-29

 * Medium changes
   - Replace Gson with Jackson.


# Changes in version 6.0-1.13.0 - 2018-04-17

 * Medium changes
   - Change the "exit_addresses" field to not exclude current OR
     addresses anymore.

 * Minor changes
   - Turn valid utf-8 escape sequences into utf-8 characters.


# Changes in version 5.2-1.12.0 - 2018-04-06

 * Medium changes
   - Add version_status field to details documents.
   - Fetch descriptors from both CollecTor instances.

 * Minor changes
   - Don't attempt to un-escape character sequences in contact lines
     (like "\uk") that only happen to start like escaped utf-8 characters
     (like "\u0055").


# Changes in version 5.1-1.11.0 - 2018-03-14

 * Medium changes
   - Stop omitting "n" in summary docs for "Unnamed" relays/bridges.
   - Always add a relay to its own "effective_family".

 * Minor changes
   - Make responses deterministic by always sorting results by
     fingerprint, either if no specific order was requested or to
     break ties after ordering results as requested.
   - Announce next major protocol version update on April 14, 2018.


# Changes in version 5.0-1.10.1 - 2018-02-07

 * Medium changes
   - Change 3 month weights graph to 24 hours detail.


# Changes in version 5.0-1.10.0 - 2018-02-07

 * Medium changes
   - Make writing of bandwidth, clients, uptime, and weights documents
     independent of system time.
   - Change 3 month bandwidth graph to 24 hours detail.


# Changes in version 5.0-1.9.0 - 2017-12-20

 * Medium changes
   - Remove the $ from fingerprints in fields "alleged_family",
     "effective_family", and "indirect_family".


# Changes in version 4.4-1.8.0 - 2017-11-28

 * Medium changes
   - Add a "version" field to relay details documents with the Tor
     software version listed in the consensus and similarly to bridge
     details documents with the Tor software version found in the
     server descriptor.
   - Extend the "version" parameter to also return bridges with the
     given version or version prefix.
   - Add a "recommended_version" field to bridge details documents
     based on whether the directory authorities recommend the bridge's
     version.
   - Add a "recommended_version" parameter to return only relays and
     bridges running a Tor software version that is recommended or not
     recommended by the directory authorities.


# Changes in version 4.3-1.7.1 - 2017-11-17

 * Minor changes
   - Include "unreachable_or_addresses" as accepted value in the
     "fields" parameter.


# Changes in version 4.3-1.7.0 - 2017-11-17

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

