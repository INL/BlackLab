#!/bin/sh

# Run the docker image
docker run --rm -p 8080:8080 --name blacklab-server --mount 'type=bind,src=/home/jan/blacklab/data/zeebrieven/index,dst=/data/zeebrieven' instituutnederlandsetaal/blacklab-server

# To use a bind mount for your indexes, use:
#   docker run --rm -p 8080:8080 --name blacklab-server --mount 'type=bind,src=/path/to/my/indexes,dst=/data' instituutnederlandsetaal/blacklab-server
# (this assumes your indexes are in /path/to/my/indexes/index1, etc.)

# TODO:
# - use volumes
# - offer a separate Docker image for indexing data into a volume
