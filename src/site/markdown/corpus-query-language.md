# Corpus Query Language

BlackLab supports Corpus Query Language, a full-featured query language introduced by the Corpus WorkBench (CWB) and also supported by the Lexicom Sketch Engine. It is a standard and powerful way of searching corpora.

The basics of Corpus Query Language is the same in all three projects, but in there are a few minor differences in some of the more advanced features, as well as some features that are exclusive to some projects. For most queries however, this will not be an issue.

This page will introduce the query language and show all features that BlackLab supports. If you want to compare, see [CWB CQL](http://cwb.sourceforge.net/files/CQP_Tutorial/ "http://cwb.sourceforge.net/files/CQP_Tutorial/") and [Sketch Engine CQL](http://www.sketchengine.co.uk/documentation/wiki/SkE/CorpusQuerying "http://www.sketchengine.co.uk/documentation/wiki/SkE/CorpusQuerying").

## Matching tokens

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

## Sequences

Corpus Query Language allows you to search for sequences of words as well (i.e. phrase searches, but with many more possibilities). To search for the phrase "the tall man", use this query:

	"the" "tall" "man"

It might seem a bit clunky to separately quote each word, but this allow us the flexibility to specify exactly what kinds of words we're looking for. For example, if you want to know all single adjectives used with man (not just "tall"), use this:

	"an?|the" [pos="ADJ"] "man"

This would also match "a wise man", "an important man", "the foolish man", etc.

## Token-level regular expression operators

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

## Case- and diacritics sensitivity

CWB and Sketch Engine both default to (case- and diacritics) sensitive search. That is, they exactly match upper- and lowercase letters in your query, plus any accented letters in the query as well. BlackLab, on the contrary, defaults to \*IN\*sensitive search (although this default can be changed if you like). To match a pattern sensitively, prefix it with "(?c)":

	"(?c)Panama"

If you've changed the default search to sensitive, but you wish to match a pattern in your query insensitively, prefix it with "(?i)":

	[pos="(?i)nou"]

Although BlackLab is capable of setting case- and diacritics-sensitivity separately, it is not yet possible from Corpus Query Language. We may add this capability if requested.

## Matching XML elements

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

## Capturing groups

Just like in regular expressions, it is possible to "capture" part of the match for your query in a "group".

CWB and Sketch Engine offer similar functionality, but instead of capturing part of the query, they label a single token. BlackLab's functionality is very similar but can capture a number of tokens as well.

Example:

	"an?|the" Adjectives:[pos="ADJ"]+ "man"

This will capture the adjectives found for each match in a captured group named "Adjectives". BlackLab also supports numbered groups:

	"an?|the" 1:[pos="ADJ"]+ "man"

