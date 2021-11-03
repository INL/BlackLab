#!/bin/bash
# This script will read specific large files in BlackLab indices into the
# disk cache and lock them there. This improves performance for
# applications needing random access on those files.

if [ "$EUID" -ne 0 ]
then
  echo "Please run as root"
  exit
fi

DATASETS_DIR=/blacklab/indices
VMTOUCH_BINARY=/blacklab/bin/vmtouch/vmtouch

# Figure out the list of files to cache
cd $DATASETS_DIR
FILES_TO_CACHE=
for DATASET in `ls $DATASETS_DIR`
do
  if [ -d $DATASET ]; then
    echo "  $DATASET"
    FILES_TO_CACHE="$FILES_TO_CACHE $DATASET/fi_contents%word/tokens.dat $DATASET/fi_contents%lemma/tokens.dat $DATASET/fi_contents%pos/tokens.dat $DATASET/fi_contents%punct/tokens.dat"
  fi
done

# Start vmtouch daemon to keep files in disk cache
$VMTOUCH_BINARY -vtld $FILES_TO_CACHE