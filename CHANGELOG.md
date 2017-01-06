# Changes in version x.x.x - 2017-xx-xx

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

 * Minor changes
   - Include XZ binaries in release binaries.


# Changes in version 3.1-1.0.0 - 2016-11-23

 * Major changes
   - This is the initial release after over five years of development.

