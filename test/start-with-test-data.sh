#!/bin/bash

# This script is intended for use with the integration tests.
# It will remove previous data, index the test data and start Tomcat.

rm -rf /data/index
rm -rf /data/user-index

mkdir /data/index
mkdir /data/user-index

cd /usr/local/lib/blacklab-tools
java -cp '*' nl.inl.blacklab.tools.IndexTool create /data/index/test '/test-data/input/*.xml' voice-tei
#cd /usr/local/tomcat && catalina.sh jpda run
cd /usr/local/tomcat && catalina.sh run
