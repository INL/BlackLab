# BARK 8 - Distributed search

- **type:** change
- **status:** in development

Distributed search allows us to better scale to huge corpora and many users.

## Why?

Because we will get a lot more data (billions of tokens) and want all of it to be simultaneously searchable, ideally by many users at the same time as well. Also we want to be flexible in scaling up and down according to demand.

A potential side benefit is that sharding (on a single machine or multiple machines), i.e. breaking indexes into multiple "subindexes" that can be searched as one large index, can allow us to re-index only that shard, which is significantly quicker than re-indexing the entire index and easier/quicker than deleting and re-adding documents.

## How?

Using Solr's distributed search, after integrating BlackLab with Solr (see [BARK 7](bark007-solr-integration.md)).

## When?

Work on this has started in 2022. It will likely be finished in 2024.

## Impact on users

Distributed search will be an option, not a requirement. Solr can also be run standalone, and classic BlackLab Server will be supported for the foreseeable future.
