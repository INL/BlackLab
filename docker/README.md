# Docker image (experimental)

This is an experimental Docker image for BlackLab Server.

To build it, change to the top-level directory of the repository and run:

```bash
docker build -f docker/Dockerfile -t instituutnederlandsetaal/blacklab .
```

This image has a default configuration file in `/etc/blacklab/blacklab-server.yaml`; if necessary, you can overwrite this with your own version.

## Making indexes available

The default configuration will look for indexes in `/data/`.

### Index files inside the container

This is the easiest way to use this image, and probably preferable to using bind mounts (although those can have their uses as well).

It is strongly recommended to use a named volume for `/data/`, where your indexes should be stored to be read by BlackLab Server. Otherwise, an anonymous volume will be created, making it difficult to keep track of your data and increasing the risk of accidental deletion.

To index files in the container, bind mount the input files and run IndexTool:

(TODO)



### Add indexes using a bind mount

If you have some indexes on the local machine and want to use a bind mount to access them from a BlackLab Server container, use:

```bash
docker run --rm -p 8080:8080 --name blacklab --mount 'type=bind,src=/path/to/my/indexes,dst=/data' instituutnederlandsetaal/blacklab
```

(this assumes your indexes are in `/path/to/my/indexes/index1`, etc.)



## TODO
- use volumes
- offer a separate Docker image for indexing data into a volume
- user?
- 
- (more to come)
