# Corpus Query Language

BlackLab supports Corpus Query Language, a full-featured query language introduced by the IMS Corpus WorkBench (CWB) and also supported by the Lexicom Sketch Engine. It is a standard and powerful way of searching corpora.

The basics of Corpus Query Language is the same in all three projects, but in there are a few minor differences in some of the more advanced features, as well as some features that are exclusive to some projects. For most queries however, this will not be an issue.

This page will introduce the query language and show all features that BlackLab supports. If you want to learn even more about CQL, see [CWB CQP Query Language Tutorial](http://cwb.sourceforge.net/files/CQP_Tutorial/ "http://cwb.sourceforge.net/files/CQP_Tutorial/") and [Sketch Engine Corpus Query Language](https://www.sketchengine.co.uk/documentation/corpus-querying/ "https://www.sketchengine.co.uk/documentation/corpus-querying/").

* <a href="#cql-support">CQL support</a>
    * <a href="#supported-features">Supported features</a>
    * <a href="#differences-cwb">Differences from CWB</a>
    * <a href="#unsupported">(Currently) unsupported features</a>
* <a href="#using-cql">Using Corpus Query Language</a>
    * <a href="#matching-tokens">Matching tokens</a>
    * <a href="#sequences">Sequences</a>
    * <a href="#regex-operators-tokens">Regular expression operators on tokens</a>
    * <a href="#case-diacritics-sensitivity">Case- and diacritics-sensitivity</a>
    * <a href="#matching-xml-elements">Matching XML elements</a>
    * <a href="#label-tokens-capturing-groups">Labels, capturing groups</a>
    * <a href="#global-constraints">Global constraints</a>

<a id="cql-support"></a>

## CQL support
For those who already know CQL, here's a quick overview of the extent of BlackLab's support for this query language. If you a feature we don't support yet is important to you, please let us know. If it's quick to add, we may be able to help you out.

<a id="supported-features"></a>

### Supported features ###
BlackLab currently supports (arguably) most of the important features of Corpus Query Language:

* Matching on token annotations (also called properties or attributes), using regular expressions and `=`, `!=`, `!`. Example: `[word="bank"]` (or just `"bank"`)
* Case/accent sensitive matching. Note that, unlike in CWB, case-INsensitive matching is currently the default. To explicitly match case-/accent-insensitively, use `"(?i)..."`. Example: `"(?-i)Mr\." "(?-i)Banks"`
* Combining criteria using `&`, `|` and `!`. Parentheses can also be used for grouping. Example: `[lemma="bank" & pos="V"]`
* Matchall pattern `[]` matches any token. Example: `"a" [] "day"`
* Regular expression operators `+`, `*`, `?`, `{n}`, `{n,m}` at the token level. Example: `[pos="ADJ"]+`
* Sequences of token constraints. Example: `[pos="ADJ"] "cow"`
* Operators `|`, `&` and parentheses can be used to build complex sequence queries. Example: `"happy" "dog" | "sad" cat"`
* Querying with tag positions using e.g. `<s>` (start of sentence), `</s>` (end of sentence), `<s/>` (whole sentence) or `<s> ... </s>` (equivalent to `<s/> containing ...`). Example: `<s> "The" `. XML attribute values may be used as well, e.g. `<ne type="PERS"/>` ("named entities that are persons").
* Using `within` and `containing` operators to find hits inside another set of hits. Example: `"you" "are" within <s/>`
* Using an anchor to capture a token position. Example: `"big" A:[]`. Captured matches can be used in global constraints (see next item) or processed separately later (using the Java interface; capture information is not yet returned by BlackLab Server). Note that BlackLab can actually capture entire groups of tokens as well, similarly to regular expression engines.
* Global constraints on captured tokens, such as requiring them to contain the same word. Example: `"big" A:[] "or" "small" B:[] :: A.word = B.word`

See below for features not in this list that may be added soon, and let us know if you want a particular feature to be added.

<a id="differences-cwb"></a>

### Differences from CWB ###
BlackLab's CQL syntax and behaviour differs in a few small ways from CWBs. In future, we'll aim towards greater compliance with CWB's de-facto standard (with some extra features and conveniences).

For now, here's what you should know:

* Case-insensitive search is currently the default in BlackLab, although you can change this if you wish. CWB and Sketch Engine use case-sensitive search as the default. We may change our default in a future major version.  
  If you want to switch case/diacritics sensitivity, use `"(?-i).."` (case sensitive) or `"(?i).."` (case insensitive, usually the default). CWBs `%cd` flags for setting case/diacritics-sensitivity are not (yet) supported, but will be added.
* If you want to match a string literally, not as a regular expression, use backslash escaping: `"e\.g\."`. `%l` for literal matching is not yet supported, but will be added.
* BlackLab supports result set manipulation such as: sorting (including on specific context words), grouping/frequency distribution, subsets, sampling, setting context size, etc. However, these are supported through the REST and Java APIs, not through a command interface like in CWB. See [BlackLab Server overview](blacklab-server-overview.html)).
* Querying XML elements and attributes looks natural in BlackLab: `<s/>` means "sentences", `<s>` means "starts of sentences", `<s type='A'>` means "sentence tags with a type attribute with value A". This natural syntax differs from CWBs in some places, however, particularly when matching XML attributes. While we believe our syntax is the superior one, we may add support for the CWB syntax as an alternative.  
  We only support literal matching of XML attributes at the moment, but this will be expanded to full regex matching.
