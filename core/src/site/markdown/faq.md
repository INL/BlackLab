# Frequently Asked Questions


Who is BlackLab for?
--------------------

BlackLab is a good choice if you want to search a large body of text annotated with extra information per word (e.g. lemma, part of speech, or any number of additional layers). It adds a number of search features to Lucene.

With BlackLab, you can search for complex patterns of words (e.g. “find all nouns preceded by two or three adjectives”). It can accurately highlight matches (not just simple terms) in the original document or show them in a keyword-in-context (KWIC) view. It can quickly sort or group large result sets based on several criteria, including the exact words matched or words surrounding the match. It can also search inside specific XML tags, so you can search for people or places, for example.

BlackLab supports a few XML input formats right now, including [TEI](http://www.tei-c.org/), [ALTO](http://www.loc.gov/standards/alto/) and [FoLiA](http://proycon.github.io/folia/). Adding support for a new input format [is easy](add-input-format.html).

Who uses it?
------------

See [Who uses BlackLab?](who-uses-blacklab.html).

Is it easy to use?
------------------

Yes, ease of use is an important design goal of BlackLab. This goes for both the Java library (BlackLab Core) and the webservice (BlackLab Server).

The simplest program using the Java library is 3 lines long: open a BlackLab index, execute a query, and close the index again. Of course, you might want to iterate over the results and display them, so a real-world program will be longer.

Queries can be supplied in many different forms, depending on what you’re familiar with:

-   [Corpus Query Language](corpus-query-language.html), as used by the Sketch Engine and IMS Corpus WorkBench;
-   [Contextual Query Language](http://www.loc.gov/standards/sru/specs/cql.html) (EXPERIMENTAL), used by many online information retrieval systems;
-   or if you prefer, you can programmatically construct queries out of objects, so you can add your own query languages, offer a GUI query builder, etc.

See [Getting started](getting-started.html) to get your feet wet with BlackLab. See the [reference documentation](apidocs/) for a detailed overview of the Java library, or [BlackLab Server overview](blacklab-server-overview.html) for more information about the webservice.

If you have questions, contact us (see below)!

Is it fast?
-----------

Yes, we made sure the features we added don’t compromise Lucene’s impressive search speed. Of course, search and index speed varies based on machine and disk speed and available memory, but here are a few examples, from on a reasonably fast machine with 32GB RAM.

### Search speed

Here’s a rough indication of current search performance in a corpus with 450M words:

-   Most queries will yield the first batch of results in under a second, even when there’s hundreds of thousands of matches. Some types of wildcard queries take a few seconds longer.
-   Sorting a resultset of 100K matches by document title or date takes about 2 seconds.
-   Sorting the same resultset by the words occurring to the right of the match takes about 4 seconds.
-   Grouping a resultset of 800K matches of a wildcard query on matched text (to see how often different words were matched) takes about 8 seconds.

Note that search performance is heavily reliant on disk caching (all the forward indices must be in OS cache to achieve maximum search, sort and group performance), so make sure you have plenty of memory.

### Index speed

In addition to the factors mentioned above, indexing speed also depends on the input format used. Here’s two examples:

-   The 450M word data set mentioned (consisting of OCR’ed text pages) was indexed in around 6 hours (around 20,000 words/sec)
-   A 100M word data set in a more compact input format (word-per-line), including headword and part-of-speech tagging, was indexed in under an hour (around 30,000 words/sec; )

Can I use BlackLab with Solr/ElasticSearch?
-------------------------------------------

Solr/ElasticSearch integration is high on our [wishlist](roadmap.html), but BlackLab started as a Java library using Lucene directly, and some changes are required to get it to integrate with Solr and/or ElasticSearch. We know the steps required, it's just a question of finding the time. 

If you’re using Solr/ElasticSearch and are interested in taking advantage of the features that BlackLab provides, drop us a line (see below). We'd love to collaborate on this.

What do I do if BlackLab (Server) is slow and uses 100% CPU?
------------------------------------------------------------
Usually this is memory-related. Make sure the JVM has enough heap space. If heap memory is low and/or fragmented, the JVM garbage collector might start taking 100% CPU moving objects in order to recover enough free space, slowing things down to a crawl. See [http://crunchify.com/how-to-change-jvm-heap-setting-xms-xmx-of-tomcat/ here].

Future plans
------------
We intend to keep improving BlackLab. For an overview of our future plans, check the [Road map](roadmap.html).

More questions?
---------------
For technical questions about BlackLab, contact [Jan Niestadt](mailto:jan.niestadt@ivdnt.org).

I'm always happy to chat with fellow search geeks!
