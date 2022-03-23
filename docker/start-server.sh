#!/bin/bash

rm -rf /data/index
rm -rf /data/user-index

mkdir /data/index
mkdir /data/user-index

cd /usr/local/lib/blacklab-tools
java -cp '*' nl.inl.blacklab.tools.IndexTool create /data/index/test /input voice-tei
cd /usr/local/tomcat && catalina.sh jpda run
