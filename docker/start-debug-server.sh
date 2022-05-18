#!/bin/bash

# This script is intended for regular (debug) use of the BlackLab image,
# other than the integration tests. It will simply start Tomcat in debug mode.

cd /usr/local/tomcat && catalina.sh jpda run
