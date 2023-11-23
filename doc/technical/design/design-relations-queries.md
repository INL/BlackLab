# Origins of BlackLab's relations search syntax

How did our relations search extension come about, and how does it relate to existing treebanks' query languages?

## Our goals

First and foremost, BlackLab is a token-based corpus search system. Its query language (BCQL) is a dialect of the de-facto standard CQP query language that was created for the IMS Corpus Workbench.

To add support for basic relations search to this token-based system, our goal was to create a superset of the existing query language, aiming to keep the syntax simple and user-friendly. The user should be able to query different types of relations with it, not just dependency relations. Relations shouldn't only be between single words, but it should also be possible to index and query relations between two spans of words.

While it would be nice to eventually offer full-fledged treebank querying, we decided to start with a simple set of operations that could feasibly be implemented in limited time and would cover common use cases -- the "low-hanging fruit", if you will.

## Research into treebank systems

In a brainstorm session with Leif-Jöran Olsson at Språkbanken in November 2017, we found and compared various treebank systems. (see the [notes from that session](https://docs.google.com/document/d/1x3ntLekAAg9P6lFMt69lEdrBuMC8NJ2Rw4h813m3I9A/edit#heading=h.b3bvjtyo6p3v)).

Since then we've looked further into these and other treebank systems at INT. Some of them are still actively maintained, some not. In no particular order:

- [TIGERSearch](https://www.ims.uni-stuttgart.de/forschung/ressourcen/werkzeuge/tigersearch/) (developed at the University of Stuttgart) has a query language inspired by CQP, although not quite a superset.
- [ICARUS](https://www.ims.uni-stuttgart.de/forschung/ressourcen/werkzeuge/icarus/) (again developed at the University of Stuttgart) also has a query language inspired by CQP, but uses nesting to represent tree fragments.
- [Icecup](https://www.ucl.ac.uk/english-usage/resources/icecup/) (developed at the University College London) features “search by example”.
- [GrETEL](https://gretel.hum.uu.nl/ng/home) (developed at the universities of Leuven and Utrecht, with contributions by INT), uses XPath as a query language, and also features search-by-example.
- [INESS Search](https://clarino.uib.no/iness/page?page-id=INESS_Search) (developed at University of Bergen) is a reimplementation of TIGERSearch, with a similar but slightly different query language.
- [TGrep](https://web.stanford.edu/dept/linguistics/corpora/cas-tut-tgrep.html) (developed at Stanford University) also uses a query language very similar to TIGERSearch.
- [MonaSearch](https://dspace.library.uu.nl/handle/1874/296796) (developed at the University of Tübingen) features a very advanced query language based on monadic second-order logic.
- [Fangorn](https://code.google.com/archive/p/fangorn/wikis/Query_Language.wiki) (developed at the universities of Melbourne and Pennsylvania) uses a query language that has similarities to XPath.

We can see that Corpus Query Language has (directly or indirectly) inspired many of the treebank query languages, mostly via TIGERSearch. XPath is also represented, and search by example is also (understandably) a popular approach (that can be, but isn't always, offered in combination with a query language).

However, none of the existing query languages represent a superset of CQP. This makes sense, as most treebank systems focus exclusively on searching tree structures, not on token-based querying like BlackLab does.

The nested syntax that ICARUS uses looks nice, but causes issues when a relation is between two spans of words: in that case, we cannot easily use nesting in a CQP dialect, because of its token-based nature. This issue doesn't crop up in ICARUS because the `[ ... ]` syntax represents a node in the tree as opposed to a single token.

## Rolling our own

We eventually concluded that we needed to extend BCQL with our own relations search operators. We decided on using arrows `-->` to symbolically represent a directional relation. It also allowed us to incorporate a filter on the relation type, with the option of using a regular expression filter (e.g. `-nsubj|nobj->`).

With this syntax, simple queries are readable and look quite familiar, although they do get a bit verbose as complexity increases. We don't think this is a big problem as most users will probably prefer using a visual query builder, which we intend to build. The query syntax will still be useful for diagnosing problems, providing programmatic access and other advanced use cases.

It should be noted that our relations search extensions are still very limited compared to the above treebanks search systems. For example, there are no generic "is an ancestor/descendant of" operators yet. Offering full treebank functionality would be significantly more work, but there's no reason the current syntax couldn't be expanded on in the future, though.

## Summary and conclusion

After some research we decided that this custom solution would best fit our specific requirements. It is readable, integrates well with token-based querying, but it's still limited in capability compared to full treebank systems.

If anyone working on similar corpus search systems is interested, it would be useful to discuss the pros and cons of our approach versus others. Perhaps we could eventually arrive at a de-facto standard for relations search in CQP dialects.
