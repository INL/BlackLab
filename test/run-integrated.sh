#!/bin/bash

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

# Build and run BlackLab Server
# (--force-recreate to avoid error 'network not found')
echo === Testing integrated index format...
export BLACKLAB_FEATURE_integrateExternalFiles=tru
$COMPOSE up --force-recreate -d --build testserver

# Build and run the test suite
$COMPOSE build test
$COMPOSE run --rm test
$COMPOSE restart testserver
$COMPOSE run --rm test
$COMPOSE restart testserver
$COMPOSE run --rm test
$COMPOSE restart testserver
$COMPOSE run --rm test
$COMPOSE restart testserver
$COMPOSE run --rm test

# Clean up
# (stop then rm -v instead of down -v, otherwise we get an error about the volume being in use)
$COMPOSE stop testserver
$COMPOSE rm -fv testserver
