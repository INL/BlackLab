## Stage 0: Build Solr plugin
#=============================
FROM maven:3.6-jdk-11 AS builder
WORKDIR /app

# Copy source
COPY . .

# Build plugin JAR
RUN --mount=type=cache,target=/root/.m2 mvn --no-transfer-progress package


## Stage 1: Solr
#=============================
FROM solr:8.11.2
LABEL maintainer="Jan Niestadt <jan.niestadt@ivdnt.org>"

# Place plugin JAR built in previous stage
USER solr

# Create directory for our test core
RUN mkdir -p /var/solr/data/test

# Copy test core
COPY solr/src/test/resources/existing-index /var/solr/data/test/

# NOTE: the following commented-out locations for our JARs don't work! See below.

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

# Copy JARs from previous stage to Solr's WEB-INF/lib. THIS WORKS!!
COPY --from=builder /app/solr/target/blacklab-solr-no-deps.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
COPY --from=builder /app/solr/target/lib/*.jar                 /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/

# Copy externally built JARs to Solr's WEB-INF/lib. THIS WORKS!!
#COPY solr/target/blacklab-solr-no-deps.jar /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
#COPY solr/target/lib/*.jar                 /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/

# Make sure the files we just copied are owner by Solr, not root
# (roundabout way of doing it, but if we don't first copy as root, then set ownership, we get an error)
RUN chown -R "$SOLR_UID":"$SOLR_GROUP" /var/solr/data/test

USER solr
