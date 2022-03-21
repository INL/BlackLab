#!/bin/bash
set -e

echo Downloading test and configuration data
aws s3 cp ${REMOTE_DATA_URL} ${GITHUB_WORKSPACE}/test/data-integ-test --recursive --only-show-errors
aws s3 cp ${REMOTE_CONFIGURATION_URL} ${GITHUB_WORKSPACE}/custom-configuration --recursive --only-show-errors

# Writes the location of the test data for downstream tasks
# This is relative to the docker container filesystem running the tests
echo "INDEX_TEST_DATA_ROOT=data-integ-test" >> $GITHUB_ENV

echo "CONFIG_ROOT=custom-configuration" >> $GITHUB_ENV
