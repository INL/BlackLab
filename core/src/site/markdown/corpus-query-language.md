# Corpus Query Language

BlackLab supports Corpus Query Language, a full-featured query language introduced by the Corpus WorkBench (CWB) and also supported by the Lexicom Sketch Engine. It is a standard and powerful way of searching corpora.

The basics of Corpus Query Language is the same in all three projects, but in there are a few minor differences in some of the more advanced features, as well as some features that are exclusive to some projects. For most queries however, this will not be an issue.

This page will introduce the query language and show all features that BlackLab supports. If you want to compare, see [CWB CQL](http://cwb.sourceforge.net/files/CQP_Tutorial/ "http://cwb.sourceforge.net/files/CQP_Tutorial/") and [Sketch Engine CQL](http://www.sketchengine.co.uk/documentation/wiki/SkE/CorpusQuerying "http://www.sketchengine.co.uk/documentation/wiki/SkE/CorpusQuerying").

## Summary: supported features
Here's a quick overview of our CQL support. If you a feature we don't support yet is important to you, please let us know. If it's quick to add, we may be able to help you out.

BlackLab currently supports the following parts of Corpus Query Language:
* `[word="bank"]` or just `"bank"`: filtering on token annotations (also called properties), using regular expressions and `=`, `!=`, `!` and parentheses for grouping.
* `[word="(?i)bank.*"]`: case/accent insensitive filtering (NOTE: this is currently the default, unlike in CWB)
* `[word="(?-i)bank.*"]`: case/accent sensitive filtering
* `[lemma="bank" & pos="V"]`: combining annotation filters using & and |. Parentheses can also be used for grouping.
* `"a" [] "day"`: matchall pattern to match all tokens
* `[pos="ADJ"]+`: regular expression operators `+`, `*`, `?`, `{n}`, `{n,m}` at the token level
* `[pos="ADJ"] "cow"`: sequences of token constraints. `|`, `&` and parentheses can be used to build complex sequence queries.
* `<s> "The" `: querying with tag positions using `<tag>`, `</tag>`, `<tag/>` or `<tag> ... </tag>`. Attribute values may be queries as well.
* `"you" "are" within <s/>`: using `within` and `containing` operators to find hits inside another set of hits.
* `"big" A:[]`: using an anchor to capture a token position to process it separately later (using the Java interface; group information is not yet returned by BlackLab Server). Note that BlackLab can actually capture entire groups of tokens as well, like regular expressions can.
* `"big" A:[] "or" "small" B:[] :: A.word = B.word`: global constraints on captured tokens, such as requiring them to contain the same word.
*  (not through the query language, but through the BlackLab Server REST interface or the Java API)

While the future direction of Blacklab is probably aimed towards greater compliance to CWB's de-facto standard (with some added conveniences of our own), there are currently still a number of small differences from CWB:
* Case-insensitive search is currently the default in BlackLab, although you can change this if you wish. CWB and Sketch Engine use case-sensitive search as the default. We may change our default in a future major version.
* If you want to switching case/diacritics sensitivity, use `"(?-i).."` (case sensitive) or `"(?i).."` (case insensitive, usually the default). CWBs `%cd` flags for setting case/diacritics-sensitivity are not (yet) supported, but will be added.
* If you want to match a string literally, not as a regular expression, use backslash escaping: `"e\.g\."`. The `%l` for literal matching is not yet supported, but will be added.
* BlackLab supports result set manipulation such as: sorting (including on specific context words), grouping/frequency distribution, subsets, sampling, setting context size, etc. However, these are supported through the REST and Java APIs, not through a command language like CWBs. See [BlackLab Server overview](blacklab-server-overview.html)).
* Querying XML elements and attributes looks natural in BlackLab: `<s/>` means "sentences", `<s>` means "starts of sentences", `<s type='A'>` means "sentence tags with a type attribute with value A". This natural syntax differs from CWBs, however. We may add the CWB syntax as an alternative in the future. We only support literal matching of XML attributes at the moment, but this can easily be expanded to full regex matching.
* In global constraints (expressions occurring after `::`), only literal matching (no regex matching) is currently supported. So instead of `A:[] "dog" :: A.word = "happy|sad"`, use `"happy|sad" "dog"`. We will implement regex matching for this too later. 
* To expand your query to return whole sentences, use `<s/> containing (...)`. We don't yet support CWBs `expand to`, `expand left to`, etc., but may add this in the future.
* We don't support the `@` anchor and corresponding `target` label; use a named anchor instead. If someone makes a good case for it, we may consider adding this feature.
* The implication operator `->` is currently only supported in global constraints (expressions after the `::` operator), not in a regular token constraints. We may add this if there's demand for it.
* backreferences to anchors only work in global constraints, so this doesn't work: `A:[] [] [word = A.word]`. Instead, use something like: `A:[] [] B:[] :: A.word = B.word`). We hope to add support for these in the future, but our matching approach may not allow full support for this in all cases.

