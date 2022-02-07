# What is BlackLab?

[BlackLab](http://inl.github.io/BlackLab/) is a corpus retrieval engine built on top of [Apache Lucene](http://lucene.apache.org/). It allows fast, complex searches with accurate hit highlighting on large, tagged and annotated, bodies of text. It was developed at the Institute of Dutch Lexicology (INL) to provide a fast and feature-rich search
interface on our historical and contemporary text corpora.

We're also working on BlackLab Server, a web service interface to BlackLab, so you can access it from any programming language. BlackLab Server is included in the repository as well.

BlackLab and BlackLab Server are licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

To learn how to index and search your data, see the [official project site](http://inl.github.io/BlackLab/).

Learn about [BlackLab's structure and internals](./core/src/site/markdown/blacklab-internals.md) (work in progress). 

## Changed: 'main' branch

The branch that corresponds to BlackLab's latest release is now called _main_ instead of _master_.

Local clones can either be removed and re-cloned, or you can rename the local branch with these commands:

```bash
git branch -m master main
git fetch origin
git branch -u origin/main main
git remote set-head origin -a
```

Please note that _dev_, not _main_, is the default branch. This is the development
branch, which should be considered unstable.

## Using BlackLab with Docker

An experimental Docker setup is provided now. It will likely change in the future.

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