* In global constraints (expressions occurring after `::`), only literal matching (no regex matching) is currently supported. Regex matching will be added soon. For now, instead of `A:[] "dog" :: A.word = "happy|sad"`, use `"happy|sad" "dog"`.  
* To expand your query to return whole sentences, use `<s/> containing (...)`. We don't yet support CWBs `expand to`, `expand left to`, etc., but may add this in the future.
* The implication operator `->` is currently only supported in global constraints (expressions after the `::` operator), not in a regular token constraints. We may add this if there's demand for it.
* We don't support the `@` anchor and corresponding `target` label; use a named anchor instead. If someone makes a good case for it, we will consider adding this feature.
* backreferences to anchors only work in global constraints, so this doesn't work: `A:[] [] [word = A.word]`. Instead, use something like: `A:[] [] B:[] :: A.word = B.word`. We hope to add support for these in the near future, but our matching approach may not allow full support for this in all cases.

<a id="unsupported"></a>

### (Currently) unsupported features ###
The following features are not (yet) supported:

* `intersection`, `union` and `difference` operators. These three operators will be added in the future. For now, the first two can be achieved using `&` and `|` at the sequence level, e.g. `"double" [] & [] "trouble"` to match the intersection of these queries, i.e. "double trouble" and `"happy" "dog" | "sad "cat"` to match the union of "happy dog" and "sad cat".
* `_` meaning "the current token" in token constraints. We will add this soon.
* `lbound`, `rbound` functions to get the edge of a region. We will probably add these.
* `distance`, `distabs` functions and `match`, `matchend` anchor points (sometimes used in global constraints). We will see about adding these.
* using an XML element name to mean 'token is contained within', like `[(pos = "N") & !np]` meaning "noun NOT inside in an <np/> tag". We will see about adding these.
* a number of less well-known features. If people ask, we will consider adding them.

<a id="using-cql"></a>

## Using Corpus Query Language

<a id="matching-tokens"></a>

### Matching tokens

Corpus Query Language is a way to specify a "pattern" of tokens (i.e. words) you're looking for. A simple pattern is this one:

	[word="man"]

This simply searches for all occurrences of the word "man". If your corpus includes the per-word properties lemma (i.e. headword) and pos (part-of-speech, i.e. noun, verb, etc.), you can query those as well. For example, to find a form of word "search" used as a noun, use this query:

	[lemma="search" & pos="NOU"]

This query would match "search" and "searches" where used as a noun. (Of course, your data may contain slightly different part-of-speech tags.)

The first query could be written even simpler without brackets, because "word" is the default property:

	"man"

You can use the "does not equal" operator (!=) to search for all words except nouns:

	[pos != "NOU"]

