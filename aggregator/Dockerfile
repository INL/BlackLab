# Stage "builder": build the WAR file
#--------------------------------------
FROM maven:3.6-jdk-11 AS builder

# Copy source
WORKDIR /app
COPY . .

# Build the WAR.
# NOTE: make sure BuildKit is enabled (see https://docs.docker.com/develop/develop-images/build_enhancements/)
#       to be able to cache Maven libs so they aren't re-downloaded every time you build the image
#RUN mvn --no-transfer-progress package
RUN --mount=type=cache,target=/root/.m2 mvn --no-transfer-progress package


# Tomcat container with the WAR file
#--------------------------------------
FROM tomcat:9

# Create config directory
RUN mkdir /etc/blacklab/

# Copy the WAR file
COPY --from=builder /app/aggregator/service/target/blacklab-aggregator.war /usr/local/tomcat/webapps/blacklab-server.war
