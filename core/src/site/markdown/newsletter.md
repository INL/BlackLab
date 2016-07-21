**NOTE:** if you want to receive an email alert for this newsletter, drop me a line at [jan.niestadt@inl.nl](mailto:jan.niestadt@inl.nl).

**(September 2013)**

Hi everyone! More and more people are (thinking of) using BlackLab, and I wanted to keep you all up to date. Hence this newsletter with a quick overview of the latest BlackLab developments.

Letters as Loot: a glimpse into the past
----------------------------------------

The INL has put a new BlackLab-based application online: "Letters as Loot"!

Here you can search a corpus consisting of approximately 40,000 Dutch letters from the 17th-19th centuries, sent home by sailors to keep in touch with their loved ones. A fascinating collection and priceless material for historical linguists. [Read more about the project](http://brievenalsbuit.inl.nl/zeebrieven/page/project "http://brievenalsbuit.inl.nl/zeebrieven/page/project") or [start searching the letters](http://brievenalsbuit.inl.nl/zeebrieven/page/search "http://brievenalsbuit.inl.nl/zeebrieven/page/search") (a few suggested lemma searches: "schip" (ship), "zee" (sea), "lief" (sweet)).

Large snippets: more context, please
------------------------------------

KWIC (keyword in context) views are nice, but they don’t show a lot of the document, so it can be tricky to understand the context of a hit. It would be nice if users could click on a hit to see a bit more context around it. Say you want to show them 50 words before and after the hit when they click on it. You can easily do this now using Hits.getConcordance(Hit, int). This retrieves a single concordance with a larger context size. See it in action [here](http://brievenalsbuit.inl.nl/zeebrieven/page/search "http://brievenalsbuit.inl.nl/zeebrieven/page/search"): search for lemma 'schip' (ship) and click on one of the hits for more context.

Contextual Query Language: search like a librarian
--------------------------------------------------

We've had a version of this running for a while, but now it has been integrated into BlackLab: support for the Contextual Query Language. This language is used by many libraries and other institutions across the globe. Support in BlackLab is basic but functional. Have a look at it (try it out in the [QueryTool](http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/ "http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/"), for example) and see if it suits your needs. If not, let me know (see below) and we can work out how to improve it together.

Input formats: TEI, FoLiA, Alto or add your own?
------------------------------------------------

We've also had code for indexing formats like TEI and FoLiA, but it wasn't yet part of BlackLab. Now this code has been restructured and added to the project. Both TEI P4 and P5 are supported. Please note that these indexers are relatively simple, so if you want to make use of the more advanced features of these formats, those may not be supported yet.

However, adding or improving support for input formats has become much easier. Before, you had to really dive into the inner workings of BlackLab to fully grasp how to index a new file format. Now, you just have to write a few simple handlers for element types and you’re good to go. Default handlers are supplied and you can look at the supplied indexers to see how to achieve specific things. The [introductory guide](/Add_a_new_input_format "Add a new input format") to writing an indexer should also be helpful. If you have any questions, contact me (see below).

OpenSoNaR & WhiteLab
--------------------

The recently approved OpenSoNaR project will use BlackLab to provide end users with a search interface on the SoNaR-500 reference corpus of contemporary written Dutch. The front-end will be called WhiteLab and will eventually be open sourced. OpenSoNaR is a project of the University of Tilburg and INL. More information about OpenSoNaR can be found in the [CLARIN Annual Report 2012](http://www.clarin.nl/sites/default/files/CLARIN%20NL%20Annual%20Report%202012%20130429.pdf "http://www.clarin.nl/sites/default/files/CLARIN%20NL%20Annual%20Report%202012%20130429.pdf").

Performance: search in a snap
-----------------------------

I'm happy to report that BlackLab performance has improved in several areas.

Large BlackLab indices used to take a long time to open. This has been much improved. In addition, there’s now an option to automatically “warm up” the forward indices (i.e. prime the disk cache) in a background thread on startup. Enable this by calling Searcher.setAutoWarmForwardIndices(true); before constructing a Searcher object.

Generating concordances (hits in context, for showing KWIC (keyword in context) views) has become much faster, because we generate them from the forward indices now instead of the content store. For this to work, we’ve added a new forward index called “punct” containing whitespace and punctuation – all characters that occur between two words. So if you want the improved speed, you should recreate your index to make sure you have this new forward index available. BlackLab automatically switches over to the fast way of making concordances when it detects this forward index.

Several types of queries (notably, phrase searches) have been sped up. Significant improvements were achieved by remembering properties of the resultsets of subqueries, such as whether or not the results are all of the same length. We use these extra properties to determine when certain operations (such as sorting) can be skipped.

IndexTool: add and update documents
-----------------------------------

Before, BlackLab included a test program with each of the included indexers. This was messy and didn’t allow for a lot of customization. Now, we’ve included a single, generic indexing tool for indexing data: the IndexTool. [Here's](/Building_and_testing_BlackLab#Wiki-Testing_the_library "Building and testing BlackLab") how to use it. You can also pass it "--help" to see more information. For example, it is possible to pass parameters to indexer classes to customize how you want to index your documents.

We’re working on making the BlackLab index fully updateable. It is already possible to delete documents and then add new versions of them, effectively updating the documents. The forward index is smart about reusing free space. (The content store may still get fragmented if you do a lot of deletions, but this will be improved in the future.) All this means that you don't have to reindex your whole dataset every time a few documents change.

QueryTool on the web
--------------------

The QueryTool is a useful debugging aid. It lets you try out BlackLab and test your index easily (if you’re comfortable using a command-driven interface, that is). Now, there’s a web-based version of it included in BlackLab (in the examples folder), so you don’t have to log in to a server if you just want to check something quickly. It’s also an interesting example of (one way of) building a BlackLab web application. In the future, we’ll add some more examples that you can base your own applications on. You can try out the web-based version [here](http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/ "http://gysseling.corpus.taalbanknederlands.inl.nl/QueryTool/").

Past, present and future
------------------------

Want to know about every recent improvement in BlackLab? Check out the [changelog](/Changelog "changelog") page. Or, if you'd rather look to the future, the [roadmap](/Roadmap "roadmap") gives a good overview of where we're headed.

If you have any questions about BlackLab, or suggestions of course, don't hesitate to contact me at **jan.niestadt@inl.nl**. Also, I will be at eLex 2013 in Tallinn in October, so if you're going there as well, we can meet to talk shop!
