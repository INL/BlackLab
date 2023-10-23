---
sidebarDepth: 2
---

# BlackLab Corpus Query Language (BCQL)

<!-- TODO: unary negation operator -->

BlackLab Corpus Query Language or BCQL is a powerful query language for text corpora.

It is a dialect of the [CQP Query Language](http://cwb.sourceforge.net/files/CQP_Tutorial/ "http://cwb.sourceforge.net/files/CQP_Tutorial/") introduced by the IMS Corpus WorkBench (CWB). Several other corpus engines support a similar language, such as the [Lexicom Sketch Engine](https://www.sketchengine.co.uk/documentation/corpus-querying/ "https://www.sketchengine.co.uk/documentation/corpus-querying/"). The various dialects are very similar, but differ in some of the more advanced features.

This page will introduce BCQL and show the features that BlackLab supports.

BlackLab started out as purely a token-based corpus engine. The next section shows BCQL's token-based features. After that, we will look at (syntactic) relations querying. Finally, we compare BCQL to CQP, so users familiar with that dialect can avoid common pitfalls.

## Token-based querying

### Matching a token

With BCQL you can specify a pattern of tokens (i.e. words) you're looking for.

A simple such pattern is:

	[word='man']

This simply searches for all occurrences of the word _man_.

Each corpus has a default annotation; usually _word_. Using this fact, this query can be written even simpler:

	'man'

Note that double and single quotes are interchangeable in BCQL (which is not true for all dialects). In this document we will use single quotes.

#### Multiple annotations

If your corpus includes the per-word annotations _lemma_ (i.e. headword) and _pos_ (part-of-speech, i.e. noun, verb, etc.), you can query those as well. 

For example:

	[lemma='search' & pos='noun']

This query would match _search_ and _searches_ where used as a noun. (your data may use different part-of-speech tags, of course)

#### Negation

You can use the "does not equal" operator (!=) to search for all words except nouns:

	[pos != 'noun']

#### Regular expressions

The strings between quotes can also contain "wildcards", of sorts. To be precise, they are [regular expressions](http://en.wikipedia.org/wiki/Regular_expression), which provide a flexible way of matching strings of text. For example, to find _man_ or _woman_ (in the default annotation _word_), use:

	'(wo)?man'

And to find lemmas starting with _under_, use:

	[lemma='under.*']

Explaining regular expression syntax is beyond the scope of this document, but for a complete overview, see [regular-expressions.info](http://www.regular-expressions.info/).

#### Matching any token

Sometimes you want to match any token, regardless of its value.

Of course, this is usually only useful in a larger query, as we will explore next. But we'll introduce the syntax here.

To match any token, use the match-all pattern, which is just a pair of empty square brackets:

    []

### A sequence of tokens

You can search for sequences of words as well (i.e. phrase searches, but with many more possibilities). To search for the phrase _the tall man_, use this query:

	'the' 'tall' 'man'

It might seem a bit clunky to separately quote each word, but this allows us the flexibility to specify exactly what kinds of words we're looking for.

For example, if you want to know all single adjectives used with man (not just _tall_), use this:

	'an?|the' [pos='ADJ'] 'man'

This would also match _a wise man_, _an important man_, _the foolish man_, etc.

If we don't care about the part of speech between the article and _man_, we can use the match-all pattern we showed before:

    'an?|the' [] 'man'

This way we might match something like _the cable man_ as well as _a wise man_.

### Regular expression operators on tokens

Really powerful token-based queries become possible when you use the regular expression operators on whole tokens as well. If we want to see not just single adjectives applied to _man_, but multiple as well:

	[pos='ADJ']+ 'man'

This query matches _little green man_, for example. The plus sign after `[pos='ADJ']` says that the preceding part should occur one or more times (similarly, `*` means "zero or more times", and `?` means "zero or once").

If you only want matches with exactly two or three adjectives, you can specify that too:

	[pos='ADJ']{2,3} 'man'

Or, for two or more adjectives:

	[pos='ADJ']{2,} 'man'

You can group sequences of tokens with parentheses and apply operators to the whole group as well. To search for a sequence of nouns, each optionally preceded by an article:

	('an?|the'? [pos='NOU'])+

This would, for example, match the well-known palindrome _a man, a plan, a canal: Panama!_ (provided the punctuation marks were not indexed as separate tokens)

### Case- and diacritics sensitivity

BlackLab defaults to (case and diacritics) _insensitive_ search. That is, it ignores differences in upper- and lowercase, as well as diacritical marks (accented characters). So searching for `'panama'` will also find _Panama_.

To match a pattern sensitively, prefix it with `(?-i)`:

	'(?-i)Panama'

::: details compare to other corpus engines
CWB and Sketch Engine both default to _sensitive_ search.
:::


### Matching XML elements

Your input data may contains "spans": marked regions of text, such as paragraphs, sentences, named entities, etc. If your input data is XML these may be XML elements, but they may also be marked in other ways. Non-XML formats may also define spans.

Finding text in relation to these spans is done using an XML-like syntax, regardless of the exact input data format.

#### Finding spans

If you want to find all the sentence spans in your data:

    <s/>

Note that forward slash before the closing bracket. This way of referring to the span means "the whole span". Compare this to `<s>`, which means "the start of the span", and `</s>`, which means "the end of the span".

So to find only the starts of sentences, use:

    <s>

This would find zero-length hits at the position before the first word. Similarly, `</s>` finds the ends of sentences. Not very useful, but we can combine these with other queries.

#### Words at the start or end of a span

More useful might be to find the first word of each sentence:

    <s> []

or sentences ending in _that_:

    'that' </s>

(Note that this assumes the period at the end of the sentence is not indexed as a separate token - if it is, you would use `'that' '.' </s>` instead)

#### Words inside a span

You can also search for words occurring inside a specific span. Say you've run named entity recognition on your data, so all names of people are tagged with the `person` span. To find the word _baker_ as part of a person's name, use:

	'baker' within <person/>

The above query will just match the word _baker_ as part of a person's name. But you're likely more interested in the entire name that contains the word _baker_. So, to find those full names, use:

	<person/> containing 'baker'

#### Other uses for within and containing

As you might have guessed, you can use `within` and `containing` with any other query as well. For example:

	([pos='ADJ']+ containing 'tall') 'man'

will find adjectives applied to man, where one of those adjectives is _tall_.

### Labeling tokens, capturing groups

Just like in regular expressions, it is possible to "capture" part of the match for your query as a named group. Everything you capture is returned with the hit in a response section called _match info_.

Example:

	'an?|the' A:[pos='ADJ'] 'man'

The adjective part of the match will be captured in a group named _A_.

You can capture multiple words as well:

	'an?|the' adjectives:[pos='ADJ']+ 'man'

This will capture the adjectives found for each match in a captured group named _adjectives_.

The capture name can also just be a number:

	'an?|the' 1:[pos='ADJ']+ 'man'

::: details Compared to other corpus engines
CWB and Sketch Engine offer similar functionality, but instead of capturing part of the match, they label a single token.

BlackLab can capture a span of tokens of any length, capture relations and spans with all their details, and even capture lists of relations, such as all relations in a sentence (relations are described later in this document).
:::

::: tip Spans are captured automatically
If your query involves spans like `<s/>`, it will automatically be captured under the span name (`s` in this case). You can override the capture name by specifying it in the query, e.g. `A:<s/>`.
:::

### Capture constraints

If you tag certain tokens with labels, you can also apply "capture constraints" (also known as "global constraints") 
on these tokens. This is a way of relating different tokens to one another, for example requiring that they correspond 
to the same word:

    A:[] 'by' B:[] :: A.word = B.word

This would match _day by day_, _step by step_, etc.

#### Functions

You can also use a few special functions in capture constraints. For example, ensure that words occur in the right order:

    (<s> containing A:'cat') containing B:'fluffy' :: start(B) < start(A)

Here we find sentences containing both _cat_ and _fluffy_ (in some order), but then require that _fluffy_ occurs before _cat_.

Of course this particular query would be better expressed as `<s/> containing 'fluffy' []* 'cat'`. As a general rule, 
capture constraints can be a bit slower, so only use them when you need to.

#### Local capture constraints

Unlike most other corpus engines, BlackLab allows you to place capture constraints inside a parenthesized expression.
Be careful that the constraint only refers to labels that are captured inside the parentheses, though!

This is valid and would match _over and over again_:

    (A:[] 'and' B:[] :: A.word = B.word) 'again'

This is NOT valid (may not produce an error, but the results are undefined):

    A:[] ('and' B:[] :: A.word = B.word) 'again'   # BAD




## Relations querying

::: tip Supported from v4.0
Indexing and searching relations is supported starting from BlackLab 4.0 (and current development snapshots).
:::

If your input data contains relations, such as dependency relations, you can query those as well. One advantage of this style of querying is that it's much easier to find nonadjacent words related to one another, or two related words regardless of what order they occur in.

Querying relations is essentially done by building a partial tree of relations constraints.

### An example dependency tree

Let's use an example to illustrate the various querying options. Here's a simple dependency tree for the phrase _I have a fluffy cat_:

```
      |
     have
    /    \
 (subj)   (obj)
 /          \
I            cat
           /   |
        (det)(amod)
        /      |
       a     fluffy 
```

### Finding specific relation types

We might want to find object relations in our data. We can do this as follows:

    _ -obj-> _

This will find _have a fluffy cat_ (the span of text covering the two ends of the relation), with a match info group named for the relation type (_obj_) containing the relation details between _have_ and _cat_.

The two `_` marks in the query simply means we only care about the relation type, not the source or target of the relation. If we specifically want to find instances where _cat_ is the object of the sentence, we can use this instead:

    _ -obj-> 'cat'

So you can see that the token-based queries described previously are still useful here.

::: details Can I use [] instead of _ ?

As explained above, `_` in a relation expression means "any source or target". You might be tempted to use `[]` instead, especially if you know your relations always have a single-token source and target. This works just fine, but it's a bit slower (it has to double-check that source and target are actually of length 1), so we recommend sticking with `_`.

(the actual equivalent of `_` here is `[]*` (zero or more tokens with no restrictions), but that makes for less readable queries)

:::

### A note on terminology

For dependency relations, linguists call the left side the _head_ of the relation and the right side the _dependent_. However, because dependency relations aren't the only class of relation, and because the term _head_ can be a bit confusing (there's also the "head of an arrow", but that's the other end!), we will use _source_ and _target_.

When talking about tree structures, we will also use _parent_ and _child_.

| Context                   | Terms                |
|---------------------------|----------------------|
| Dependency relations      | `head --> dependent` |
| Relations in BlackLab     | `source --> target`  |
| Searching tree structures | `parent --> child`   |

### Finding relation types using regular expressions

We can specify the relation type as a regular expression as well. To find both subject and object relations, we could use:

    _ -subj|obj-> _

or:

    _ -.*bj-> _

If you find it clearer, you can use parentheses around the regular expression:

    _ -(subj|obj)-> _

With our example tree, the above queries will find all subject relations and all object relations. Each hit will have one relation in the match info. To find multiple relations per hit, read on.

::: details Relation classes

When indexing relations in BlackLab, you assign them a _class_, a short string indicating what family of relations it belongs to. For example, you could assign the class string `dep` to dependency relations. An `obj` relation would become `dep::obj`.

To simplify things, `dep` is the default relation class in BlackLab. If you index relations without a class, they will automatically get the `dep` class. Similarly, when searching, if you don't specify a class, `dep::` will be prepended to the relation type. So if you're not indexing different classes of relations, you can just ignore the classes.

:::

### Root relations

A dependency tree has a single root relation. A root relation is special relation that only has a target and no source. Its relation type is usually just called _root_. In our example, the root points to the word _have_.

You can find root relations with a special unary operator:

    ^--> _

This will find all root relations. The details for the root relation will be returned in the match info.

Of course you can place constraints on the target of the root relation as well:

    ^--> 'have'

This will only find root relations pointing to the word _have_.

### Finding two relations with the same source

What if we want to find the subject and object relations of a sentence, both linked to the same source (the verb in the sentence)? We can do that using a semicolon to separate the two _target constraints_ (or _child constraints_):

    _ -subj-> _ ;
      -obj-> _

As you can see, the source or parent is specified only once at the beginning. Then you may specify one or more target constraints (a relation type plus target, e.g. `-subj-> _`), separated by semicolons.

The above query will find hits covering the words involved in both relations, with details for the two relations in the match info of each hit. In our example, it would find the entire sentence _I have a fluffy cat_.

::: details Target constraint uniqueness

Note that when matching multiple relations with the same source this way, BlackLab will enforce that they are unique. That is, two target constraints will only match two different relations.

:::

### Negative child constraints

You may want to have negative constraints, such as making sure that _dog_ is not the object of the sentence. This can be done by prefixing the relation operator with `!`:

    _  -subj-> _ ;
      !-obj-> 'dog'

Note that this is different from :

    _  -subj-> _ ;
       -obj-> [word != 'dog']

The second query requires an object relation where the target is a word other than _dog_; that is, the object relation must exist. By contrast, in the first case, we only require that there exists no object relation with the target _dog_, so this might match sentences without an object as well as sentences with an object that is not _dog_. 

### Searching over multiple levels in the tree

What if we want to query over multiple levels of the tree? For example, we want to find sentences where the target of the `subj` relation is the source of an `amod` relation pointing to _fluffy_, such as in our example tree.   

    _ -subj-> _ -amod-> 'fluffy'

We can combine the techniques as well, for example if we also want to find the object of the sentence like before:

    _ -subj-> (_ -amod-> _) ;
      -obj-> _

As you can see, the value of the expression `(_ -amod-> _)` is actually the _source_ of the `amod` relation, so we can easily use it as the target of the `subj` relation.

The `-..->` operator is right-associative (as you can see from the first example), but we do need parentheses here, or the parent of the `-obj->` relation would be ambiguous.

### Limitation: descendant search

One current limitation compared to dedicated treebank systems is the lack
of support for finding descendants that are not direct children.

For example, if we want to look for sentences with the verb _have_ and the word _fluffy_ somewhere as an adjectival modifier in that sentence, we can't query something like this:

    ^--> 'have' -->> -amod-> 'fluffy'   # DOES NOT WORK

Instead, we have to know how many nodes are between _have_ and _fluffy_, e.g. this does work:

    ^--> 'have' --> _ -amod-> 'fluffy'

Supporting arbitrary descendant search with decent performance is a challenge that we may try to tackle in the future.

For now, you might be able to work around this limitation using a hybrid between token-based and relations querying, e.g.:

    (<s/> containing (^--> 'have')) containing (_ -amod-> 'fluffy')

### Advanced relations querying features

Most users won't need this, but they might come in handy in some cases.

#### Controlling the resulting span

As shown in the previous section, relation expressions return the source of the matching relation by default. But what if you want a different part of the relation?

For example, if we want to find targets of the _amod_ relation, we can do this:

    rspan(_ -amod-> _, 'target')

If we want the entire span covering both the source and the target (and anything in between):

    rspan(_ -amod-> _, 'full')

Note that _full_ is the default value for the second parameter, so this will work too:

    rspan(_ -amod-> _)

`rspan` supports another option: _all_ will return a span covering all of the relations matched by your query.

    rspan(_ -subj-> (_ -amod-> _) ; -obj-> _, 'all')

Note that BlackLab already adds this by default if your query matches any relations, so you generally don't need to specify it explicitly. Of course, if you specify _full_ or _target_, BlackLab won't change it to _all_.

#### Capturing all relations in a sentence

If you want to capture all relations in the sentence containing your match, use:

    rcapture('elephant' within <s/>, 's')

What actually happens here is that all relations in a captured span are returned as _rels_ in the match info. In this case, the sentence span in our query is automatically captured as _s_, but you can use any capture. So if you wanted to capture the relations in the preceding and following sentences as well, you could useuse:

    rcapture(A:(<s/> (<s/> containing 'elephant') <s/>), 'A')

You can pass a third parameter with the match info name for the list of captured relations (defaults to _rels_):

    rcapture('elephant' within <s/>, 's', 'relations')

If you only want to capture certain relations, you specify a fourth parameter that is a regular expression filter on the relation type. For example, to only capture relations in the `fam` class, use:

    rcapture('elephant' within <s/>, 's', 'rels', 'fam::.*')


## Advanced subjects

### Operator precedence

This is the precedence of the different CQL operators in BlackLab, from highest to lowest. The highest precedence operators "bind most tightly". See the examples below.

Inside token brackets `[ ]`:

| Operator | Description    | Associativity |
|----------|----------------|---------------|
| `!`      | logical not    | right-to-left |
| `=` `!=` | (not) equals   | left-to-right |
| `&` `\|` | logical and/or | left-to-right |

At the sequence level (i.e. outside token brackets):

| Operator                     | Description                      | Associativity |
|------------------------------|----------------------------------|---------------|
| `!`                          | logical not                      | right-to-left |
| `[ ]`                        | token brackets                   | left-to-right |
| `( )`                        | function call                    | left-to-right |
| `*` `+` `?`<br>`{n}` `{n,m}` | repetition                       | left-to-right |
| `:`                          | capture                          | right-to-left |
| `< />` `< >` `</ >`          | span (start/end)                 | left-to-right |
| `-..-> .. ; ..`<br>`^-->`    | child relations<br>root relation | right-to-left |
| `[] []`                      | sequence<br>(implied operator)   | left-to-right |
| `\|` `&`                     | union/intersection               | left-to-right |
| `within` `containing`        | position filter                  | right-to-left |
| `::`                         | capture constraint               | left-to-right |

NOTES:
- you can always use grouping parens `( )` (at either token or sequence level) to override this precedence.
- notice that `|` and `&` have the _same_ precedence; don't rely on `&` binding more tightly than `|` or vice versa, which you might be used to from other languages.

A few examples:

| Query                                           | Interpreted as                                                     |
|-------------------------------------------------|--------------------------------------------------------------------|
| `[word = 'can' & pos != 'verb']`                | `[ (word = 'can') & (pos != 'verb') ]`                             |
| `[pos = 'verb' \| pos = 'noun' & word = 'can']` | `[ (pos = 'verb' \| pos = 'noun') & word = 'can']`                 |
| `A:'very'+`                                     | `A:('very'+)`                                                      |
| `A:_ --> B:_`                                   | `(A:_) --> (B:_)`                                                  |
| `_ -obj-> _ -amod-> _`                          | `_ -obj-> (_ -amod-> _)`                                           |
| `!'d.*' & '.e.*'`                               | `(!'d.*') & '.e.*'`, meaning <br>`[word != 'd.*' & word = '.e.*']` |
| `'cow' within <pasture/> containing 'grass'`     | `'cow' within (<pasture/> containing 'grass')`                     |


### Supported features, differences from CWB

For those who already know CQL, here's a quick overview of the extent of BlackLab's support for this query language. If you a feature we don't support yet is important to you, please let us know. If it's quick to add, we may be able to help you out.

### Supported features ###

BlackLab currently supports (arguably) most of the important features of Corpus Query Language:

* Matching on token annotations, using regular expressions and `=`, `!=`, `!`. Example: `[word='bank']` (or just `'bank'`)
* Case/accent sensitive matching. Note that, unlike in CWB, case-INsensitive matching is currently the default. To explicitly match case-/accent-insensitively, use `'(?i)...'`. Example: `'(?-i)Mr\.' '(?-i)Banks'`
* Combining criteria using `&`, `|` and `!`. Parentheses can also be used for grouping. Example: `[lemma='bank' & pos='V']`
* Matchall pattern `[]` matches any token. Example: `'a' [] 'day'`
* Regular expression operators `+`, `*`, `?`, `{n}`, `{n,m}` at the token level. Example: `[pos='ADJ']+`
* Sequences of token constraints. Example: `[pos='ADJ'] 'cow'`
* Operators `|`, `&` and parentheses can be used to build complex sequence queries. Example: `'happy' 'dog' | 'sad' cat'`
* Querying with tag positions using e.g. `<s>` (start of sentence), `</s>` (end of sentence), `<s/>` (whole sentence) or `<s> ... </s>` (equivalent to `<s/> containing ...`). Example: `<s> 'The' `. XML attribute values may be used as well, e.g. `<ne type='PERS'/>` ("named entities that are persons").
* Using `within` and `containing` operators to find hits inside another set of hits. Example: `'you' 'are' within <s/>`
* Using an anchor to capture a token position. Example: `'big' A:[]`. Captured matches can be used in capture 
  constraints (see next item) or processed separately later (using the Java interface; capture information is not yet returned by BlackLab Server). Note that BlackLab can actually capture entire groups of tokens as well, similarly to regular expression engines.
* Capture constraints, such as requiring two captures to contain the same word. Example: `'big' A:[] 'or' 'small' B:[] :: A.word = B.word`

See below for features not in this list that may be added soon, and let us know if you want a particular feature to be added.

### Differences from CWB ###

BlackLab's CQL syntax and behaviour differs in a few ways from CWBs, although they are mostly lesser-used features.

For now, here's what you should know:

* Case-insensitive search is the default in BlackLab, while CWB and Sketch Engine use case-sensitive search as the default. If you want to match a term case-sensitively, use `'(?-i)..'` or `'(?c)..'`.
* If you want to match a string literally, not as a regular expression, use backslash escaping: `'e\.g\.'`. 
* BlackLab supports result set manipulation such as: sorting (including on specific context words), grouping/frequency distribution, subsets, sampling, setting context size, etc. However, these are supported through the REST and Java APIs, not through a command interface like in CWB. See [BlackLab Server overview](/server/overview.md)).
* Querying XML elements and attributes looks natural in BlackLab: `<s/>` means "sentences", `<s>` means "starts of sentences", `<s type='A'>` means "sentence tags with a type attribute with value A". This natural syntax differs from CWBs in some places, however, particularly when matching XML attributes.
* In capture constraints (expressions occurring after `::`), only literal matching (no regex matching) is currently supported.
* To return whole sentences as the context of hits, pass `context=s` to BLS.
* The implication operator `->` is currently only supported in capture constraints (expressions after the `::` operator), not in a regular token constraints.
* We don't support the `@` anchor and corresponding `target` label; use a named anchor instead.
* backreferences to anchors only work in capture constraints, so this doesn't work: `A:[] [] [word = A.word]`. Instead, use something like: `A:[] [] B:[] :: A.word = B.word`.
* Instead of CWBs `intersection`, `union` and `difference` operators, BlackLab supports the `&`, `|` and `!` operators at the top-level of the query, e.g. `('double' [] & [] 'trouble')` to match the intersection of these queries, i.e. 'double trouble' and `('happy' 'dog' | 'sad 'cat')` to match the union of 'happy dog' and 'sad cat'. Difference can be achieved by combining `!` and `&`, e.g. `('happy' [] & !([] 'dog'))` to match 'happy' followed by anything except 'dog' (although this is better expressed as `'happy' [word != 'dog']`).

### (Currently) unsupported features ###

Some features that are not (yet) supported:

* `lbound`, `rbound` functions to get the edge of a region. You can use `<s>` to get all starts-of-sentences or `</s>` to get all ends-of-sentences, however.
* `distance`, `distabs` functions and `match`, `matchend` anchor points (sometimes used in capture constraints).
* using an XML element name to mean 'token is contained within', like `[(pos = 'N') & !np]` meaning "noun NOT inside in an `<np/>` tag".
* a number of less well-known features.

If people ask about missing features, we're happy to work with them to see if it could be added.
