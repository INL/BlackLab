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

# Go to the repository root
cd $( dirname -- "$0"; )/..

# Check how to call Compose
COMPOSE=docker-compose
if ! command -v $COMPOSE &> /dev/null
then
    COMPOSE="docker compose"
fi

export DOCKER_BUILDKIT=1

#----------------------------------------------------------
# Build and run BlackLab Server
# (--force-recreate to avoid error 'network not found')
echo === Testing classic index format...
export BLACKLAB_FEATURE_integrateExternalFiles=false
$COMPOSE up -d --build --force-recreate testserver

# Build and run the test suite
$COMPOSE build "$SERVICE_NAME"
$COMPOSE run --rm "$SERVICE_NAME"

# Clean up
# (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE stop testserver
$COMPOSE rm -fv testserver


#----------------------------------------------------------
# Re-run to test the other index format as well
echo === Testing integrated index format...
export BLACKLAB_FEATURE_integrateExternalFiles=true
$COMPOSE up -d testserver
$COMPOSE run --rm "$SERVICE_NAME"

# Clean up
# (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE stop testserver
$COMPOSE rm -fv testserver


##----------------------------------------------------------
## Re-run the same tests using Solr+proxy
cd proxy
$COMPOSE up --force-recreate -d --build
cd ..
export APP_URL=http://host.docker.internal:8080/blacklab-server
export CORPUS_NAME=test
export SKIP_INDEXING_TESTS=true   # not yet implemented for Solr
sleep 15 # allow a little time to start up
$COMPOSE build "$SERVICE_NAME"
$COMPOSE run --rm "$SERVICE_NAME"

# Clean up
# (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
cd proxy
$COMPOSE stop
$COMPOSE rm -fv
cd ..
