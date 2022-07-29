# Find hits

Find occurrences of a text pattern in the corpus, optionally filtered on document metadata fields.

This resource can also [group hits](../hits-grouped/get.md) (returning a list of groups), or [show the contents](../hits-viewgroup/get.md) of one of the resulting groups.

**URL** : `/blacklab-server/<corpus-name>/hits`

**Method** : `GET`

All parameters except `patt` are optional.

#### Basic parameters

Use these to find text patterns in the corpus and control which results are returned.

- `patt`: ([Corpus Query Language](guide/corpus-query-language.md)) pattern to search for
- `filter`: [Lucene Query Language](https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description) document filter query.
- `wordsaroundhit`: number of words to show around each hit. Default: `5`
- `first`: first result (0-based) to return. Default: `0`
- `number`: number of results to return (if available). Default: `50`
- `waitfortotal`: Whether or not to wait for the total number of results to be known. If no (the default), subsequent requests (with number=0 if you don’t need more hits) can be used to monitor the total count progress. Default: `false`


#### Parameters for sampling

Take a random sampling of hits. Note that this has to retrieve all hits (or at least as many hits as the `maxretrieve` setting allows), then perform the sampling, so it may take a while.

- `sample`: Percentage of hits to select. Chooses a random sample of all the hits found.
- `samplenum`: Exact number of hits to select. Chooses a random sample of all the hits found.
- `sampleseed`: Signed long seed number for sampling. If given, uses this value to seed the random number generator, ensuring identical sampling results next time. Please note that, without sorting, hit order is undefined (if the same data is re-indexed, hits may be produced in a different order). So if you want true reproducability, you should always sort hits that you want to sample, ideally with multiple sort criteria so the sort is fully defined.

#### Parameters for sorting and grouping hits and faceting documents

Hits can be grouped by various criteria (see below). Faceting can be performed on the matching documents.

- `sort`: One or more sort criteria. See below.<br/>**NOTE:** sorting results required retrieving all of them, so this can take some time and consume significant memory.
- `group`: One or more grouping criteria. See below.<br/>**NOTE:** grouping results required retrieving all of them, so this can take some time and consume significant memory.
- `includegroupcontents`: Whether to include the hits with each group.
  NOTE: only works for /hits requests for now. Default: `false`
- `viewgroup`: Identity of one of the groups to view (identity values are returned with the grouping results).<br/>**NOTE:** you may not get all results in the group because there is a limit to how many results are stored per group! Use `hitfiltercrit` to get all hits.
- `hitfiltercrit`: A criterium to filter hits on. Also needs `hitfilterval` to work. See below.<br/>
  This is useful if you want to view hits in a group, and then be able to group on those hits again. These two parameters essentially do something similar to the `viewgroup` parameter: that parameter also allows you to view the hits in a group, but won't allow you to group that subset of hits again. By specifying multiple criteria and values to hitfiltercrit/hitfilterval, you can keep diving deeper into your result set.<br/>
  **NOTE:** this may be slow because it finds all hits, then filters them by this criterium.
- `hitfilterval`: A value (of the specified `hitfiltercrit`) to filter hits on.
- `facets`: Document faceting criteria, comma-separated. See below.  (default: don’t do any faceting)

Grouping and sorting criteria:

TODO

#### Miscellaneous parameters

Some less commonly used parameters for advanced use cases.

- `pattlang`: pattern language to use for `patt`. Defaults to `corpusql` (Corpus Query Language). The other values (`contextql` and `luceneql`) have very limited support at the moment.  Other, more useful query languages may be added in the future.
- `pattfield`: the annotated field to search using `patt`, if your corpus contains multiple annotated fields. Most corpora only contain one. Defaults to the first (or only) annotated field defined.
- `pattgapdata`: (Corpus Query Language only) Data (TSV, tab-separated values) to put in gaps in query. Show details You may leave 'gaps' in the double-quoted strings in your query that can be filled in from tabular data. The gaps should be denoted by @@, e.g. [lemma="@@"] or [word="@@cat"]. For each row in your TSV data, will fill in the row data in the gaps. The queries resulting from all the rows are combined using OR. For example, if your query is "The" "@@" "@@" and your TSV data is "white\tcat\nblack\tdog", this will execute the query ("The" "white" "cat") | ("The" "black" "dog"). Please note that if you want to pass a large amount of data, you should use a POST request as the amount of data you can pass in a GET request is limited (with opinions on a safe maximum size varying between 255 and 2048 bytes). Large amounts of data
- `filterlang`: filter language to use for `filter`. Defaults to `luceneql`. `contextql` is also supported, but very limited. More options may be added in the future.
- `docpid`: filter on a single document pid.
- `maxretrieve`: Maximum number of hits to retrieve. `-1` means "no limit". Also affects documents-containing-pattern queries and grouped-hits queries. Default configurable. Very large values (millions, or unlimited) may cause server problems.
- `maxcount`: Maximum number of hits to count. `-1` means "no limit". Default configurable. Even when BlackLab stops retrieving hits, it still keeps counting them. For large results sets this may take a long time.
- `usecontent`: `fi` or `orig`. `fi` (default) uses the forward index to reconstruct document content (for snippets and concordances; inline tags are lost in the process), `orig` uses the original XML from the content store (slower but more accurate).<br/>**NOTE:** using the original content may cause problems with well-formedness; these are fixed automatically, but the fix may result in inline tags in strange places (e.g. a start-sentence tag that is not at the start of the sentence anymore) 
- `calc`: specify the value `colloc` to calculate collocations (frequency lists of words near hits). Experimental feature.

## Success Response

**Code** : `200 OK`

### Content examples

```json

```

## TODO

- This endpoint does a lot of different things, producing different responses. This can be confusing. Maybe we should separate the different uses into multiple endpoints.
