#!/bin/sh
# This script will clear the disk cache, making sure
# any performance tests relating to disk cache is repeatable.

# Flush file system buffers, to be on the safe side
sync

# Drop all disk cache
echo 3 > /proc/sys/vm/drop_caches
