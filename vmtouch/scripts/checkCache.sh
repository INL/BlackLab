#!/bin/sh
# This script checks to see how much of the set of files to cache
# is currently in the disk cache. Should eventually reach and stay
# at 100%.

DATASETS_DIR=/blacklab/indices
VMTOUCH_BINARY=/blacklab/bin/vmtouch/vmtouch

# Figure out the list of files to cache
cd $DATASETS_DIR
FILES_TO_CACHE=
for DATASET in `ls $DATASETS_DIR`
do
  if [ -d $DATASET ]; then
    FILES_TO_CACHE="$FILES_TO_CACHE $DATASET/fi_contents%word/tokens.dat $DATASET/fi_contents%lemma/tokens.dat $DATASET/fi_contents%pos/tokens.dat $DATASET/fi_contents%punct/tokens.dat"
  fi
done

# Check to see how much of the files has been cached
$VMTOUCH_BINARY -v $FILES_TO_CACHE