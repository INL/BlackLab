# Getting Started

There's [several examples online](blacklab-in-action.html) of what you can do with BlackLab.

To start searching your own data, you'll need to:

- [Choose between BlackLab Server and Core](#server-or-core)<br/>
- [Getting BlackLab](#getting-blacklab)<br/>
- [Prepare your data](#preparing-your-data)<br/>
- [Corpus search application](#corpus-search-application)<br/>
- [Write a simple application](#write-a-simple-blacklab-application)<br/>

Let's go over these one by one.

<a id="server-or-core"></a>

## Server or Core?

The web service, BlackLab Server, can be used from any programming language and offers a simple REST interface. The Java library offers the most flexibility, but it does mean you have to use a language that runs on the JVM (e.g. Java, Scala, Kotlin, etc.). Note that you will need a Java SE 8 compatible JVM to use the latest BlackLab versions.

For now, this guide will focus on BlackLab Core and Java. For more information on BlackLab Server, see this [overview](blacklab-server-overview.html).

First you need to get the BlackLab library. The simplest way is to let Maven download it automatically from the Central Repository, but you can also download a prebuilt binary, and it's trivial to build it yourself.

<blockquote>
<b>Note to MacOS users</b>: Dirk Roorda at DANS wrote a detailed guide for installing and indexing data on MacOS. It's available <a href='https://github.com/Dans-labs/clariah-gm/blob/master/blacklab/install.md'>here</a>.
</blockquote>

<a id="getting-blacklab"></a>

## Getting BlackLab

### Maven Central

BlackLab is in the Maven Central Repository, so you should be able to simply [add it to your build tool](dependency-info.html). If you're not sure what version to use, see the [downloads](downloads.html) or [changelog](changelog.html) pages.

### Downloading a prebuilt binary

BlackLab Core consists of a JAR and a set of required libraries. See the [GitHub releases page](https://github.com/INL/BlackLab/releases/) and choose a jar-with-libs download. The latter one may also contain development versions you can try out.

BlackLab Server only consists of a WAR file that includes everything. You could even unzip this WAR file to obtain the included BlackLab JAR and zip files if you needed to for some reason.

### Building from source

If you want the very latest version (the "dev" branch) of BlackLab, you can easily build it from source code.

First, you need to download the source code from GitHub. You can download it from there in a .zip file (be sure to select the dev branch before doing so), but a better way to get it is by cloning it using Git. [Install a Git client](https://git-scm.com/book/en/v2/Getting-Started-Installing-Git) (we'll give command line examples here, but it should translate easily to GUI clients like TortoiseGit), change to a directory where you keep your projects, and clone BlackLab:

	git clone git://github.com/INL/BlackLab.git

Git will download the project and place it in a subdirectory "BlackLab". Now switch to the dev branch:

    git checkout dev
    
Install a recent JDK (Java Development Kit). If you're on Linux, you can use your package manager to do this (OpenJDK is fine too). Note that you will need at least JDK version 8 (i.e. openjdk-1.8.0) to use the latest BlackLab versions.

BlackLab is built using [Maven](http://maven.apache.org/), a popular Java build tool. [Install Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html) (use your package manager if on Linux), change into the BlackLab directory, and build the library:

	mvn install

("install" refers to the fact that the library is "installed" to your private Maven repository after it is built)

After a lot of text output, it should say "BUILD SUCCESS" and the BlackLab JAR library should be under core/target/blacklab-VERSION.jar (where VERSION is the current BlackLab version, i.e. "1.7-SNAPSHOT"; SNAPSHOT means it's not an official release, by the way). The BlackLab Server WAR will be in server/target/blacklab-server-VERSION.war.

NOTE: If you want to use BlackLab Server and [BlackLab AutoSearch](https://github.com/INL/corpus-frontend/) (our search application), you'll need an application server like Apache Tomcat too. Also available via package manager in Linux. After installation, find the "webapps" directory (e.g. /var/lib/tomcat/webapps/, but may depend on distribution) and copy the WAR file to it. It should be extracted by Tomcat automatically. For full installation and configuration instructions, see [BlackLab Server overview](blacklab-server-overview.html).

<a id="preparing-your-data"></a>

## Preparing your data

In order to search your data using BlackLab, it needs to be in a supported format, and it needs to be indexed by BlackLab.

### Supported formats

BlackLab supports a number of input formats, but the most well-known are [TEI](http://www.tei-c.org/index.xml) (Text Encoding Initiative) and [FoLiA](http://proycon.github.io/folia/) (Format for Linguistic Annotation). These are both XML formats. If your data is not already in one of the supported formats, you need to convert it. (see the next section if you want to use a test dataset instead of your own)

NOTE: BlackLab needs *tokenized* data files as input. That means the word boundaries have already been determined and BlackLab can just index each word as it parses the input file.

One way to convert your data is using our tool [OpenConvert](https://github.com/INL/OpenConvert), which can generate TEI or FoLiA from txt, doc(x) or html files, among others. After conversion, you can tag the files using a tool such as <a href='http://ilk.uvt.nl/frog/'>Frog</a>. 

A [web-based user interface](http://openconvert.clarin.inl.nl/) for converting and tagging (Dutch) input files is available. You will need a [CLARIN.eu account](https://user.clarin.eu/user) ([more information](https://www.clarin.eu/content/clarin-identity-provider)).

### Testing with the Brown corpus

If you can't use your own data yet, we've provided a [tokenized, annotated TEI version of the Brown corpus](https://github.com/INL/BlackLab/wiki/brownCorpus.lemmatized.xml.zip) for you to test with. 

The [Brown corpus](http://en.wikipedia.org/wiki/Brown_Corpus "http://en.wikipedia.org/wiki/Brown_Corpus") is a corpus compiled in the 1960s by [Nelson Francis and Henry Kucera](http://archive.org/details/BrownCorpus) at Brown University. It is small by today's standard (500 documents, 1M words). It was converted to TEI format by [Lou Burnard](http://users.ox.ac.uk/~lou/). It is available from archive.org under the [CC-BY-NC 3.0](http://creativecommons.org/licenses/by-nc/3.0/) license, but we've created our own version which includes lemmata. (Please note that we didn't check the lemmatization, and it probably contains errors - useful for testing purposes only!)

### Indexing data

Get the blacklab JAR and the required libraries (see above). The libraries should be in a directory called "lib" that's in the same directory as the BlackLab JAR (or elsewhere on the classpath).

Start the IndexTool without parameters for help information:

	java -cp "blacklab.jar" nl.inl.blacklab.tools.IndexTool
 
(this assumes blacklab.jar and the lib subdirectory containing required libraries are located in the current directory; if not, prefix it with the correct directory)

(if you're on Windows, replace the classpath separator colon (:) with a semicolon (;))

We want to create a new index, so we need to supply an index directory, input file(s) and an input format:

	java -cp "blacklab.jar" nl.inl.blacklab.tools.IndexTool create INDEX_DIR INPUT_FILES FORMAT

If you specify a directory as the INPUT_FILES, it will be scanned recursively. You can also specify a file glob (such as \*.xml) or a single file. If you specify a .zip or .tar.gz file, BlackLab will automatically index its contents.

For example, if you have TEI data in /tmp/my-tei/ and want to create an index as a subdirectory of the current directory called "test-index", run the following command:

	java -cp "blacklab.jar" nl.inl.blacklab.tools.IndexTool create test-index /tmp/my-tei/ tei

Your data is indexed and placed in a new BlackLab index in the "test-index" directory.

Please note that if you're indexing large files, you should [give java more than the default heap memory](https://docs.oracle.com/cd/E15523_01/web.1111/e13814/jvm_tuning.htm#PERFM161), using the `-Xmx` option. For really large files, and if you have the memory, you could use `-Xmx 6G`, for example.

See also:

- [Adding a new input format](how-to-configure-indexing.html) (if your format isn't supported yet and you don't want to convert)
- [Indexing in detail](indexing-with-blacklab.html)

### Testing your index

BlackLab Core includes a very basic command-based query tool useful for testing and debugging. To query the index you just created using this tool, type:

	java -cp "blacklab.jar" nl.inl.blacklab.tools.QueryTool test-index

The query tool supports several query languages, but it will start in CorpusQL mode. A few hints:

- Enclose each word between double quotes: "the" "egg" searches for "the" followed by "egg"
- You can user regular expressions: ".*g" searches for words ending with 'g'
- If you want to get more of a feel for what kinds of matches were found, try grouping by matched text using the command "group match". Then, if you want to view one of the groups, use "group *n*".

Type "help" to see a list of commands.

See also:

- [Using the query tool](query-tool.html)
- [Corpus Query Language](corpus-query-language.html)

<a id="corpus-search-application"></a>

## Corpus search application

[BlackLab AutoSearch](https://github.com/INL/corpus-frontend/) is our corpus search application. It is easy to install; see the GitHub page for instructions.

![Screenshot of corpus-frontend](images/corpus-frontend.png)

<a id="write-a-simple-blacklab-application"></a>

## Write a simple BlackLab application

Finally, let's look at an example Java application.

Here’s the basic structure of a BlackLab search application, to give you an idea of where to look in the source code and documentation (note that we leave nl.inl.blacklab out of the package names for brevity):

1. Call BlackLab.open() to instantiate a BlackLabIndex object. This provides the main BlackLab API.
2. Construct a TextPattern structure that represents your query. You may want to do this from a query parser, or use one of the query parsers supplied with BlackLab (CorpusQueryLanguageParser, …).
3. Call the BlackLabIndex.find() method to execute the TextPattern and return a Hits object. (Internally, this translates the TextPattern into a Lucene SpanQuery, executes it, and collects the hits. Each of these steps may also be done manually if you wish to have more control over the process)
4. Sort or group the results, using Hits.sort() or Hits.group() and a HitProperty object to indicate the sorting/grouping criteria.
5. Select a few of your Hits to display by calling Hits.window().
6. Loop over the HitsWindow and display each hit.
7. Close the BlackLabIndex object.

The above in code:

	// Open your index
	try (BlackLabIndex index = BlackLab.open(new File("/home/zwets/testindex"))) {
	    String corpusQlQuery = " \"the\" [pos=\"adj.*\"] \"brown\" \"fox\" ";
	
	    // Parse your query to get a TextPattern
	    TextPattern pattern = CorpusQueryLanguageParser.parse(corpusQlQuery);
	
	    // Execute the TextPattern
	    Hits hits = index.find(pattern);
	
	    // Sort the hits by the words to the left of the matched text
	    HitProperty sortProperty = new HitPropertyLeftContext(index, index.annotation("word"));
	    hits = hits.sort(sortProperty);
	
	    // Limit the results to the ones we want to show now (i.e. the first page)
	    Hits window = hits.window(0, 20);
	
	    // Iterate over window and display the hits
	    Concordances concs = hits.concordances(ContextSize.get(5));
	    for (Hit hit: window) {
	        Concordance conc = concs.get(hit);
	        // Strip out XML tags for display.
	        String left = XmlUtil.xmlToPlainText(conc.left);
	        String hitText = XmlUtil.xmlToPlainText(conc.hit);
	        String right = XmlUtil.xmlToPlainText(conc.right);
	        System.out.printf("%45s[%s]%s\n", left, hitText, right);
	    }
	
	}

See also:

- [API reference](apidocs/index.html)
- [The included example application](example-application.html)
