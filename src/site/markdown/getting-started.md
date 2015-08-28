# Getting Started

If you just want to see an example of what you can do with BlackLab, see [here](blacklab-in-action.html).

To start searching your own data, we'll need to:

- Choose your flavour of BlackLab (the web service BlackLab Server or the Java library BlackLab Core) and download it
- Convert your data to a supported format (or use our test data for now) and index it
- Write a simple search application

## Server or Core?

The web service, BlackLab Server, can be used from any programming language and offers a simple REST interface. The Java library offers the most flexibility, but it does mean you have to use a language that runs on the JVM (e.g. Java, Scala, Groovy, etc.).

For now, this guide will focus on BlackLab Core and Java, but we'll expand it to work with BlackLab Server and other programming languages soon. For more information on BlackLab Server, see the [GitHub project](https://github.com/INL/BlackLab-server).

First you need to get the BlackLab library JAR, either by downloading a prebuilt binary, or by building it yourself (it's easy, promise!).

## Downloading a prebuilt binary

A prebuilt binary JAR for BlackLab is available from the [Downloads](downloads.html) page. Note that this may not include the most recent improvements.

**NOTE**: we are working towards releasing BlackLab to Maven Central, so you can simply add it to your project configuration file and have it be automatically downloaded when you build your project.

## Building from source

If you want the very latest version of BlackLab, you should build it from source code.

### Getting the source code

First, you need to download the source code from GitHub. You can download it from there in a .zip file, but a better way to get it is by cloning it using Git. [Install a Git client](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) (we'll give command line examples here, but it should translate easily to GUI clients like TortoiseGit), change to a directory where you keep your projects, and clone BlackLab:

	git clone git://github.com/INL/BlackLab.git

Git will download the project and place it in a subdirectory "BlackLab".

### Building the library

BlackLab is built using [Maven](http://maven.apache.org/), a popular Java build tool. [Install Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html), change into the BlackLab directory, and build the library:

	mvn install

("install" refers to the fact that the library is "installed" to your private Maven repository after it is built)

After a lot of text output, it should say "BUILD SUCCESS" and the BlackLab library should be under target/BlackLab-VERSION.jar (where VERSION is the current BlackLab version, i.e. "1.2-SNAPSHOT")

## Preparing your data

In order to search your data using BlackLab, it needs to be in a supported format, and it needs to be indexed by BlackLab.

### Supported formats

BlackLab supports a number of input formats, but the most well-known are [TEI](http://www.tei-c.org/index.xml) (Text Encoding Initiative) and [FoLiA](http://proycon.github.io/folia/) (Format for Linguistic Annotation). These are both XML formats. If your data is not already in one of the supported formats, you need to convert it. (see the next section if you want to use a test dataset instead of your own)

One way to convert your data is using our tool [OpenConvert](https://github.com/INL/OpenConvert), which can generate TEI or FoLiA from txt, doc(x) or html files, among others. After conversion, you can tag the files using a tool such as <a href='http://ilk.uvt.nl/frog/'>Frog</a>. 

A [web-based user interface](http://openconvert.clarin.inl.nl/) for converting and tagging (Dutch) input files is available. You will need a CLARIN.eu account, which can be obtained [here](https://user.clarin.eu/user) ([more information](https://www.clarin.eu/content/clarin-identity-provider)).

### Testing with the Brown corpus

If you can't use your own data yet, we've provided an [annotated TEI version of the Brown corpus](https://github.com/INL/BlackLab/wiki/brownCorpus.lemmatized.xml.zip) for you to test with. 

The [Brown corpus](http://en.wikipedia.org/wiki/Brown_Corpus "http://en.wikipedia.org/wiki/Brown_Corpus") is a corpus compiled in the 1960s by [Nelson Francis and Henry Kucera](http://archive.org/details/BrownCorpus) at Brown University. It is small by today's standard (500 documents, 1M words). It was converted to TEI format by [Lou Burnard](http://users.ox.ac.uk/~lou/). It is available from archive.org under the [CC-BY-NC 3.0](http://creativecommons.org/licenses/by-nc/3.0/) license, but we've created our own version which includes lemmata. (Please note that we didn't check the lemmatization, and it probably contains errors - useful for testing purposes only!)

### Indexing data

Start the IndexTool without parameters for help information:

	java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool
 
(replace BLACKLAB_JAR with the path to your BlackLab JAR, e.g. "target/blacklab*.jar")

We want to create a new index, so we need to supply an index directory, input file(s) and an input format:

	java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool create INDEX_DIR INPUT_FILES FORMAT

If you specify a directory as the INPUT_FILES, it will be scanned recursively. You can also specify a file glob (such as \*.xml) or a single file. If you specify a .zip or .tar.gz file, BlackLab will automatically index the contents.

For example, if you have TEI data in /tmp/my-tei/ and want to create an index as a subdirectory of the current directory called "test-index", run the following command:

	java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool create test-index /tmp/my-tei/ tei

Your data is indexed and placed in a new BlackLab index in the "test-index" directory.

See also:

- [Adding a new input format](add-input-format.html) (if your format isn't supported yet and you don't want to convert)

### Testing your index

BlackLab Core includes a very basic command-based query tool useful for testing and debugging. To query the index you just created using this tool, type:

	java -cp BLACKLAB_JAR nl.inl.blacklab.tools.QueryTool test-index

The query tool supports several query languages, but it will start in CorpusQL mode. A few hints:

- Enclose each word between double quotes: "the" "egg" searches for "the" followed by "egg"
- You can user regular expressions: ".*g" searches for words ending with 'g'
- If you want to get more of a feel for what kinds of matches were found, try grouping by matched text using the command "group match". Then, if you want to view one of the groups, use "group *n*".

Type "help" to see a list of commands.

See also:

- [Using the query tool](query-tool.html)
- [Corpus Query Language](corpus-query-language.html)

## Anatomy of a BlackLab application

Finally, let's look at an example Java application.

Here’s the basic structure of a BlackLab search application, to give you an idea of where to look in the source code and documentation (note that we leave nl.inl.blacklab out of the package names for brevity):

1.  Call Searcher.open() to instantiate a search.Searcher object. This provides the main BlackLab API.
2.  Construct a search.TextPattern structure that represents your query. You may want to do this from a query parser, or use one of the query parsers supplied with BlackLab (queryParser.corpusql.CorpusQueryLanguageParser, …).
3.  Call the Searcher.find() method to execute the TextPattern and return a search.Hits object. (Internally, this translates the TextPattern into a Lucene SpanQuery, executes it, and collects the hits. Each of these steps may also be done manually if you wish to have more control over the process)
4.  Sort or group the results, using Hits.sort() or the search.grouping.ResultsGrouper class and a search.grouping.HitProperty object to indicate the sorting/grouping criteria.
5.  Select a few of your Hits to display by wrapping them in a search.HitsWindow.
6.  Loop over the HitsWindow and display each hit.
7.  Close the Searcher object.

The above in code:

	// Open your index
	Searcher searcher = Searcher.open(new File("/home/zwets/testindex"));
	try {
	    // NOTE: we use single quotes here to keep the example readable,
	    // but CQL only uses double quotes, so we do a replace.
	    String corpusQlQuery = "'the' [pos='adj.*'] 'brown' 'fox' ";
	    corpusQlQuery = corpusQlQuery.replaceAll("'", "\"");
	
	    // Parse your query to get a TextPattern
	    TextPattern pattern = CorpusQueryLanguageParser.parse(corpusQlQuery);
	
	    // Execute the TextPattern
	    Hits hits = searcher.find(pattern);
	
	    // Sort the hits by the words to the left of the matched text
	    HitProperty sortProperty = new HitPropertyLeftContext(searcher);
	    hits.sort(sortProperty);
	
	    // Limit the results to the ones we want to show now (i.e. the first page)
	    HitsWindow window = hits.window(0, 20);
	
	    // Iterate over window and display the hits
	    for (Hit hit: window) {
	        Concordance conc = hits.getConcordance(hit);
	        // Strip out XML tags for display.
	        String left = XmlUtil.xmlToPlainText(conc.left);
	        String hitText = XmlUtil.xmlToPlainText(conc.hit);
	        String right = XmlUtil.xmlToPlainText(conc.right);
	        System.out.printf("%45s[%s]%s\n", left, hitText, right);
	    }
	
	} finally {
	    searcher.close();
	}

See also:

- [API reference](apidocs/index.html)
