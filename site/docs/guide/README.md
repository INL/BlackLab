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

> **OLD SITE?**: for now, the old site is still [available](https://inl.github.io/BlackLab/old-site/) for reference (but contains mostly the same information).

## Features

BlackLab features include:

- **Index annotated text**, so you can search for specific lemmas or parts of speech. For example, you could search for all verb forms starting with “re” to find "researching", "replies", etc.
- **Easy to use**: both the REST API and the Java library were designed to be easy to use, and we do our best to provide good documentation.
- **Fast and scalable**: find complex patterns in large corpora (a billion words or more) in seconds.
- **Flexible data ingestion**: if your data is not in one of the built-in formats, getting them into BlackLab is a matter of writing a configuration file.
- **Search for regular-expression-like patterns**. Like being able to search for one or more adjectives followed by the word “cow”. (see [Corpus Query Language](corpus-query-language.md) to learn more)
- **Search within XML tags** to find named entities that contain the word "tower" (Eiffel Tower, Tower Bridge, etc.), or words at the beginning or end of a sentence.
- **Capture groups** so you can capture specific parts of matches.
- **Fast grouping and sorting** of large result sets on several criteria. For example, you can group results by the word occurring to the right of the matched word(s).
- **Accurate highlighting** of hits in a document and fast KWIC (keyword in context) view of hits.
- **Active open source project** in development since 2010, with many [plans for the future](future-plans.md).


## Try it online

For a quick example of the BlackLab Frontend web application, have a look at either of these:

- [Brieven als Buit](https://brievenalsbuit.ivdnt.org/) ("Letters as Loot"), where you can search a collection of historical letters to and from sailors from the 17th to the 19th century
- [Corpus Gysseling](https://corpusgysseling.ivdnt.org/), a small corpus of historic Dutch (1200-1300)

With a [free CLARIN account](https://idm.clarin.eu/unitygw/pub#!registration-CLARIN%20Identity%20Registration) account, you can also check out:

- [Corpus Hedendaags Nederlands](https://chn.ivdnt.org/)
- [OpenSonar](https://opensonar.ivdnt.org/)

Here are a few searches you can try (click on the _Extended_ tab):

- **Lemma: "koe"** Finds all forms of the word "koe" (cow)<br/>
  Other words to try: "wet" (law), "zien" (to see), "groot" (large)
- **Part of speech: "NOU-C"** Find all common nouns<br/>
  Other values to try: "VRB\*" (verbs), "ADJ\*" (adjectives)
- **Word: "coe"** Find a specific historic spelling of "koe"

This is just a small sample of the capabilities of BlackLab.

If you're excited about the possibilities and want to get BlackLab up and running yourself, move on to [Getting Started](getting-started.md).
