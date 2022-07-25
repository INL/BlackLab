## What is BlackLab?

BlackLab is a corpus search engine built on top of [Apache Lucene](http://lucene.apache.org/).

::: details <b>What is a corpus search engine?</b>
A corpus search engine allows you to search through large bodies of annotated text. Each word can have a number of annotations such as headword, part of speech, etc. You can search for all of these annotations and look for specific patterns.

For example, the word *chickens* would be tagged with the headword *chicken* and the part of speech *(plural) noun*.

An example of a query could be: find adjectives occurring before the headword *chicken*. This might find matches like "small chicken" or "black spotted chickens". Of course, much more complex queries can be crafted as well.
 
You may also have annotations on groups of words; for example, named entities like *Albert Einstein* or *The Eiffel Tower*. Also, paragraphs and sentences may be tagged. You can incorporate all these annotations in your queries as well.
 
Even if your corpus does not include annotations, you can still benefit from other features that a corpus engine provides, such as sorting hits by the word before the hit, or grouping on the matched text.
:::



BlackLab was designed primarily for linguists, but it is also being used for other purposes, including historical research and artifical intelligence.

BlackLab is available as a web service (BlackLab Server), making it easy to use it from any programming language, as well as a Java library (BlackLab Core).

BlackLab was developed at the [Dutch Language Institute](https://ivdnt.org). It is free and open source software (Apache License 2.0).


## Features

BlackLab features include:

- **Index annotated text**, so you can search for specific lemmas or parts of speech. For example, you could search for all verb forms starting with “a”.
- **Fast and scalable**: find complex patterns in large corpora (a billion words or more) in seconds.
- **Search for regular-expression-like patterns**. Like being able to search for one or more adjectives, followed by the word “cow”, followed within 3 words by a form of the verb “to walk”. [Read more](/development/query-tool.md) about this.
- **Multiple input formats**: whether your texts are in TEI, Alto, FoLiA or the Sketch Engine format, it's easy to get them into a BlackLab index. And if your format isn't supported yet, [adding support is easy](how-to-configure-indexing.md). If you do have trouble, we're always happy to help.
- **Multiple query languages**: you can use the powerful [Corpus Query Language](/guide/corpus-query-language.md), or [Lucene's own query parser](http://lucene.apache.org/core/2_9_4/queryparsersyntax.html). There's also (very basic) experimental support for [SRU CQL](http://zing.z3950.org/cql/intro.html "http://zing.z3950.org/cql/intro.html"). Adding another query language isn't hard to do.
- **Easy to use**: the API was carefully designed according to the 'principle of least surprise'. Have a look at the example program given on [this page](getting-started.md).
- **Search within XML tags** occurring in a text. For example, if your text is tagged with <ne\> tags around named entities (people, organisations, locations), BlackLab allows you to search for named entities occurring in thtext that contain the word “city”. Or you can find words at the beginning or end of a sentence.
- **Fast grouping and sorting** of large result sets on several criteria, including context (hit text, left context of hit, right context of hit). For example, you can group results by the word occurring to the right of the matched word(s).
- **Accurate highlighting** of hits in a document and fast KWIC (keyword in context) view of hits.
- **Active open source project** written in Java, based on Apache Lucene. Almost 300 commits in the past year. Many [future plans](future-plans.md).


## Try it online

For a quick example of the BlackLab Frontend web application, have a look at either of these:

- [Brieven als Buit](https://brievenalsbuit.ivdnt.org/) ("Letters as Loot"), where you can search a collection of historical letters to and from sailors from the 17th to the 19th century
- [Corpus Gysseling](https://corpusgysseling.ivdnt.org/), a small corpus of historic Dutch (1200-1300)

If you have a [CLARIN](https://clarin.eu/) account, you might also want to check out:

- [Corpus Hedendaags Nederlands](https://chn.ivdnt.org/)
- [OpenSonar](https://opensonar.ivdnt.org/)

Here are a few searches you can try:

- **Lemma: "koe"** Finds all forms of the word "koe" (cow)<br/>
  Other words to try: "wet" (law), "zien" (to see), "groot" (large)
- **POS: "NOU\*"** Find all nouns<br/>
  Other values to try: "VRB\*" (verbs), "ADJ\*" (adjectives)
- **Word form: "coe"** Find a specific historic spelling of "koe"

Please note that this is just a small sample of the capabilities of BlackLab.


## Quick Start

If you want to try out BlackLab on your own machine, in preparation for indexing your own data, read on.

There's two ways to quickly try BlackLab. The easiest is probably using Docker, but Tomcat isn't much more difficult.

    
### Using Docker

TODO

### Using Tomcat

### Test Index

### Search your corpus

### Advanced: Corpus Query Language

