# Changes in version ???

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

