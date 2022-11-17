## Stage 0: Build Solr plugin
#=============================
#FROM maven:3.6-jdk-11 AS builder
#WORKDIR /app
#
## Copy source from stage 0
#COPY . .
#
# Build plugin JAR
#RUN --mount=type=cache,target=/root/.m2 mvn --no-transfer-progress package
#

## Stage 1: Solr
#=============================
FROM solr:8.11.1
LABEL maintainer="Jan Niestadt <jan.niestadt@ivdnt.org>"

# Place plugin JAR built in previous stage
USER solr
# use bind mount instead
#COPY --from=builder /app/solr/target/blacklab-solr.jar /opt/solr/contrib/blacklab-solr/lib/

# Create directory for our test core
RUN mkdir -p /var/solr/data/test

# Copy test core
COPY solr/src/test/resources/existing-index /var/solr/data/test/

# Install JAR in core's lib dir (will be loaded autmatically)
# (still gives us the SPI problem though)
#COPY solr/target/blacklab-solr-no-deps.jar /var/solr/data/test/lib/

# Install JAR in main lib dir(s)
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/lib/

# (doesn't work, even if we load them explicitly?)
# COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/server/solr/lib/
# COPY solr/target/lib/blacklab-*.jar /opt/solr/server/solr/lib/

# If we include this, we get ClassNotFoundException: org.apache.lucene.codecs.PostingsFormat
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/server/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/server/lib/

#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/contrib/blacklab-solr/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/contrib/blacklab-solr/lib/

# Copy to dir specified as sharedLib in solr.xml
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/shared/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/shared/

USER root

# Copy to Solr's WEB-INF/lib. THIS WORKS!!
#COPY solr/target/blacklab-solr.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
COPY solr/target/lib/*.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
#COPY solr/target/lib/blacklab-*.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
#COPY solr/target/lib/jackson-*.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
#COPY solr/target/lib/xmlresolver-*.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/

# Make sure the files we just copied are owner by Solr, not root
# (not sure why they would be owner by root otherwise, but they are)
RUN chown -R solr:solr /var/solr/data/test

USER solr
