#!/bin/bash

if [ $# -lt 3 ]; then
  echo
  echo 'Index a corpus using BlackLab IndexTool'
  echo '---------------------------------------'
  echo
  echo 'This runs IndexTool inside a Docker container with bind mounts for the'
  echo 'index, input and format (if necessary).'
  echo
  echo 'Usage:'
  echo
  echo '  ./index-corpus.sh <TARGET_DIR> <INPUT> <FORMAT> [INDEXTOOL_OPTIONS]'
  echo
  echo 'Please note that symlinks in <INPUT> will generally not work inside the container,'
  echo "because the target path won't be mounted."
  echo
  echo 'By default, IndexTool will be started with options --threads 4 --index-type integrated'
  echo 'you can override these by passing them as the fourth argument.'
  echo
  echo 'Examples:'
  echo
  echo '  # relative paths, builtin format, override options'
  echo "  ./index-corpus.sh index input tei-p5 '--threads 2 --index-type external'"
  echo
  echo '  # absolute paths and custom format config file'
  echo '  ./index-corpus.sh /bl-corpora/mycorpus /input-data/mycorpus /blacklab-formats/format.blf.yaml'
  echo
  exit 1
fi

# Absolute paths of our arguments
BL_CORPUS_TARGET_DIR=$(realpath "$1")
BL_CORPUS_INPUT_DIR=$(realpath $2)
BL_CORPUS_FORMAT="$3"
BL_CORPUS_FORMAT_FILE=$(realpath "$BL_CORPUS_FORMAT")
BL_INDEXTOOL_OPTIONS="${4:---threads 4 --index-type integrated}"

# Base names to use inside the container
BL_CORPUS_NAME=$(basename $BL_CORPUS_TARGET_DIR)
BL_CORPUS_INPUT_DIR_NAME=$(basename $BL_CORPUS_INPUT_DIR)     # (so fromInputFile makes more sense)

# Full paths inside container
BL_CONTAINER_CORPUS_DIR="/data/index/$BL_CORPUS_NAME"
BL_CONTAINER_INPUT_DIR="/input/$BL_CORPUS_INPUT_DIR_NAME"

# Ensure target dir exists so we can bind mount
mkdir -p $BL_CORPUS_TARGET_DIR

BL_CONTAINER_USER_GROUP="$(id -u):$(id -g)"
echo "Running as user:group $BL_CONTAINER_USER_GROUP"

# See if we need to bind the format file (if it doesn't seem to exist, it's a builtin format)
BIND_FORMAT=
BL_CONTAINER_FORMAT="$BL_CORPUS_FORMAT"
if [ -f "$BL_CORPUS_FORMAT_FILE" ]; then
  BL_CORPUS_FORMAT_FILE_NAME=$(basename $BL_CORPUS_FORMAT_FILE)
  BL_CONTAINER_FORMAT="/tmp/blacklab-formats/$BL_CORPUS_FORMAT_FILE_NAME"
  BIND_FORMAT="--mount type=bind,src=$BL_CORPUS_FORMAT_FILE,dst=$BL_CONTAINER_FORMAT "
fi

# Run the indexer
docker run --user $BL_CONTAINER_USER_GROUP --rm \
    --name blacklab-indexer \
    --mount type=bind,src="$BL_CORPUS_TARGET_DIR",dst="$BL_CONTAINER_CORPUS_DIR" \
    --mount type=bind,src="$BL_CORPUS_INPUT_DIR",dst="$BL_CONTAINER_INPUT_DIR" \
    $BIND_FORMAT\
    instituutnederlandsetaal/blacklab:latest \
    /bin/bash -c "\
      cd /usr/local/lib/blacklab-tools && \
      java -cp '*' nl.inl.blacklab.tools.IndexTool $BL_INDEXTOOL_OPTIONS create \
        $BL_CONTAINER_CORPUS_DIR \
        $BL_CONTAINER_INPUT_DIR \
        $BL_CONTAINER_FORMAT"

exit 0
