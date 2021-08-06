# Stage "builder": build the WAR file
#--------------------------------------
FROM maven:3.6-jdk-11 AS builder

# Copy source
WORKDIR /app
COPY . .

# Build the WAR.
# NOTE: make sure BuildKit is enabled (see https://docs.docker.com/develop/develop-images/build_enhancements/)
#       to be able to cache Maven libs so they aren't re-downloaded every time you build the image
RUN --mount=type=cache,target=/root/.m2 mvn --no-transfer-progress package


# Tomcat container with the WAR file
#--------------------------------------
FROM tomcat:9

# Install custom server.xml
# (URIEncoding=utf-8)
COPY docker/server.xml /usr/local/tomcat/conf/

# Copy the configuration file
COPY docker/blacklab-server.yaml /etc/blacklab/

# Create directory for formats
RUN mkdir /etc/blacklab/formats

# Copy the WAR file
COPY --from=builder /app/server/target/blacklab-server-*.war /usr/local/tomcat/webapps/blacklab-server.war

# Copy JARs for the commandline tools
COPY --from=builder /app/core/target/blacklab-*.jar /app/core/target/lib /usr/local/lib/blacklab-tools/

# Our data directory (either an anonymous volume or a named volume mounted by user)
VOLUME /data
