#!/bin/bash

set -o errexit  # Exit on error (set -e)

# Check how to call Compose
COMPOSE=docker-compose
if ! command -v $COMPOSE &> /dev/null
then
    COMPOSE="docker compose"
fi

export DOCKER_BUILDKIT=1

# Build and run BlackLab Server
# (--force-recreate to avoid error 'network not found')
export BLACKLAB_FEATURE_integrateExternalFiles=true
$COMPOSE up --force-recreate -d --build testserver

# Build and run the test suite
$COMPOSE build test
$COMPOSE run --rm test

# Re-run to test non-integrated index as well
$COMPOSE stop testserver
$COMPOSE rm -fv testserver
export BLACKLAB_FEATURE_integrateExternalFiles=false
$COMPOSE run --rm test


# Clean up
# (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE stop testserver
$COMPOSE rm -fv testserver
