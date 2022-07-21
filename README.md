# Distributed Search (Proof of Concept)

This branch contains a proof of concept for distributed search. It is not intended to be merged.
If succesful, we will integrate BlackLab with Solr to enable distributed indexing and search.

See [aggregator/](aggregator/README.md) for details.

# What is BlackLab?

[BlackLab](https://inl.github.io/BlackLab/) is a corpus retrieval engine built on top of [Apache Lucene](http://lucene.apache.org/). It allows fast, complex searches with accurate hit highlighting on large, tagged and annotated, bodies of text. It was developed at the [Dutch Language Institute (INT)](https://ivdnt.org/) to provide a fast and feature-rich search interface on our contemporary and historical text corpora.

In addition to the Java library (BlackLab Core), there is also a web service (BlackLab Server), so you can access it from any programming language.

BlackLab is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

To learn how to index and search your data, see the [official project site](http://inl.github.io/BlackLab/).

Learn about [BlackLab's structure and internals](./core/src/site/markdown/blacklab-internals.md) (work in progress). 

## Branches

The default branch, **dev**, corresponds to the "bleeding edge" in-development version. You can certainly run it (we do), but if you need maximum stability, it might be better to stay on a stable release instead. 

The branch that corresponds to BlackLab's latest release is called **main**.

There are additional branches related to in-development features. These are intended to be short-lived and will be merged into dev.


## Compatibility: Java, Lucene

This version of BlackLab required Java 11 or higher. It has been tested up to Java 17.

This version uses Lucene 8. This unfortunately means that corpora created with older BlackLab versions (up to 2.3) cannot be read and will need to be re-indexed. If this is a problem for you, you can stick with the 2.3 version for now. We would like to eventually provide a conversion tool, but there is no date planned for this.


## Roadmap

There is a high-level [roadmap](https://inl.github.io/BlackLab/roadmap.html) page on the documentation site.

For the next major version (4.0), we are focused on integrating BlackLab with Solr, with the goal of enabling distributed search. We will use this to scale our largest corpus to many billions of tokens. Status and plans for this can be found [here](PLAN.md).


## Development workflow

We strive towards practicing Continuous Delivery.

Our intention is to:
- continuously improve both unit and integration tests (during development and whenever bugs are discovered)
- avoid long-lived feature branches but frequently merge to the dev branch
- use temporary feature flags to prevent issues with unfinished code
- deploy to our servers frequently


## Code style

Configurations for IDE code formatters can be found in the `build-tools/` directory: 
- `formatter-intellij.xml` for IntelliJ IDEA
- `formatter-eclipse.xml` for Eclipse


## Building the site

Here's how to build the [BlackLab site](https://inl.github.io/BlackLab) locally:

```bash
# Contains the configurations for various checking plugins shared by multiple modules
cd build-tools
mvn install

# Build the actual site (result will be in core/target/site)
cd ..
mvn site
```

## Using BlackLab with Docker

An experimental Docker setup is provided now. It works well, but details may change in the future. We will eventually publish an official Docker image release, which will then be available on [Docker Hub](https://hub.docker.com/r/instituutnederlandsetaal/blacklab).

A Docker version supporting [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) is required (18.09 or higher), as well as Docker Compose version 1.27.1 or higher. 

We assume here that you are familiar with the BlackLab indexing process; see [indexing with BlackLab](https://inl.github.io/BlackLab/indexing-with-blacklab.html) to learn more.

Create a file named `test.env` with your indexing configuration:

```ini
IMAGE_VERSION=latest
BLACKLAB_FORMATS_DIR=/path/to/my/formats
INDEX_NAME=my-index
INDEX_FORMAT=my-file-format
INDEX_INPUT_DIR=/path/to/my/input-files
JAVA_OPTS=-Xmx10G
```

To index your data:

```bash
docker-compose --env-file test.env run --rm indexer
```

Now start the server:

```bash
docker-compose up -d
```

Your index should now be accessible at http://localhost:8080/blacklab-server/my-index.


See the [Docker README](docker/README.md) for more details.

## Special thanks

* ej-technologies for the <a href="https://www.ej-technologies.com/products/jprofiler/overview.html">JProfiler Java profiler</a>
