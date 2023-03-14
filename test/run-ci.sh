#!/bin/bash

# Run the integration tests.
#
# This script is intended to run on a Continuous Integration server, e.g. GitHub Actions.
# If a new test has been added, but no saved response file exists, the test will fail.
# You should execute run-local.sh to save and commit the new response file.

$( dirname -- "$0"; )/testrunner.sh test
exit 0