(Currently) unsupported features:
* `intersection`, `union` and `difference` operators. You can achieve similar results using &, | and ! however. These shouldn't be too hard to add, and we will do that in the future.
* `distance`, `distabs` functions and `match`, `matchend` anchor points (sometimes used in global constraints). We will add these soon.
* `_` meaning "the current token" in token constraints. We will add these soon.
* using an element name to mean 'token is contained within', like `[(pos = "N") & !np]` meaning "noun NOT contained in a noun phrase". Instead, you could try `[pos="N"] & !([pos="N"] within <np/>)`. `lbound`, `rbound` functions to get the edge of a region. We will consider adding these (perhaps with a slightly different syntax that matches our existing XML element syntax)
* a number of less well-known features. If people ask, we will consider adding them.

## Using Corpus Query Language in BlackLab

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

The strings between quotes can also contain wildcards, of sorts. To be precise, they are [regular expressions](http://en.wikipedia.org/wiki/Regular_expression "http://en.wikipedia.org/wiki/Regular_expression"), which provide a flexible way of matching strings of text. For example, to find "man" or "woman", use:

	"(wo)?man"

And to find lemmata starting with "under", use:

	[lemma="under.\*"]

Explaining regular expression syntax is beyond the scope of this document, but for a complete overview, see [here](http://www.regular-expressions.info/ "http://www.regular-expressions.info/").

### Sequences

Corpus Query Language allows you to search for sequences of words as well (i.e. phrase searches, but with many more possibilities). To search for the phrase "the tall man", use this query:

	"the" "tall" "man"

It might seem a bit clunky to separately quote each word, but this allow us the flexibility to specify exactly what kinds of words we're looking for. For example, if you want to know all single adjectives used with man (not just "tall"), use this:

	"an?|the" [pos="ADJ"] "man"

This would also match "a wise man", "an important man", "the foolish man", etc.

### Token-level regular expression operators

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

### Case- and diacritics sensitivity

CWB and Sketch Engine both default to (case- and diacritics) sensitive search. That is, they exactly match upper- and lowercase letters in your query, plus any accented letters in the query as well. BlackLab, on the contrary, defaults to \*IN\*sensitive search (although this default can be changed if you like). To match a pattern sensitively, prefix it with "(?-i)":

	"(?-i)Panama"

If you've changed the default search to sensitive, but you wish to match a pattern in your query insensitively, prefix it with "(?i)":

	[pos="(?i)nou"]

Although BlackLab is capable of setting case- and diacritics-sensitivity separately, it is not yet possible from Corpus Query Language. We may add this capability if requested.

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

### Capturing groups

Just like in regular expressions, it is possible to "capture" part of the match for your query in a "group".

CWB and Sketch Engine offer similar functionality, but instead of capturing part of the query, they label a single token. BlackLab's functionality is very similar but can capture a number of tokens as well.

Example:

	"an?|the" Adjectives:[pos="ADJ"]+ "man"

This will capture the adjectives found for each match in a captured group named "Adjectives". BlackLab also supports numbered groups:

	"an?|the" 1:[pos="ADJ"]+ "man"

### Global constraints

If you tag certain tokens with labels, you can also apply "global constraints" on these tokens. This is a way of relating different tokens to one another, for example requiring that they correspond to the same word:

    A:[] "by" B:[] :: A.word = B.word
    
This would match "day by day", "step by step", etc.
