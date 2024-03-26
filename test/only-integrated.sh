#!/bin/bash

# Run the integration tests.
#
#   testrunner.sh <servicename>
#
# For <servicename>, use "test" for CI testing,
# "test-local" to run locally (and automatically save
# missing responses).

set -o errexit  # Exit on error (set -e)

# Get the servicename (or default to "test", the regular CI test)
SERVICE_NAME="${1:-test}"

# Go to the test dir
cd $( dirname -- "$0"; )/

# Check how to call Compose
#COMPOSE=docker-compose
#if ! command -v $COMPOSE &> /dev/null
#then
    COMPOSE="docker compose"
#fi

# Enable BuildKit
export DOCKER_BUILDKIT=1



#----------------------------------------------------------
# Re-run to test the other index format as well
echo === Testing integrated index format...
$COMPOSE build testserver "$SERVICE_NAME"
export BLACKLAB_FEATURE_integrateExternalFiles=true
export INDEX_TYPE=integrated
$COMPOSE up -d --force-recreate testserver # (--force-recreate to avoid error 'network not found')
$COMPOSE run --rm "$SERVICE_NAME"
$COMPOSE stop testserver # (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE rm -fv testserver
