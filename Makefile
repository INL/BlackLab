# Makefile for BlackLab Docker images
#
# How to use:
#
# make build                     # Build images
# make push                      # Push images to Docker Hub
# make deploy CONTEXT=my-server  # Pull images on server and (re)deploy application


# Config variables
#----------------------------------------

# Our project name. This is important if our project directory has a different name,
# such as when calling from Jenkins. In that case, the --project-name option will automatically
# be added.
PROJECT_NAME = blacklab

# Name prefix for the Docker image(s)
IMAGE_NAME_PREFIX = instituutnederlandsetaal/blacklab



# Helper variables
#-------------------------------------------

# Helper to be able to have leading whitespace in variable...
EMPTY_STRING =

CURRENT_DIR = $(shell basename $(PWD))

# See if our directory name matches our project name.
# If not, pass --project-name to Compose so it always behaves the same way.
# (if we didn't do this, container names would vary based on the directory name)
opt-project-name:
ifneq ($(PROJECT_NAME),$(CURRENT_DIR))
  OPT_PROJECT_NAME = $(EMPTY_STRING) --project-name $(PROJECT_NAME)
endif
-include opt-project-name

# Pass CONTEXT=<name> to use that Docker context
ifdef CONTEXT
  CONTEXT_PARAM = $(EMPTY_STRING) --context $(CONTEXT)
endif

# Our standard commands for calling Docker and Compose
DOCKER_COMMAND  = docker$(CONTEXT_PARAM)
COMPOSE_NO_FILE = $(DOCKER_COMMAND) compose$(OPT_PROJECT_NAME)
COMPOSE_COMMAND      = $(COMPOSE_NO_FILE) -f docker-compose.yml
COMPOSE_COMMAND_DEV  = $(COMPOSE_NO_FILE)


# IMAGE_VERSION defined in our .env file (for tagging)
IMAGE_VERSION := $$(grep IMAGE_VERSION .env | cut -d "=" -f 2)

# Short hash of latest Git commit
GIT_COMMIT_HASH := $$(git log -1 --pretty=%h)

# Tag comprised of IMAGE_VERSION and short hash of latest Git commit
GIT_COMMIT_TAG := $(IMAGE_VERSION)-$(GIT_COMMIT_HASH)

# For build, push, etc.: apply to all profiles, not just default
ALL_PROFILES = $(EMPTY_STRING) --profile default --profile tools


# Application management targets
#-------------------------------------------

show-info:
	echo Git commit tag: $(GIT_COMMIT_TAG)
	echo current dir name: $(CURRENT_DIR)
	echo OPT_PROJECT_NAME:$(OPT_PROJECT_NAME)

# Stop application and remove containers
down:
	$(COMPOSE_COMMAND) down

# Build images
build:
	$(COMPOSE_COMMAND)$(ALL_PROFILES) build

# Build images (plain output for CI)
build-plain:
	$(COMPOSE_COMMAND)$(ALL_PROFILES) build --progress plain

# Tag images with most recent git commit hash and push to Docker Hub
push-commit:
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX):$(IMAGE_VERSION)        $(IMAGE_NAME_PREFIX):$(GIT_COMMIT_TAG)
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX)-solr:$(IMAGE_VERSION)   $(IMAGE_NAME_PREFIX)-solr:$(GIT_COMMIT_TAG)
	$(DOCKER_COMMAND) tag $(IMAGE_NAME_PREFIX)-proxy:$(IMAGE_VERSION)  $(IMAGE_NAME_PREFIX)-proxy:$(GIT_COMMIT_TAG)
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX):$(GIT_COMMIT_TAG)
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-solr:$(GIT_COMMIT_TAG)
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-proxy:$(GIT_COMMIT_TAG)

# Push images to Docker Hub
push:
	# Push configured version tags
	$(COMPOSE_COMMAND)$(ALL_PROFILES) push
    # Push latest tags as well
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX):latest
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-solr:latest
	$(DOCKER_COMMAND) push $(IMAGE_NAME_PREFIX)-proxy:latest

# Build images and push them to Docker Hub
release: build push
	:

# Pull images from Docker Hub
pull:
	$(COMPOSE_COMMAND)$(ALL_PROFILES) pull --quiet

# Stop application
stop:
	$(COMPOSE_COMMAND) stop

# Run application with local dev config
# (will build images only if missing)
dev:
	$(COMPOSE_COMMAND_DEV) up -d

# Restart application
restart:
	$(COMPOSE_COMMAND) restart

# Show application logs
logs:
	$(COMPOSE_COMMAND) logs

# Show and follow application logs
# (Ctrl+C to exit)
logs-f:
	$(COMPOSE_COMMAND) logs -f

# Start application
up:
	$(COMPOSE_COMMAND) up -d

# Deploy application to server (based on images from Docker Hub) with production config
# by pulling the newest versions of images, then using them to bring the application up
deploy: pull
	$(COMPOSE_COMMAND) up -d --no-build
