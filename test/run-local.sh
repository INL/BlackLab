#!/bin/bash

# Run the integration tests.
#
# This script is intended to be run locally on a development machine.
# If a new test has been added, and no saved response file exists, the test will SUCCEED,
# and the response from the server will be saved for future runs.
# Run this script after adding a new test, then commit the new response file.

set -o errexit  # Exit on error (set -e)

# Go to the repository root
cd $( dirname -- "$0"; )/..

# Check how to call Compose
COMPOSE=docker-compose
if ! command -v $COMPOSE &> /dev/null
then
    COMPOSE="docker compose"
fi

export DOCKER_BUILDKIT=1

export BLACKLAB_TEST_SAVE_MISSING_RESPONSES=true

# Build and run BlackLab Server
# (--force-recreate to avoid error 'network not found')
echo === Testing classic index format...
export BLACKLAB_FEATURE_integrateExternalFiles=false
$COMPOSE up --force-recreate -d --build testserver

# Build and run the test suite
$COMPOSE build test-local
$COMPOSE run --rm test-local

$COMPOSE stop testserver
$COMPOSE rm -fv testserver
# Re-run to test the other index format as well
echo === Testing integrated index format...
export BLACKLAB_FEATURE_integrateExternalFiles=true
$COMPOSE run --rm test-local


# Clean up
# (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE stop testserver
$COMPOSE rm -fv testserver
