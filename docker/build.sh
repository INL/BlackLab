#!/bin/sh

# Go to the top directory of the repo
cd $(dirname "$0")/..
pwd

# Build the docker image
docker build -f docker/Dockerfile.blacklab-server -t instituutnederlandsetaal/blacklab-server .
