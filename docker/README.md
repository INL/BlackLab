# Docker image (experimental)

This is an experimental Docker image for BlackLab Server.

To build it, change to the top-level directory of the repository and run:

    docker build -f docker/Dockerfile.blacklab-server -t instituutnederlandsetaal/blacklab-server .

This image has a default configuration file in `/etc/blacklab/blacklab-server.yaml`. It will look for indexes in `/data/`.

If you have some indexes on the local machine and want to use a bind mount to access them from a BlackLab Server container, use:

    docker run --rm -p 8080:8080 --name blacklab-server --mount 'type=bind,src=/path/to/my/indexes,dst=/data' instituutnederlandsetaal/blacklab-server

(this assumes your indexes are in `/path/to/my/indexes/index1`, etc.)



## TODO

- use volumes
- offer a separate Docker image for indexing data into a volume
- (more to come)
