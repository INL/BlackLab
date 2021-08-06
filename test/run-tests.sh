#!/bin/sh

set -e

# Ensure the server is awake and the index has been opened.
sleep 5
wget -O - ${APP_URL:-http://localhost:8080/blacklab-server}/test/hits?patt=%22passport%22 > /dev/null

# Run the tests.
npm run test