The strings between quotes can also contain wildcards, of sorts. To be precise, they are [regular expressions](http://en.wikipedia.org/wiki/Regular_expression), which provide a flexible way of matching strings of text. For example, to find "man" or "woman", use:

	"(wo)?man"

And to find lemmata starting with "under", use:

	[lemma="under.\*"]

Explaining regular expression syntax is beyond the scope of this document, but for a complete overview, see [regular-expressions.info](http://www.regular-expressions.info/).

<a id="sequences"></a>

### Sequences

Corpus Query Language allows you to search for sequences of words as well (i.e. phrase searches, but with many more possibilities). To search for the phrase "the tall man", use this query:

	"the" "tall" "man"

It might seem a bit clunky to separately quote each word, but this allow us the flexibility to specify exactly what kinds of words we're looking for. For example, if you want to know all single adjectives used with man (not just "tall"), use this:

	"an?|the" [pos="ADJ"] "man"

This would also match "a wise man", "an important man", "the foolish man", etc.

<a id="regex-operators-tokens"></a>

### Regular expression operators on tokens

Corpus Query Language really starts to shine when you use the regular expression operators on whole tokens as well. If we want to see not just single adjectives applied to "man", but multiple as well:

	"an?|the" [pos="ADJ"]+ "man"

This query matches "a little green man", for example. The plus sign after [pos="ADJ"] says that the preceding part should occur one or more times (similarly, \* means "zero or more times", and ? means "zero or one time").

If you only want matches with two or three adjectives, you can specify that too:

	"an?|the" [pos="ADJ"]{2,3} "man"

Or, for two or more adjectives:

	"an?|the" [pos="ADJ"]{2,} "man"

You can group sequences of tokens with parentheses and apply operators to the whole group as well. To search for a sequence of nouns, each optionally preceded by an article:

	("an?|the"? [pos="NOU"])+

This would, for example, match the well-known palindrome "a man, a plan, a canal: Panama!" (A note about punctuation: in BlackLab, punctuation tends to not be indexed as a separate token, but as a property of a word token - CWB and Sketch Engine on the other hand tend to index punctuation as a separate token instead. You certainly could choose to index punctuation as a separate token in BlackLab, by the way -- it's just not commonly done. Both approaches have their advantages and disadvantages, and of course the choice affects how you write your queries.)

<a id="case-diacritics-sensitivity"></a>

### Case- and diacritics sensitivity

CWB and Sketch Engine both default to (case- and diacritics) sensitive search. That is, they exactly match upper- and lowercase letters in your query, plus any accented letters in the query as well. BlackLab, on the contrary, defaults to \*IN\*sensitive search (although this default can be changed if you like). To match a pattern sensitively, prefix it with "(?-i)":

	"(?-i)Panama"

If you've changed the default search to sensitive, but you wish to match a pattern in your query insensitively, prefix it with "(?i)":

	[pos="(?i)nou"]

Although BlackLab is capable of setting case- and diacritics-sensitivity separately, it is not yet possible from Corpus Query Language. We may add this capability if requested.

<a id="matching-xml-elements"></a>

### Matching XML elements

Corpus Query Language allows you to find text in relation to XML elements that occur in it. For example, if your data contains sentence tags, you could look for sentences starting with "the":

	<s>"the"

Similarly, to find sentences ending in "that", you would use:

	"that"</s>

You can also search for words occurring inside a specific element. Say you've run named entity recognition on your data and all person names are surrounded with <person\>...</person\> tags. To find the word "baker" as part of a person's name, use:

	"baker" within <person/>

Note that forward slash at the end of the tag. This way of referring to the element means "the whole element". Compare to <person\>, which means "the element's open tag", and </person\>, which means "the element's close tag".

The above query will just match the word "baker" as part of a person's name. But you're likely more interested in the entire name that contains the word "baker". So, to find those full names, use:

	<person/> containing "baker"

Or, if you simply want to find all persons, use:

	<person/>

As you can see, the XML element reference is just another query that yields a number of matches. So as you might have guessed, you can use "within" and "containing" with any other query as well. For example:

	([pos="ADJ"]+ containing "tall") "man"

will find adjectives applied to man, where one of those adjectives is "tall".

<a id="label-tokens-capturing-groups"></a>

### Labeling tokens, capturing groups

Just like in regular expressions, it is possible to "capture" part of the match for your query in a "group".

CWB and Sketch Engine offer similar functionality, but instead of capturing part of the query, they label a single token. BlackLab's functionality is very similar but can capture a number of tokens as well.

Example:

	"an?|the" Adjectives:[pos="ADJ"]+ "man"

This will capture the adjectives found for each match in a captured group named "Adjectives". BlackLab also supports numbered groups:

	"an?|the" 1:[pos="ADJ"]+ "man"

<a id="global-constraints"></a>

### Global constraints

If you tag certain tokens with labels, you can also apply "global constraints" on these tokens. This is a way of relating different tokens to one another, for example requiring that they correspond to the same word:

    A:[] "by" B:[] :: A.word = B.word
    
This would match "day by day", "step by step", etc.
