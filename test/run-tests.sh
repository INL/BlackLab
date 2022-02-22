#!/bin/sh

set -e
## Load overrides for the testing environment if any
if [ -f ${INDEX_TEST_DATA_ROOT}/environment ]; then
  echo "sourcing custom environment for tests"
  . ${INDEX_TEST_DATA_ROOT}/environment
fi

sleep 5
# Ensure the server is awake and the index has been opened.
echo "testing on url ${APP_URL}"
wget -O - ${APP_URL:-http://localhost:8080/blacklab}/test/hits?patt=%22passport%22 > /dev/null

# Run the tests.
npm run test
