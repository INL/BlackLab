#!/bin/bash

# Run the integration tests.
#
# This script is intended to be run locally on a development machine.
# If a new test has been added, and no saved response file exists, the test will SUCCEED,
# and the response from the server will be saved for future runs.
# Run this script after adding a new test, then commit the new response file.

$( dirname -- "$0"; )/testrunner.sh test-local
exit 0
