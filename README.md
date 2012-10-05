What is BlackLab?
=================
BlackLab is a Java search library that extends Apache Lucene. It was developed at INL to provide a fast and featureful search interface on our text corpora.

Right now, it powers the [IMPACT](http://www.impact-project.eu/) retrieval demonstrator and Corpus Gysseling online (developed for [CLARIN Search & Develop](http://www.clarin.eu/)).

BlackLab is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).


See it in action
================
Corpus Gysseling, a small corpus of historic Dutch (1200-1300), is our first publicly available application (still in beta) using BlackLab: http://tinyurl.com/gysseling

This simple application showcases some features of BlackLab. Here are a few searches you can try:

* Lemma: “koe”        Finds all forms of the word “koe” (cow)
  Other words to try: “wet” (law), “zien” (to see), “groot” (large)
* POS: “NOU*”     Find all nouns
  Other values to try: “VRB*” (verbs), “ADJ*” (adjectives)
* Word form: “coe”    Find a specific historic spelling of “koe”

You can also change the operator used to combine search clauses (And/Or), or add filter settings in the box below the search fields (click ‘Show’).

To experiment with more of BlackLab’s features, please run the QueryTool commandline demo (described in a separate document).


Who is BlackLab for?
====================
BlackLab is a good choice if you want to search a large body of text, possibly annotated with extra information per word (e.g. lemma, part of speech). It adds a number of search features to Lucene.

With BlackLab, you can search for complex patterns of words (e.g. “find all nouns preceded by two or three adjectives”). It can accurately highlight matches (not just simple terms) in the original document or show them in a keyword-in-context (KWIC) view. It can quickly sort or group large result sets based on several criteria, including the exact words matched or words surrounding the match. It can also search inside specific XML tags, so you can search for people or places, for example.

BlackLab supports a few XML input formats right now, including [ALTO](http://www.loc.gov/standards/alto/) and [TEI](http://www.tei-c.org/index.xml). Adding support for a new input format isn’t that hard. Right now, you have to write a single class to support a new format. In the future, we’d like to make it even easier: just write one configuration file.


Is it easy to use?
==================
Yes! Ease of use was one of the design goals. The simplest program is 3 lines long: open a BlackLab index, execute a query, and close the index again.

Of course, you might want to iterate over the results and display them, so a real program will be slightly longer, but trust us, it’s not rocket science. :-)

Queries can be supplied in many different forms, depending on what you’re familiar with:

* Lucene’s own query language;
* Corpus Query Language, as used by the [Sketch Engine](http://www.sketchengine.co.uk/);
* [Contextual Query Language](http://www.loc.gov/standards/sru/specs/cql.html), used by many online information retrieval systems;
* or if you prefer, you can programmatically construct queries out of objects.

You can also add your own query language if you want. All you need is a parser for your language.


Is it fast?
===========
Yes, we made sure the features we added don’t compromise Lucene’s impressive search speed. Of course, search and index speed varies based on machine and disk speed and available memory, but here are a few examples, from on a reasonably fast machine with 32GB RAM.

Search speed
------------
Here’s a rough indication of current search performance in a corpus with 450M words:

* Most queries will yield the first batch of results in one or two seconds, even when there’s hundreds of thousands of matches. Some types of wildcard queries take a few seconds longer.
* Sorting a resultset of 100K matches by document title or date takes about 2 seconds.
* Sorting the same resultset by the words occurring to the right of the match takes about 6 seconds.
* Grouping a resultset of 800K matches of a wildcard query on matched text (to see how often different words were matched) takes about 10 seconds.

Index speed
-----------
In addition to the factors mentioned above, indexing speed also depends on the input format used. Here’s two examples:
* The 450M word data set mentioned (consisting of OCR’ed text pages) was indexed in around 6 hours (around 20,000 words/sec)
* A 100M word data set in a more compact input format (word-per-line), including headword and part-of-speech tagging, was indexed in under an hour (around 30,000 words/sec; )


Can I use BlackLab with Solr?
=============================
Probably yes, but we need more information! At INL, we don’t use Solr, opting instead to work with Lucene directly. But we have looked at Solr and tried to design BlackLab such that the two could conceivably be used together. We haven’t had a chance to test this, however.

If you’re using Solr and are interested in taking advantage of the features that BlackLab provides, drop us a line (see below).


Future plans
============
We intend to keep improving BlackLab. Here are a some things we intend to work on:

* Make adding new input formats easier. It’s not that hard right now if you adapt one of the existing DocIndexer classes, but it could be better.
* Add more advanced search features, like finding a word at the beginning of a sentence, or a search within the matches of a previous search.
* Further improve search speed and reduce the amount of memory required.
* Experiment with distributed indexing/searching, to search multiple corpora with one query or speed up searching/sorting for a single corpus.
* Add the ability to dynamically update documents in the index, so you don’t have to re-index the entire set if one document changes.


Questions?
==========
For technical questions about BlackLab, contact Jan Niestadt ([jan.niestadt@inl.nl](mailto:jan.niestadt@inl.nl)).

Always happy to chat with fellow search geeks! :-)
