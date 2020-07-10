# Features

BlackLab features include:

-   **Index annotated text**, so you can search for specific lemmas or parts of speech. For example, you could search for all verb forms starting with “a”.
-   **Fast and scalable**: find complex patterns in large corpora (a billion words or more) in seconds.
-   **Search for regular-expression-like patterns**. Like being able to search for one or more adjectives, followed by the word “cow”, followed within 3 words by a form of the verb “to walk”. [Read more](query-tool.html) about this.
-   **Multiple input formats**: whether your texts are in TEI, Alto, FoLiA or the Sketch Engine format, it's easy to get them into a BlackLab index. And if your format isn't supported yet, [adding support is easy](how-to-configure-indexing.html). If you do have trouble, we're always happy to help.
-   **Multiple query languages**: you can use the powerful [Corpus Query Language](corpus-query-language.html), or [Lucene's own query parser](http://lucene.apache.org/core/2_9_4/queryparsersyntax.html). There's also (very basic) experimental support for [SRU CQL](http://zing.z3950.org/cql/intro.html "http://zing.z3950.org/cql/intro.html"). Adding another query language isn't hard to do.
-   **Easy to use**: the API was carefully designed according to the 'principle of least surprise'. Have a look at the example program given on [this page](getting-started.html).
-   **Search within XML tags** occurring in a text. For example, if your text is tagged with <ne\> tags around named entities (people, organisations, locations), BlackLab allows you to search for named entities occurring in the text that contain the word “city”. Or you can find words at the beginning or end of a sentence.
-   **Fast grouping and sorting** of large result sets on several criteria, including context (hit text, left context of hit, right context of hit). For example, you can group results by the word occurring to the right of the matched word(s).
-   **Accurate highlighting** of hits in a document and fast KWIC (keyword in context) view of hits.
-   **Active open source project** written in Java, based on Apache Lucene. Almost 300 commits in the past year. Many [future plans](roadmap.html).

[Get started with BlackLab](getting-starting.html)

[Who uses BlackLab?](who-uses-blacklab.html)
