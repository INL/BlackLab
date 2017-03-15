# Introduction

## What is BlackLab Server?

BlackLab Server is a REST web service for accessing [BlackLab](../) indices. This makes it easy to use BlackLab from your favourite programming language. It can be used for anything from quick analysis scripts to full-featured corpus search applications.

This page explains how to set up and use BlackLab Server. See the [BlackLab homepage](../) for more information on the underlying corpus search engine.

* [Features](#features)
* [Overview](#overview)
* [Requests](#requests)
* [Sorting, grouping & faceting](#sorting-grouping-filtering-faceting)
* [Examples](#examples)
* [Installation](#installation)
* [Configuration file](#configuration-file)
* [Error and status responses](#error-and-status-responses) 

<a id="features"></a>

## Features
-   Supports XML, JSON and JSONP, making it very easy to use from any programming language.
-   Supports faceted search, giving users an overview of their search results and allowing them to easily refine them.
-   Uses a stateless protocol and persistent identifiers, making it possible to develop web applications that “feel at home” in the browser, meaning they can be bookmarked, opened in multiple tabs and properly support the back button.
-   Makes very responsive (AJAX or 'regular') web applications possible because requests return all relevant information at once and both client and server can make use of caching.
-   One BlackLab Server instance can be used for multiple corpora; this means you need less configuration and fewer server resources.
-   BlackLab Server can be tuned in terms of server load and client responsiveness. Possible settings are, for example: strive for X free memory on the server; allow any client at most X running jobs; let clients cache resultats for X amount of time.
-   Provides debug information (e.g. show all searches in the cache)
-   Has many configurable defaults: page size, context size, default response format, default query language, etc. Of course defaults may be overridden for each request.

<a id="overview"></a>

## Overview
BlackLab Server is a read-only REST webservice. Only GET requests are supported.

It is stateless: a particular URL will always result in the same response. There’s one exception to this: when the server has the requested set of results, it might indicate that it is still counting the total number of results, and has counted X so far. The client may keep doing additional requests to update the running count for the user. This way, the user gets to see the first results as soon as possible, and she’ll be able to see that the total number of results is still being counted.

The webservice answers in JSON or XML. Selection of the desired output format is done through the HTTP Accept header (value “application/json” or “application/xml”), or by passing an extra parameter “outputformat” (value “json” or “xml”). If both are specified, the parameter has precedence. If neither are specified, the configured default format is used (usually XML).

An extra option is JSONP (“padded JSON”, for when the webservice is running on a different host than the web application). Use the “jsonp” parameter for this (see next section).

<a id="requests"></a>

## Requests
A request to BlackLab Server has the following structure:

http://server/webservice/corpus/resource?parameters

Here’s what the various parts of this URL mean:

<table>
	<tr>
		<th style="text-align:left;">Part        </th>
		<th style="text-align:left;">Meaning</th>
	</tr>
	<tr>
		<td>server      </td>
		<td>the server name, e.g. “blacklab.ivdnt.org”</td>
	</tr>
	<tr>
		<td>webservice  </td>
		<td>the web service name, e.g. “blacklab-server”</td>
	</tr>
	<tr>
		<td>corpus      </td>
		<td>the corpus (i.e. text collection) to search, e.g. “opensonar”</td>
	</tr>
	<tr>
		<td>resource    </td>
		<td>what type of information we’re looking for (hits, docs, docs/pid, docs/pid/content, …) (see below for the meaning of each resource)</td>
	</tr>
	<tr>
		<td>pid         </td>
		<td>persistent identifier for the document. This refers to a metadata field that must be configured per corpus (in the index metadata JSON file; see documentation about indexing with BlackLab). Any field that uniquely identifies the document and won’t change in the future will do. You can retrieve documents with this pid, and result sets will use it to refer to the corresponding documents.><br/><br/><b>NOTE:</b> BlackLab Server will use the Lucene document id instead of a true persistent identifier if your corpus has no persistent identifier configured (using "pidField" in the index template file - see [Indexing with BlackLab](indexing-with-blacklab.html)), but this is not recommended: Lucene document ids can change if you re-index or compact the index, so bookmarked URLs may not always return to the same information.</td>
	</tr>
	<tr>
		<td>parameters  </td>
		<td>search and result parameters, that indicate what pattern you wish to look for, what metadata values you wish to filter on, what to group or sort on, what part of the results to show, what data format to return results in, etc. (see below)</td>
	</tr>
</table>

Explanation of the various resources:

<table>
	<tr>
		<th style="text-align:left;">Resource        </th>
		<th style="text-align:left;">Meaning</th>
	</tr>
	<tr>
		<td>hits </td>
		<td>A set of occurrences of a pattern in the corpus (optionally filtered on document properties as well). This resource can also return the result of grouping hits (returning a list of groups), or the contents of one such group (if you wish to the hits in a group).</td>
	</tr>
	<tr>
		<td>docs </td>
		<td>A set of documents that contain a certain pattern and/or match a certain document filter query. This resource can also return the result of grouping document results, or show the contents of one such group.</td>
	</tr>
	<tr>
		<td>docs/pid </td>
		<td>Metadata for a document</td>
	</tr>
	<tr>
		<td>docs/pid/contents </td>
		<td>Contents of a document. This returns the original input XML.</td>
	</tr>
	<tr>
		<td>docs/pid/snippet </td>
		<td>Uses the forward index to retrieve a snippet of the document.</td>
	</tr>
</table>

Below is an overview of parameters that can be passed to the various resources. Default values for most parameters can be configured on the server; below are a few suggestions for defaults.

(NOTE: parameters in italics haven't been implemented yet)

<table>
	<tr>
		<th style="text-align:left;">Parameter        </th>
		<th style="text-align:left;">Meaning</th>
	</tr>
	<tr>
		<td>patt </td>
		<td>Pattern to search for.</td>
	</tr>
	<tr>
		<td>pattlang </td>
		<td>Query language for the patt parameter. (default: corpusql, Corpus Query Language. Also supported: contextql, Contextual Query Language (only very basic support though)) and lucene (Lucene Query Language).</td>
	</tr>
	<tr>
		<td>pattgapdata </td>
		<td>(Corpus Query Language only) Data (TSV, tab-separated values) to put in gaps in query. You may leave 'gaps' in the double-quoted strings in your query that can be filled in from tabular data. The gaps should be denoted by @@, e.g. [lemma="@@"] or [word="@@cat"]. For each row in your TSV data, will fill in the row data in the gaps. The queries resulting from all the rows are combined using OR. For example, if your query is "The" "@@" "@@" and your TSV data is "white\tcat\nblack\tdog", this will execute the query ("The" "white" "cat") | ("The" "black" "dog"). Please note that if you want to pass a large amount of data, you should use a POST request as the amount of data you can pass in a GET request is limited (with opinions on a safe maximum size varying between 255 and 2048 bytes). Large amounts of data </td>
	</tr>
	<tr>
		<td>pattfield </td>
		<td>Content field to search. (default: the main contents field, corpus-specific. Usually “contents”.)</td>
	</tr>
	<tr>
		<td>filter </td>
		<td>Document filter query, e.g. “publicationYear:1976” (default: none)</td>
	</tr>
	<tr>
		<td>filterlang </td>
		<td>Query language for filter parameter. Supported are lucene (Lucene Query Language) and (limited) contextql (contextual query language).(default: lucene )</td>
	</tr>
	<tr>
		<td>docpid </td>
		<td>Filter on a single document pid, e.g. “DOC0001” (default: none)</td>
	</tr>
	<tr>
		<td>wordsaroundhit </td>
		<td>Number of words of context to retrieve for hits- and docs-results (default: 5)</td>
	</tr>
	<tr>
		<td>sort </td>
		<td>Sorting criteria, comma-separated. ‘-’ reverses sort order. See below. (default: don’t sort)</td>
	</tr>
	<tr>
		<td>group </td>
		<td>Grouping criteria, comma-separated. See below. (default: don’t group)</td>
	</tr>
	<tr>
		<td>viewgroup </td>
		<td>Identity of one of the groups to view (identity values are returned with the grouping results).</td>
	</tr>
	<tr>
		<td>hitfiltercrit </td>
		<td>A criterium to filter hits on. Also needs hitfilterval to work. See below. (default: don't filter)<br/>This is useful if you want to view hits in a group, and then be able to group on those hits again. These two parameters essentially supersede the viewgroup parameter: that parameter also allows you to view the hits in a group, but won't allow you to group that subset of hits again. By specifying multiple criteria and values to hitfiltercrit/hitfilterval, you can keep diving deeper into your result set.</td>
	</tr>
	<tr>
		<td>hitfilterval </td>
		<td>A value (of the specified hitfiltercrit) to filter hits on. (default: don't filter)</td>
	</tr>
	<tr>
		<td>facets </td>
		<td>Document faceting criteria, comma-separated. See below.  (default: don’t do any faceting)</td>
	</tr>
	<tr>
		<td><i>collator</i></td>
		<td>What collator to use for sorting and grouping (default: nl)</td>
	</tr>
	<tr>
		<td>first </td>
		<td>First result (0-based) to return (default: 0)</td>
	</tr>
	<tr>
		<td>number </td>
		<td>Number of results to return (if available) (default: 50)</td>
	</tr>
	<tr>
		<td>hitstart </td>
		<td>(snippet operation) First word (0-based) of the hit we want a snippet around (default: 0)</td>
	</tr>
	<tr>
		<td>hitend </td>
		<td>(snippet operation) First word (0-based) after the hit we want a snippet around (default: 1)</td>
	</tr>
	<tr>
		<td>wordstart </td>
		<td>(snippet/contents operations) First word (0-based) of the snippet/part of the document we want. -1 for document start. NOTE: partial contents XML output will be wrapped in &#60;blacklabResponse/&#62; element to ensure a single root element.</td>
	</tr>
	<tr>
		<td>wordend </td>
		<td>(snippet/contents operations) First word (0-based) after the snippet/part of the document we want. -1 for document end.</td>
	</tr>
	<tr>
		<td>block (deprecated)</td>
		<td>Blocking (“yes”) or nonblocking (“no”) request? (default: yes) 
		    <br/><b>NOTE:</b> nonblocking requests will be removed in a future version.</td>
	</tr>
	<tr>
		<td>waitfortotal </td>
		<td>Whether or not to wait for the total number of results to be known. If no (the default), subsequent requests (with number=0 if you don’t need more hits) can be used to monitor the total count progress. (default: no)</td>
	</tr>
	<tr>
		<td>maxretrieve </td>
		<td>Maximum number of hits to retrieve. -1 means "no limit". Also affects documents-containing-pattern queries and grouped-hits queries. Default configurable in blacklab-server.json. Very large values (millions, or unlimited) may cause server problems.</td>
	</tr>
	<tr>
		<td>maxcount </td>
		<td>Maximum number of hits to count. -1 means "no limit". Default configurable in blacklab-server.json. Even when BlackLab stops retrieving hits, it still keeps counting. For large results sets this may take a long time.</td>
	</tr>
	<tr>
		<td>outputformat </td>
		<td>“json” or “xml”. (Default: check the HTTP Accept header, or use the server default (usually xml) if none was specified. NOTE: most browsers send a default Accept header including XML.</td>
	</tr>
	<tr>
		<td>jsonp </td>
		<td>Name of JSONP callback function to use. Automatically forces the outputformat to JSONP. (A JSONP response is a Javascript with a single function call that gets the JSON response object as its parameter. This is used to circumvent browsers&#39; Same Origin Policy that blocks AJAX calls to other domains)</td>
	</tr>
	<tr>
		<td>prettyprint </td>
		<td>yes or no. Determines whether or not the output is on separate lines and indented. Useful while debugging. (default: no (yes in debug mode, see configuration))</td>
	</tr>
	<tr>
		<td>includetokencount </td>
		<td>yes or no. Determines whether or not a document search includes the total number of tokens in the matching documents. Slower, because all document information has to to be fetched to calculate this. (default: no)</td>
	</tr>
	<tr>
		<td>usecontent </td>
		<td>fi or orig. fi uses the forward index to reconstruct document content (for snippets and concordances; inline tags are lost in the process), orig uses the original XML from the content store (slower but more accurate).</td>
	</tr>
	<tr>
		<td>calc </td>
		<td>(empty) or colloc. Calculate some information from the result set. Currently only supports calculating collocations (frequency lists of words near hits).</td>
	</tr>
	<tr>
		<td>sample</td>
		<td>Percentage of hits to select. Chooses a random sample of all the hits found.</td>
	</tr>
	<tr>
		<td>samplenum</td>
		<td>Exact number of hits to select. Chooses a random sample of all the hits found.</td>
	</tr>
	<tr>
		<td>sampleseed</td>
		<td>Signed long random seed for sampling. Optional. When given, uses this value to seed the random number
		    generator, ensuring identical sampling results next time. Please note that, without sorting, hit order
		    is undefined (if the same data is re-indexed, hits may be produced in a different order). So if you 
		    want true reproducability, you should always sort hits that you want to sample.</td>
	</tr>
</table>

NOTE: using the original content may cause problems with well-formedness; these are fixed automatically, but the fix may result in inline tags in strange places (e.g. a start-sentence tag that is not at the start of the sentence anymore)

<a id="sorting-grouping-faceting"></a>

## Sorting, grouping, filtering & faceting

The sort, group, hitfiltercrit and facets parameters receive one or more criteria (comma-separated) that indicate what to sort, group, filter or facet on.

<table>
	<tr>
		<th style="text-align:left;">Criterium        </th>
		<th style="text-align:left;">Meaning</th>
	</tr>
	<tr>
		<td>hit[:prop[:c]] </td>
		<td>Sort/group/facet on matched text. If prop is omitted, the default property (usually word) is used. c can specify case-sensitivity: either s (sensitive) or i (insensitive). prop and c can also be added to left, right, wordleft and wordright. Examples: hit, hit:lemma, hit:lemma:s.</td>
	</tr>
	<tr>
		<td>left / right </td>
		<td>Left/right context words. Used for sorting, not for grouping/faceting (use wordleft/wordright instead). Examples: left, left:pos, left:pos:s.</td>
	</tr>
	<tr>
		<td>context</td>
		<td>More generic context words expression, giving the user more control at the cost of a bit of speed. Example:
		    context:word:s:H1-2 (first two matched words). See below for a complete specification.</td>
	</tr>
	<tr>
		<td>wordleft / wordright </td>
		<td>Single word to the left or right of the matched text. Used for grouping/faceting. Examples: wordleft, wordleft:pos</td>
	</tr>
	<tr>
		<td>field:name </td>
		<td>Metadata field</td>
	</tr>
	<tr>
		<td>decade:name </td>
		<td>Sort/group by the decade of the year given in specified metadata field.</td>
	</tr>
	<tr>
		<td>numhits </td>
		<td>(for sorting per-document results) Sort by number of hits in the document.</td>
	</tr>
	<tr>
		<td>identity </td>
		<td>(for sorting grouping results) Sort by group identity.</td>
	</tr>
	<tr>
		<td>size </td>
		<td>(for sorting grouping results) Sort by group size, descending by default.</td>
	</tr>
</table>

### Grouping/sorting on context words

Criteria like "context:word:s:H1-2" (first two matched words) allow fine control over what to group or sort on.

Like with criteria such as left, right or hit, you can vary the property to group or sort on (e.g. word/lemma/pos, or other options depending on your data set). You may specify whether to sort/group case- and accent-sensitively (s) or insensitively (i).

The final parameter to a "context:" criterium is the specification. This consists of one or more parts separated by a semicolon. Each part consists of an "anchor" and number(s) to indicate a stretch of words. The anchor can be H (hit text), E (hit text, but counted from the end of the hit), L (words to the left of the hit) or R (words to the right of the hit). The number or numbers after the anchor specify what words you want from this part. A single number indicates a single word; 1 is the first word, 2 the second word, etc. So "E2" means "the second-to-last word of the hit". Two numbers separated by a dash indicate a stretch of words. So "H1-2" means "the first two words of the hit", and "E2-1" means "the second-to-last word followed by the last word". A single number followed by a dash means "as much as possible from this part, starting from this word". So "H2-" means "the entire hit text except the first word".

A few more examples:
- context:word:s:H1;E1 (the first and last matched word)
- context:word:s:R2-3 (second and third word to the right of the match)
- context:word:s:L1- (left context, starting from first word to the left of the hit, i.e. the same as "left:word:s". How many words of context are used depends on the 'wordsaroundhit' parameter, which defaults to 5)

<a id="examples"></a>

## Examples

### Scripts

For examples of using BlackLab Server from (many) different programming languages, see [here](from-different-languages.html).

### Requests

(NOTE: for clarity, double quotes have not been URL-encoded)

Information about the webservice; list of available indices

		http://blacklab.ivdnt.org/blacklab-server/ (trailing slash optional)

Information about the “opensonar” corpus (structure, fields, human-readable names)

		http://blacklab.ivdnt.org/blacklab-server/opensonar/ (trailing slash optional)

All occurrences of “test” in the “opensonar” corpus (CorpusQL query)

		http://blacklab.ivdnt.org/blacklab-server/opensonar/hits?patt="test"

All documents having “guide” in the title and “test” in the contents, sorted by author and date, resultats 61-90

		http://blacklab.ivdnt.org/blacklab-server/opensonar/docs?filter=title:guide&patt="test"& sort=field:author,field:date&first=61&number=30

Occurrences of “test”, grouped by the word left of each hit

		http://blacklab.ivdnt.org/blacklab-server/opensonar/hits?patt="test"&group=wordleft

Documents containing “test”, grouped by author

		http://blacklab.ivdnt.org/blacklab-server/opensonar/docs?patt="test"&group=field:author

Metadata of document with specific PID

		http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802

The entire original document

		http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents

The entire document, with occurrences of “test” highlighted (with <hl/\> tags)

		http://blacklab.ivdnt.org/blacklab-server/opensonar/docs/0345391802/contents?patt="test"

### Output

(Description of output format coming soon. Please try in the browser for now; use outputformat=json or outputformat=xml)

<a id="installation"></a>

## Installation

First, you need the BlackLab Server WAR file. You can either download the latest release [https://github.com/INL/BlackLab/releases here], or you can build it by cloning the [https://github.com/INL/BlackLab GitHub repository] and building it using Maven.

BlackLab Server needs to run in a Java application server that support servlets. We’ll assume Apache Tomcat here, but others should work almost the same.

For larger indices, it is important to give Tomcat's JVM enough heap memory. See [http://crunchify.com/how-to-change-jvm-heap-setting-xms-xmx-of-tomcat/ here]. (If heap memory is low and/or fragmented, the JVM garbage collector might start taking 100% CPU moving objects in order to recover enough free space, slowing things down to a crawl.)

Place the configuration file blacklab-server.json (see Appendix A) in /etc/blacklab/ or, if you prefer, on the application server’s classpath. Make sure at least the “indexCollections” setting is correctly specified (should point to a directory containing one or more BlackLab indices as subdirectories). Apart from that, the file could actually be empty: default values are used for missing settings. So if you have an index in directory /home/jan/blacklab/test, the minimal blacklab-server.json looks like this:

	{ "indexCollections": [ "/home/jan/blacklab" ] }

Place blacklab-server.war in Tomcat’s webapps directory ($TOMCAT/webapps/). Tomcat should automatically discover and deploy it, and you should be able to go to [http://servername:8080/blacklab-server/](http://servername:8080/blacklab-server/ "http://servername:8080/blacklab-server/") and see the BlackLab Server information page, which includes a list of available corpora.

To (significantly!) improve performance of certain operations, including sorting and grouping large result sets, you might want to consider using the [vmtouch](https://github.com/INL/BlackLab/wiki/Improve-search-speed-using-the-disk-cache "https://github.com/INL/BlackLab/wiki/Improve-search-speed-using-the-disk-cache") tool to lock files in the OS's disk cache.

<a id="configuration-file"></a>

## Configuration file

Below is a complete example of the configuration file (blacklab-server.json) including comments (contrary to the JSON standard, this file may contain double slash end-of-line comments).

You should at least have an “indexCollections” or “indices” setting, so you have at least one index to search. All other parameters are optional and may, for example, be used to improve performance.

The blacklab-server.json file should be placed in /etc/blacklab/.

	{
	    // BlackLab Server config file
	    // ===============================================================
	    // NOTE: this file is in JSON format, with end-of-line comments (//)
	    // allowed.
	
	    // The available indices
	    // ---------------------------------------------------------------
	    
	    // Index collections are multiple indices as subdirectories of a 
	    // single directory. BLS will detect when an index is added and 
	    // make it available to query. It will also detect if it has been 
	    // removed.
	    // NOTE: if multiple collections contain indices with the
	    // same name, one of them is picked at random; therefore it&#39;s best
	    // to avoid this.
	    "indexCollections": [
	        "/data/blacklab-indices/"
	    ],
	
	    // A list of single indices and where they can be found. 
	    "indices": {
	        "brown": {
	            "dir": "/data/brown-corpus/index"
	        }
	    },
	
	    // Configuration that affects how requests are handled
	    // ---------------------------------------------------------------
	    "requests": {
	        // Default number of hits/results per page.
	        // The "number" GET parameter overrides this value.
	        "defaultPageSize": 20,
	
	        // Maximum number of hits/results per page allowed.
	        // (if the "number" GET variable is set to a higher value,
	        //  the above default page size will be used instead)
	        "maxPageSize": 3000,
	
	        // Default number of words around hit.
	        // The "wordsaroundhit" GET parameter overrides this value.
	        "defaultContextSize": 5,
	
	        // Maximum context size allowed. Only applies to sets of hits,
	        // not to individual snippets.
	        "maxContextSize": 20,
	
	        // Maximum snippet size allowed. If this is too big, users can
	        // view the whole document even if they may not be allowed to.
	        // (this applies to the "wordsaroundhit" GET parameter of the 
	        // /docs/ID/snippet resource)
	        "maxSnippetSize": 100,
	
	        // Default maximum number of hits to retrieve. This affects 
	        // not only regular hits searches, but also 
	        // documents-containing-pattern searches (will stop yielding 
	        // documents when the max. hit number is reached) and 
	        // grouped-hits searches (will only group this number of hits
	        // at most).
	        // The search summary will show you whether the retrieval 
	        // limit was reached or not. -1 means "no limit". For large 
	        // corpora, this can crash the server if uses perform big 
	        // searches.
	        "defaultMaxHitsToRetrieve": 1000000,
	
	        // Default maximum number of hits to count.
	        // BlackLab will keep counting hits even if it hits the 
	        // retrieval maximum. The default value for this is -1,
	        // meaning "no limit". Counts for large hit sets will take a 
	        // long time but will generally not crash the server.
	        "defaultMaxHitsToCount": 10000000,
	
	        // Users can change the above retrieval maximum with the
	        // "maxretrieve" URL parameter. This specifies the maximum 
	        // allowed value for that parameter. -1 means "no limit".
	        "maxHitsToRetrieveAllowed": 1000000,
	
	        // Users can change the above count maximum with the 
	        // "maxcount" URL parameter. This specifies the maximum 
	        // allowed value for that parameter. -1 means "no limit".
	        "maxHitsToCountAllowed": 10000000,
	
	        // Clients from these IPs may choose their own user id and 
	        // send  it along in a GET parameter "userid". This setting 
	        // exists for web applications that contact the webservice
	        // (partly) through the server component. They would get the 
	        // same session id for each user, making them likely to hit 
	        // the maxRunningJobsPerUser setting. Instead, they should 
	        // assign session IDs for each of their clients and send them
	        // along with any request to the webservice.
	        "overrideUserIdIps": [
	            "127.0.0.1",      // IPv4 localhost
	            "0:0:0:0:0:0:0:1" // IPv6 localhost
	        ],
	    },
	
	    // Settings related to tuning server load and client 
	    // responsiveness
	    // ---------------------------------------------------------------
	    "performance": {
	
	        // Settings for controlling server load
	        "serverLoad": {
	
	            // Maximum number of concurrent searches.
	            // Should be set no higher than the number of cores in the machine. 
	            "maxConcurrentSearches": 4,
	
	            // Maximum number of paused searched.
	            // Pausing too many searches will just fill up memory, so don't
	            // set this too high. 
	            "maxPausedSearches": 10,
	
	            // Long-running counts take up a lot of CPU and memory. If a client hasn't
	            // checked the status of a count, we'd like to pause it and eventually abort
	            // it so we don't waste resources on something nobody's interested in anymore.
	            // This setting controls after how many seconds such an "abandoned" count is
	            // paused. The client might come back to it, so we don't abort it right away.
	            "abandonedCountPauseTimeSec": 10,
	
	            // Similar to the previous setting, this setting controls after how many seconds
	            // an "abandoned" count is aborted.
	            "abandonedCountAbortTimeSec": 600
	
	        },
    
	        // Settings for job caching.
	        "cache": {
	            // How many search jobs will we cache at most? (or -1 for 
	            // no limit) A note about jobs: a request to BlackLab 
	            // Server routinely results in 3+ simultaneous search jobs
	            // being launched: a job to get a window into the sorted 
	            // hits, which launches a job to get sorted hits, which 
	            // launches a job to get the unsorted hits. There&#39;s also 
	            // usually a separate job for keeping track of the running
	            // total number of hits found (which re-uses the unsorted
	            // hits job). The reason for this architecture is that 
	            // jobs can be more easily re-used in subsequent searches
	            // that way: if the sort changes, we can still use the 
	            // unsorted hits job, etc. Practical upshot of this: 
	            // number of jobs does not equal number of searches. Don’t
	            // set this too low.
	            "maxNumberOfJobs": 100,
	
	            // After how much time will a search job be removed from 
	            // the cache? (in seconds)
	            "maxJobAgeSec": 3600,
	
	            // Maximum size the cache may grow to (in megabytes), or 
	            // -1 for no limit.
	            // [NOT PROPERLY IMPLEMENTED YET! LEAVE AT -1 FOR NOW]
	            "maxSizeMegs": -1,
	
	            // How much free memory the cache should shoot for (in 
	            // megabytes) while cleaning up. Because we don&#39;t have 
	            // direct control over the garbage collector, we can&#39;t 
	            // reliably clean up until this exact number is available.
	            // Instead we just get rid of a few cached jobs whenever a
	            // new job is added and we&#39;re under this target number.
	            // See numberOfJobsToPurgeWhenBelowTargetMem.
	            "targetFreeMemMegs": 100,
	
	            // When there&#39;s less free memory available than 
	            // targetFreeMemMegs, each time a job is created and added
	            // to the cache, we will get rid of this number of older 
	            // jobs in order to (hopefully) free up memory (if the 
	            // Java GC agrees with us). 2 seems like an okay value, 
	            // but you can change it if you want to experiment.
	            "numberOfJobsToPurgeWhenBelowTargetMem": 2
	        },
	
	        // The minimum amount of free memory required to start a new 
	        // search job. If this memory is not available, an error 
	        // message is returned.
	        "minFreeMemForSearchMegs": 50,
	
	        // The maximum number of jobs a user is allowed to have 
	        // running at the same time. This does not include finished 
	        // jobs in the cache, only jobs that have not finished yet.
	        // The above remark about jobs applies here too: one search 
	        // request will start multiple jobs. Therefore, this value 
	        // shouldn&#39;t be set too low. This setting is meant to prevent
	        // over-eager scripts and other abuse from bringing down the
	        // server. Regular users should never hit this limit.
	        "maxRunningJobsPerUser": 20,
	
	        // How long the client may keep results we give them in their
	        // local (browser) cache. This is used to write HTTP cache 
	        // headers. Low values mean clients might re-request the same 
	        // information, making clients less responsive and consuming 
	        // more network resources. Higher values make clients more 
	        // responsive but could cause problems if the data (or worse,
	        // the protocol) changes after an update. A value of an hour 
	        // or so seems reasonable.
	        "clientCacheTimeSec": 3600
	
	    },
	
	    // A list of IPs that will run in debug mode.
	    // In debug mode, ...
	    // - the /cache-info resource show the contents of the job cache
	    //   (other debug information resources may be added in the 
	    //   future)
	    // - output is prettyprinted by default (can be overriden with the
	    //   "prettyprint"
	    //   GET parameter)
	    "debugModeIps": [
	        "127.0.0.1",      // IPv4 localhost
	        "0:0:0:0:0:0:0:1" // IPv6 localhost
	    ]
	
	}

<a id="error-and-status-responses"></a>

## Error and status responses

BLS can return these error and status codes. The associated human-readable message is informational only and can be shown to the user if you want; note though that the precise wording may change in future versions. The codes in the left column will not change and may be used to show your own custom messages (e.g. translations).

Operations that do not return status or error codes and messages (which is all succesful retrieval operations) will always set the HTTP status to "200 OK".

<table>
	<tr>
		<th style="text-align:left;">HTTP status </th>
		<th style="text-align:left;">Error code     </th>
		<th style="text-align:left;">Error message</th>
	</tr>
	<tr>
		<td>200 OK </td>
		<td>(no code) </td>
		<td>(no message, just search results)</td>
	</tr>
	<tr>
		<td>200 OK </td>
		<td>SUCCESS </td>
		<td>Index deleted succesfully.</td>
	</tr>
	<tr>
		<td>201 Created </td>
		<td>SUCCESS </td>
		<td>Index created succesfully.</td>
	</tr>
	<tr>
		<td>202 Accepted </td>
		<td>SUCCESS </td>
		<td>Documents uploaded succesfully; indexing started.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_OPERATION </td>
		<td>Unknown operation. Check your URL.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>NO_DOC_ID </td>
		<td>Specify document pid.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>NO_FILTER_GIVEN </td>
		<td>Document filter required. Please specify &#39;filter&#39; parameter.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>FILTER_SYNTAX_ERROR </td>
		<td>Error parsing FILTERLANG filter query: ERRORMESSAGE</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_FILTER_LANG </td>
		<td>Unknown filter language &#39; FILTERLANG &#39;. Supported: SUPPORTED_LANGS.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>NO_PATTERN_GIVEN </td>
		<td>Text search pattern required. Please specify &#39;patt&#39; parameter.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>PATT_SYNTAX_ERROR </td>
		<td>Syntax error in PATTLANG pattern: ERRORMESSAGE</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_PATT_LANG </td>
		<td>Unknown pattern language &#39;PATTLANG&#39;. Supported: SUPPORTED_LANGS.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_GROUP_PROPERTY </td>
		<td>Unknown group property &#39;NAME&#39;.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>UNKNOWN_SORT_PROPERTY </td>
		<td>Unknown sort property &#39;NAME&#39;.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>ERROR_IN_GROUP_VALUE </td>
		<td>Parameter &#39;viewgroup&#39; has an illegal value: GROUPID /<br/>
		    Parameter 'viewgroup' specified, but required 'group' parameter is missing.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>GROUP_NOT_FOUND </td>
		<td>Group not found: GROUPID</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>REGEXP_TOO_LARGE</td>
		<td>Regular expression too large. (NOTE: maximum size depends on Java stack size)</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>JSONP_ILLEGAL_CALLBACK </td>
		<td>Illegal JSONP callback function name. Must be a valid Javascript name.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>SNIPPET_TOO_LARGE </td>
		<td>Snippet too large. Maximum size for a snippet is MAXSIZE words.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>ILLEGAL_BOUNDARIES </td>
		<td>Illegal word boundaries specified. Please check parameters.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>HIT_NUMBER_OUT_OF_RANGE </td>
		<td>Non-existent hit number specified.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>CANNOT_CREATE_INDEX </td>
		<td>Could not create index. REASON</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>INDEX_ALREADY_EXISTS </td>
		<td>Could not create index. Index already exists.</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>ILLEGAL_INDEX_NAME </td>
		<td>Illegal index name (only word characters, underscore and dash allowed): INDEXNAME</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>CANNOT_UPLOAD_FILE </td>
		<td>Cannot upload file. REASON</td>
	</tr>
	<tr>
		<td>400 Bad Request </td>
		<td>INDEX_ERROR </td>
		<td>An error occurred during indexing: MESSAGE</td>
	</tr>
	<tr>
		<td>401 Unauthorized </td>
		<td>NOT_AUTHORIZED </td>
		<td>Unauthorized operation. REASON</td>
	</tr>
	<tr>
		<td>403 Forbidden </td>
		<td>FORBIDDEN_REQUEST </td>
		<td>Forbidden request. REASON</td>
	</tr>
	<tr>
		<td>405 Method Not Allowed </td>
		<td>ILLEGAL_REQUEST </td>
		<td>Illegal GET/POST/PUT/DELETE request. REASON</td>
	</tr>
	<tr>
		<td>404 Not Found </td>
		<td>CANNOT_OPEN_INDEX </td>
		<td>Could not open index &#39;NAME&#39;. Please check the name.</td>
	</tr>
	<tr>
		<td>404 Not Found </td>
		<td>DOC_NOT_FOUND </td>
		<td>Document with pid &#39;PID&#39; not found.</td>
	</tr>
	<tr>
		<td>409 Conflict </td>
		<td>INDEX_UNAVAILABLE </td>
		<td>The index &#39;INDEXNAME&#39; is not available right now. Status: STATUS</td>
	</tr>
	<tr>
		<td>429 Too Many Requests </td>
		<td>TOO_MANY_JOBS </td>
		<td>You already have too many running searches. Please wait for some previous searches to complete before starting new ones.</td>
	</tr>
	<tr>
		<td>500 Internal Server Error </td>
		<td>INTERNAL_ERROR </td>
		<td>An internal error occurred. Please contact the administrator.</td>
	</tr>
	<tr>
		<td>503 Service Unavailable </td>
		<td>SERVER_BUSY </td>
		<td>The server is under heavy load right now. Please try again later.</td>
	</tr>
	<tr>
		<td>503 Service Unavailable </td>
		<td>SEARCH_TIMED_OUT </td>
		<td>Search took too long, cancelled.</td>
	</tr>
</table>