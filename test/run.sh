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
$COMPOSE up --force-recreate -d --build testserver

# Build and run the test suite
$COMPOSE build test
$COMPOSE run --rm test

# Clean up
# (stop then down to avoid warning about network in use)
$COMPOSE stop testserver
#$COMPOSE down -v
docker container rm blacklab_testserver_1
docker volume rm blacklab_blacklab-data
docker network rm blacklab_default
