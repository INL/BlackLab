# Searching corpora

A corpus retrieval engine allows you to search for specific patterns in a corpus (a large body of text). Corpora (plural of corpus) often also include linguistic annotation: each word is 'tagged' with its lemma and its part of speech.

So for example, the word 'chickens' would be tagged with the lemma 'chicken' and the part of speech '(plural) noun'. A corpus retrieval engine allows you to query the text based on these annotations, and to construct fairly complex queries combining several different properties.

Example: you might want to search for a form of the word 'chicken' (singular or plural, or perhaps as a verb, as in 'to chicken out') preceded by one or more adjectives ('small chicken', 'black spotted chicken', etc.). 

A corpus retrieval engine allows you to specify and run queries like this. The above query might be put in Corpus Query Language like this:

	[pos="adj"]+ [lemma="chicken"]
	
You can read this as "one or more adjectives following by a word whose lemma is 'chicken' ".

Even if your corpus does not include lemma /part-of-speech tagging, you can still benefit from other features that a corpus retrieval engine provides, such as sorting hits on the left or right context around the hit, or grouping on hit text.

Suggested reading:

- [List of features](features.html)
- [Getting started](getting-started.html)
- [Frequently Asked Questions](faq.html)
