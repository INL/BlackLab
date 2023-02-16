# BARK 5 - Relationship with Lucene

- **type:** information
- **status:** active

How BlackLab uses Lucene and why staying up to date is important.

## BlackLab and Lucene

Lucene provides a fast reverse index, which BlackLab uses to search. BlackLab extends Lucene mostly by adding a number of `SpanQuery` classes that provide specific search constructs needed to resolve Corpus Query Language queries. It also adds a forward index that is used to speed up certain operations.

Improvements in Lucene's search speed or resource usage tend to translate directly to improvements for BlackLab as well. One example was Lucene 4.x, which greatly sped up certain wildcard searches. This is one reason to upgrade to newer Lucene versions.

Another is bugfixes and other improvements. Staying on old, unsupported versions of Lucene is not a good idea. We will try to reserve time to regularly update to newer Lucene versions for this reason. Pull requests are always appreciated.

When we enable Solr integration (see [BARK 7](bark007-solr-integration.md)), we will also want to stay on a recent Solr version, which implies also upgrading Lucene.

When upgrading to a new Lucene version breaks compatibility, we will only do so for a major version of BlackLab.


## Impact on users

Updating to a new Lucene version may break compatibility with older indexes. For example, when updating from Lucene 5 to 8, that broke compatibility with all existing indexes. Lucene's backward compatibility policy is to support indexes of the last two major versions.

Upgrading Lucene from version 8 to 9 should not break compatibility with Lucene 8 (BlackLab 3/4) indexes.
