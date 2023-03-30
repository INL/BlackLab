# Relations between words and word groups

(see GitHub issue [#405](https://github.com/INL/BlackLab/issues/405))

## Goal

We want to index (and search for) relations between two words, such as the dependency relation "verb X has object Y". In this case, we have two words, X and Y, and the `has_object` relation pointing from X to Y:

    X  ---has_object-->  Y

Example:

             Y <-o- X 
    Small   man   bites   large   dog.

For dependency relations, the most common case is that a word can point to multiple other words, but can only be pointed to by one other word. We don't want to limit the relations primitive to just dependency relations though, so our implementation will be able to handle any set of relations.

### Special relations

For dependency relations, there's a special relation called the `root` relation, which has a target (dep) but not a source (head). We will need to record this special case as an option in the payload.


### Terminology

For dependency relations, linguists call the start of the arrow X the _head_ of the relation and the end of the arrow Y the _dependent_.

However, because we don't want to limit the relations primitive to only dependency relations, and because the term _head_ can be confusing (there's also the "head of an arrow", but that's the other end), we will use _source_ for X and _target_ for Y in BlackLab.
 
Of course, in addition to the CQL extension functions described below, we could also include functions specifically suited for working with dependency relations, which would use the common terminology of _head_ and _dependent_.

### Relations beteween groups of words

Obviously it would also be very useful to be able to index relations between groups of words. For example, in the example above, the object of the sentence is not just "man" but actually "Small man".

Our implementation should allow us to store relations between groups of words as well, although full support for this might not be in the initial version.


## Document format and test data

The [CoNLL-U format](https://universaldependencies.org/format.html) is commonly used to encode texts with these kinds of dependency relations. We should support this format. We can use the [LassySmall](https://github.com/UniversalDependencies/UD_Dutch-LassySmall) corpus for testing.

Eventually we'll look at adding support for TEI with dependency relations as well.

## How to index relations

Indexing relations will mean changes to the index format. We will implement these changes in the integrated index format only; the classic external index format will not support them. We will simply change the integrated index format, so older integrated indexes will no longer be readable. This is okay as long as the integrated index format is still experimental.

### Unification with spans

A good way to index relations is similar to how we index spans such as `<s/>`. Spans are indexed at the starting token, with the end position stored in the payload.

We could index these in the same annotation spans are already indexed in (historically called `starttag`, but we will rename it to `_relation`). We'd have to be careful to make sure both can easily be distinguished from one another.

Better yet, we should generalize the notion of an "inline tag" such as `<s/>` we have right now to be indexed as a relation as well, i.e.:

      X ---starts_sentence_ending_at--> Y
    Small     man    bites    large    dog.

> **NOTE:** Unification also means changes to how we store the payload and how we index tag attributes (combining them all into one term string? probably provide a way to exclude unique id attributes then?). See below.

### Where to index relations

We could index relations at the source or target position X (with the term we index representing the type of relation, i.e. `has_object`) and store the position of the opposite position (target/source) Y in the payload. Which token we should choose to store a relation depends on which direction is more commonly queried, as having to decode the payload to find the "other end" of the relation takes slightly longer. We may even choose to index at both positions, meaning we would also need to store the direction of the relation. Though we should first test if any of this actually makes a difference in practice.

### Groups of words

To support relations between groups of words, we could of course add two length values to the payload as well. The first one would indicate the length of group X (the token the relation is indexed at) and the second one the length of group Y (the token indicated in the payload).

### Optimizations

We should use relative positions and variable-length integers to store information in the payload, as this could save significant amounts of disk space and potentially be faster (because more dense storage makes better use of the disk cache).

We should also consider only storing certain values if they differ from the common case (e.g. if we're not dealing with word groups, don't store two 1s for the length).

## How to implement relations search

Obviously, we'd need a query to find a relation, such as:

1. find all `has_object` relations
2. find `has_object` relations where the source lemma equals `bite`
3. find all relations where the source lemma equals `bite`
4. find `has_object` relations where the target lemma equals `man`

Of course, we also want to run more complex queries that combine several relations. There's two common ways to combine relations: two relations involving the same word, and two transitive relations. Examples:

- Find verbs where "man" is object and "dog" is subject. In other words, we're looking for a word that is the source for two specific relations. Visually:     `man <--o-- X --s--> dog `
- Find subjects where its verb is "bite" and that verb has "man" as its object. In other words, we're looking for a word that is the target for a word that is itself the source for another relation. Visually: `man <--o-- bite --s--> X`

In the first case, we can search for both relations, and find matches between them based on their source positions.

In the second case, we find matches for both relations, keeping matches from the second relation where its source exactly matches the target of the first relation, then finally find the targets of those filtered matches.

### Combining two relations into a larger span.

In certain cases we may want to combine two relations into a single larger span, while retaining the source or target we matched them on. After doing this, the source or target need not be the first and last word of the span, so these positions need to be kept track of separately.

A simple way to implement this operation is to return a combined span that has the source and target of the first relation. The source/target from the second relation that was not matched on is lost, but users could still capture those in a group if necessary.

### Longer sources and targets

If we generalize the above to sources/targets that are groups of words, the operations become slightly more complicated, because source and target are both spans in their own right, but the principles remain the same.


## CQL syntax

We need syntax to incorporate relation searches into Corpus Query Language. For the same reasons as explained in #396, we'll use a simple function call style for now. We can always add support for other query languages later if we want.

### Quick reference

Find relation:

    rel(source, reltype, target)

Match and combine relations in various ways:

    rmatch(rel1, rel2, matchtype, action)

Get sources/targets of relation matches:

    rsource( <relation-matches> )
    rtarget( <relation-matches> )

Encode inline tag term with optional attributes:

    rtspan(tagName, attr1, value1, attr2, value2, ...)


### Finding relations

Below are examples of how to use the `rel(source, type, target)` function to find relations between words. Note `_` indicates the default value for a parameter. Parameters at the end may be omitted; their default values will also be used. Parameter defaults are: `rel(source=[], type='.+', target=[])`.

Find `has_object` relations where the source word equals bites:

    rel('bites', 'has_object')

Find all relations where the source word equals bites:

    rel('bites')

Find `has_object` relations where the target word equals man:

    rel(_, 'has_object', 'man')

Find all relations where the target word equals man (the following 2 are equivalent):

    rel(_, '.+', 'man')
    rel(_, _, 'man')

Find all `has_object` relations (the following are equivalent):

    rel([], [_relation='has_object'])
    rel(_, 'has_object')

Find all relations (a very taxing query in a huge corpus, obviously) (the following are all equivalent):

    rel([], [_relation='.+'], [])
    rel([], '.+', [])
    rel(_, _, _)
    rel()


Find sentences:

    <s/>
    rel(_, rtspan('s'))

Find sentences with happy sentiment (these are all equivalent):

    <s sentiment='happy' confidence='10' />
    rel(_, rtspan('s', 'sentiment', 'happy', 'confidence', '10'))
    rel(_, '__tag\u0002s\u0001confidence\u000210\u0001sentiment\u0002happy'))

### Extract source or target

Find words that have 'man' as their object ("find relation source where target is 'man' and type is 'has_object'"):

    rsource(rel(_, 'has_object', 'man'))

Find words that are the object for 'bites' ("find relation target where source is 'bites' and type is 'has_object'"):

    rtarget(rel('bites', 'has_object'))


### Combining relation matches

We can use `rmatch` to match relations in various ways:

    rmatch(rel1, rel2, matchtype)

Example:

    rmatch(
      rel([pos='VERB'], 'nsubj'),
      rel([pos='VERB'], 'obj'),
      source
    )

This will find spans containing a verb with its subject and object. It will return a larger span that includes the spans from both relations, with the source and target of the first relation (i.e. the verb and subject).

The third parameter, `matchtype`, specifies which ends to match:
- `source` or `s` matches the sources of `rel1` and `rel2`
- `target` or `t` matches the targets
- `target_source` or `ts` matches the first's target to the second's source
- `source_target` or `st` matches the first's source to the second's target

Some examples follow.

Find words that have 'man' as their object and 'dog' as their subject (i.e. find X in `'man' <-O- X -S-> 'dog'`):

    rsource(rmatch(
      rel(_, 'has_object', 'man'),
      rel(_, 'has_subject', 'dog'),
      source
    ))

Find words that are the target of both a `has_object` and a `has_subject` relation (doesn't make sense, but ok):

    rtarget(rmatch(
      rel(_, 'has_object'),
      rel(_, 'has_subject'),
      target
    ))

Find words that are the subject of a word that has 'man' as its object (i.e. find X in `'man' <-O- ? -S-> X`):

    rtarget(rmatch(
      rel(_, 'has_subject'),
      rel(_, 'has_object', 'man'),
      source
    ))


### Capturing parts

Just like in other CQL queries, we can tag parts with a group name to capture them.

    rmatch(
      rel(V:[pos='VERB'], 'has_subject', S:[]),
      rel([pos='VERB'], 'has_object', O:[]),
      source
    )

This would return spans including a verb and its subject and object, with the verb tagged as `V`, the subject as `S` and the object as `O`.


## Changes to regular inline tag indexing

### Encoding regular spans as relations

As mentioned earlier, we want to unify the spans we already supported in BlackLab (i.e. XML tags like `<s/>`) with the new relations. This means we need a way to encode regular spans plus their attributes as relations.

#### Single term

We will encode the tag name and all attribute values into a single indexed term, so we only need to store the payload once and cannot accidentally mix up attributes with those of other spans/relations (as is a problem in the classic external index format).

A potential downside of is that this could greatly increase the number of unique terms in the index, if there's multiple attributes and many different combinations of attribute values. This would consume more disk space and could slow down queries. While this doesn't seem to be a huge problem for most typical datasets, perhaps we could offer the option to exclude certain attributes from indexing.

#### Example encoding

For example, to encode a tag `<s sentiment="happy" confidence="10" />` into a single term we can index this term in the `_relation` attribute:

    __tag\u0002s\u0001confidence\u000210\u0001sentiment\u0002happy

So the "relation type" here is `__tag\u0002s`, from which the tag name `s` can be decoded. We keep the tag name as part of the relation type so it's always at the start of the term, allowing us to use a faster prefix query.

After that, the attributes follow, in alphabetical order, each attribute name preceded by `\u0001` and each value preceded by `\u0002`. The alphabetical order is so we can construct an efficient regex to find multiple of them. (if we didn't know the order they were indexed in, we'd have to construct an awkwardly long and likely slow regex to find all matches)

### rtspan function

Because we have XML-style syntax for spans, we likely won't need it, but just in case, a utility function  `rtspan` could be provided, so that:

    rtspan('s', 'sentiment', 'happy', 'confidence', '10')

would return the regex:

    __tag\u0002s.*\u0001confidence\u000210.*\u0001sentiment\u0002happy.*


### Better keep track of spans hierarchy?

> **NOTE:** this is beyong the scope of this task, but it's good to keep it in the back of our mind

Span nesting has never been completely accurately recorded in the index, because only the start and end token is stored, not the nesting level. If two spans have the same start and end, we can't tell which one is the parent of the other.

We could include the nesting hierarchy in the indexed term, e.g. `<b><i>dog</i></b>` would be indexed as `b_i`. In addition to giving us the correct nesting, it would also help to speed up queries like `("dog" within <i/>) within <b/>`, which would only have to search for `"dog" within <b_i />`

We also can't efficiently find an inline tag's ancestors or descendants; `within` and `contains` are relatively expensive, especially in large documents. We could (for certain relations?) store a unique relation id in the payload and have a separate index file where we can look up parent/child relations, so we can quickly walk the tree. This would help if we want to support complex XPath-like search queries.


## Specification of indexing relations

This is a complete specification of how relations will be indexed in BlackLab. This includes the new way of
indexing spans as relations as well.

Relations (and spans) are indexed in the `_relation` annotation. A relation is encoded as a single term with a payload.

We have to choose where to index relations:

1. always at the source, or always at the target
2. always at the first position in the document (whether that's the source or target)

We'll start with the second option, which has the advantage that the associated spans don't need to be sorted to be in order of increasing start position. It is also the way inline tags are indexed right now, so no existing invariants and optimizations are messed up. This does need that we will have to record whether the relation was indexed at the source or target.

### Term

The term indexed is a string of the form:

    relationtype\u0001attr1\u0002value1\u0001attr2\u0002value2\u0001...

The relationtype always ends with `\u0001` (such a closing character is useful to avoid unwanted prefix matches when using regexes). For spans, the relation type is `__tag\u0002tagname\u0001`, where `tagname` is the name of the tag, e.g. `s`.

Attributes are sorted alphabetically by name. Each attribute name is followed by `\u0002`, then the value, and finally `\u0001`.

### Payload

The payload uses Lucene's `VInt` (for non-negative numbers) and `ZInt` (an implementation of [variable-length quantity (VLQ)](https://en.wikipedia.org/wiki/Variable-length_quantity)). We store a relative position for the other end to save space, but we don't need `ZInt` right now because we always index relations at the first position in the document, so the relative position is always non-negative.

The payload for a relation consists of the following fields:

* `relOtherStart: VInt`: relative position of the (start of the) other end. This is always non-negative if we index relations at the first position in the document. Default: `1`.
* `flags: byte`: if `0x01` is set, the relation was indexed at the target, otherwise at the source. If `0x02` is set, the relation only has a target (root relation). The other bits are reserved for future use and must not be set. Default: `0`.
* `thisLength: VInt`: length of this end of the relation. For a word group, this would be greater than one. Default: `1`
* `otherLength: VInt`: length of the other end of the relation. For a word group, this would be greater than one. Default: `1`

Fields may be ommitted from the end if they have the default value. Therefore, an empty payload means `{ relOtherStart: 1, flags: 0, thisLength: 1, otherLength: 1 }`.

In the future, we likely want to include unique relation ids (for some relations), for example to look up hierarchy information about inline tags. The unused bits in the `flags` byte could be used as a way to maintain backward binary compatibility with such future additions.

### Calculate Lucene span from relation term

When found using BlackLab, a relation has a source and target (which may be word groups or single words) as well as a "regular" span.

The span normally runs between source and target, but operations combining relations may enlarge the span so this no longer holds.

To calculate the Lucene span from a matched relation term's payload, first a few helper values:

    thisStart = position the relation was indexed at
    thisEnd = thisStart + thisLength
    otherStart = thisStart + relOtherStart
    otherEnd = otherStart + otherLength

So the Lucene span for a relation is:

    [thisStart, otherEnd)

or maybe even

    [thisStart, max(thisEnd, otherEnd) )

although it seems unlikely that we want the source and target to overlap.

(note that Lucene spans are half-open, i.e. the end position is not included in the span)
