#!/bin/bash

set -e

export DOCKER_BUILDKIT=1

# Build and run BlackLab Server
# (--force-recreate to avoid error 'network not found')
docker-compose up --force-recreate -d --build testserver

# Build and run the test suite
docker-compose build test
docker-compose run --rm test

# Clean up
# (stop then down to avoid warning about network in use)
docker-compose stop testserver
docker-compose down -v
