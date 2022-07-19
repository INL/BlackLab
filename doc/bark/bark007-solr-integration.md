# BARK 7 - Solr integration

- **type:** change
- **status:** in development

We will make it possible to use BlackLab with Solr, in addition to BlackLab Server.

## Why?

Solr provides a lot of extra functionality on top of Lucene, most importantly distributed search (see [BARK 8](bark008-distributed-search.md)), but also a huge ecosystem of potentially useful plugins. Some tasks currently handled by BlackLab Server could perhaps be handled by Solr's builtin functionality, e.g. filtering documents. This allows us to reduce the amount of code we have to maintain.

## How?

After creating the integrated index format (see [BARK 6](bark006-integrated-index.md)), we will add each of BlackLab Server's operations to Solr using a custom RequestHandler. We will refer to the [Mtas](https://github.com/meertensinstituut/mtas) project for an example of how to do this, but will take our own approach.

## Why do we favor Solr?

We are most familiar with Solr and Solr seems to support custom (distributed) operations well, and seems to have better documentation about its internals. The Mtas project mentioned above integrates with Solr already, so we can use this as reference.

ElasticSearch (ES) could also be an option, if that were found to have advantages. Some of the work to prepare for integrating with Solr would also be needed to integrate with ES, but the actual customizations to Solr/ES would be different. We can always decide to add ES integration in the future.

## When?

Work on this has started in 2022. It should be complete by the end of 2020.

## Impact on users

We're adding Solr integration as an option, in addition to standalone BlackLab Server. This should not affect existing users.

In a future version, we might consider deprecating and eventually dropping support for BlackLab Server, but there are no plans for that at this time.
