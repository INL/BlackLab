# Relations between words and word groups

(see GitHub issue [#405](https://github.com/INL/BlackLab/issues/405))

## Goal

We want to index (and search for) relations between two words, such as "verb X has object Y". In this case, we have two words, X and Y, and the `has_object` relation pointing from X to Y:

    X  ---has_object-->  Y

Example:

             Y <-o- X 
    Small   man   bites   large   dog.

> **NOTE:** linguists call X the _head_ of the relation and Y the _dependent_; we will stick with the more general _source_ for X and _target_ for Y in BlackLab. The latter terms reflect that this primitive has many potential usages and are easier to understand for users of different backgrounds

The most common case is that a word can be the source for multiple relations and the target for only one, but ideally our implementation doesn't care about this and can handle any set of relations.

Obviously it would also be very useful to be able to index relations between groups of words. In the example, the object of the sentence is not just "man" but actually "Small man". We will look for an implementation that will allow us to store relations between groups of words as well, although full support for this might not be in the initial version.

## How to index relations

One way to index these kinds of relations (suggested by @JessedeDoes) is similar to how we index spans such as `<s/>`. Spans are indexed at the starting token, with the end position stored in the payload.

We could index these in the same annotation as spans are already indexed in. We'd have to be careful to make sure both can easily be distinguished from one another.

Better yet, we should generalize notion of a span we have right now to be a relation as well:

      X ---starts_sentence_ending_at--> Y
    Small     man    bites    large    dog.

We could index relations at the source position X (with the term we index representing the type of relation, i.e. `has_object`) and store the position of the target Y in the payload.

For performance reasons (see below), it might be useful to also do the opposite, store the same relation at the target position Y with the source position X in the payload. If we do index both of these, we also need to indicate the direction of the relation in the payload, so we know which is source and which is target (so we'd store a boolean meaning "I am the source token" or "I am the target token").

To support relations between groups of words, we could of course add two length values to the payload as well. The first one would indicate the length of group X (the token the relation is indexed at) and the second one the length of group Y (the token indicated in the payload).

> **NOTE:** consider using VInts and relative positions to store information in the payload, as this could save significant amounts of disk space and potentially be faster (because of more efficient disk caching). Also consider only storing a value if it's different from the common case and leaving it out otherwise (e.g. if we're not dealing with word groups, don't store two 1s for the length).

> We should take the opportunity to:
> - unify spans (`<s/>`, `<named-entity/>`, etc.) with relations as described above, including changes to how we store the payload and how we index any attributes (combining them all into one term string? probably provide a way to exclude unique id attributes then?).
> - rename the `starttag` annotation to `_relations` (`_` to avoid collisions).
> This should probably be a clean break for the integrated index while it's still  experimental, so we don't have to support different variations. The older, external index format would be unaffected.
> - consider including a document-unique relation id in the payload, so we can use that later to look up details about a relation. This could be useful e.g. if we want to keep track of hierarchical relationships between spans separately, so we can more easily find ancestors or descendants of a span.

### Document format and test data

The [CoNLL-U format](https://universaldependencies.org/format.html) is commonly used to encode texts with these kinds of dependency relations. We should support this format. We can use the [LassySmall](https://github.com/UniversalDependencies/UD_Dutch-LassySmall) corpus for testing.

Eventually we'll look at adding support for TEI with dependency relations as well.

## How to implement relations search

Obviously, we'd need a query to find a relation:

1. find all `has_object` relations
2. find `has_object` relations where the source lemma equals `bite`
3. find all relations where the source lemma equals `bite`
4. find `has_object` relations where the target lemma equals `man`

1-3 are straightforward with the proposed way to index. Number 4 would probably benefit from indexing relations at both positions, because we could find matches without having to decode the payload.

Of course, we also want to run more complex queries that combine several relations. There's two common ways to combine relations: pointing from/to the same word or transitive. Examples:

- Find verbs where "man" is object and "dog" is subject. In other words, we're looking for a word that is the source for two specific relations. Visually:     `man <--o-- X --s--> dog `
- Find subjects where its verb is "bite" and that verb has "man" as its object. In other words, we're looking for a word that is the target for a word that is itself the source for another relation. Visually: `man <--o-- bite --s--> X`

In the first case, we can find matches for both relations, keep just the source positions of those matches, then combine those using AND.

In the second case, we find matches for both relations, keeping matches from the second relation where its source exactly matches the target of the first relation, then finally find the targets of those filtered matches.

In certain cases we may want to combine two relations into a single span that only has a source or target (we don't intend to keep track of multiple targets for a single source, or vice versa). After doing this, the source or target need not be the first and last word of the span, so these positions need to be kept track of separately.

### Longer sources and targets

If we generalize the above to sources/targets that are groups of words, the operations become slightly more complicated, because source and target are both spans in their own right, but the principle remains the same.


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

Encode relation type (with optional attributes):

    rtype(relname, attr1, value1, attr2, value2, ...)


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
    rel(_, rtype('span', 'tag', 's'))

Find sentences with happy sentiment:

    <s sentiment='happy' confidence='10' />
    rel(_, rtype('span', 'tag', 's', 'sentiment', 'happy', 'confidence', '10'))

### Extract source or target

Find words that have 'man' as their object ("find relation source where target is 'man' and type is 'has_object'"):

    rsource(rel(_, 'has_object', 'man'))

Find words that are the object for 'bites' ("find relation target where source is 'bites' and type is 'has_object'"):

    rtarget(rel('bites', 'has_object'))


### Combining relation matches

We can use `rmatch` to match and combine relations in various ways:

    rmatch(rel1, rel2, matchtype, action)

Example:

    rmatch(
      rel([pos='VERB'], 'nsubj'),
      rel([pos='VERB'], 'obj'),
      source,
      combine
    )

The above will find spans containing a verb with its subject and object.

The third parameter, `matchtype`, specifies which ends to match:
- `source` checks if `rel1` and `rel2` have the same source
- `target` checks for same target
- `target_source` checks if the first's target equals the second's source
- `source_target` checks if the first's source equals the second's target

The fourth parameter, `action`, indicates what to do when a match is found:
- `combine` create a span that includes both relations fully
- `first` keep only the first relation
- `second` keep only the second relation

> **NOTE:** `combine` will retain the matched source or target from the first relation. For example, if we used `source_target` as our `matchtype`, `combine` would result in a span that has only a source (from the first relation) and no target.
> 
> Trying to use the target on such a "half-relation" would result in an error.

Capture groups could be used to identify specific parts of the match, just like with regular CQL queries. See below.

Some examples follow.

Find words that have 'man' as their object and 'dog' as their subject (i.e. find X in `'man' <-O- X -S-> 'dog'`):

    rsource(rmatch(
      rel(_, 'has_object', 'man'),
      rel(_, 'has_subject', 'dog'),
      source,
      first
    ))

Find words that are the target of both a `has_object` and a `has_subject` relation (doesn't make sense, but ok):

    rtarget(rmatch(
      rel(_, 'has_object'),
      rel(_, 'has_subject'),
      target,
      first
    ))

Find words that are the subject of a word that has 'man' as its object (i.e. find X in `'man' <-O- ? -S-> X`):

    rtarget(rmatch(
      rel(_, 'has_subject'),
      rel(_, 'has_object', 'man'),
      source,
      first
    ))


### Capturing parts

Just like in other CQL queries, we can tag parts with a group name to capture them.

    rmatch(
      rel(V:[pos='VERB'], 'has_subject', S:[]),
      rel([pos='VERB'], 'has_object', O:[]),
      source,
      combine
    )

This would return spans including a verb and its subject and object, with the verb tagged as `V`, the subject as `S` and the object as `O`.


### Encoding regular spans as relations

As mentioned earlier, we want to unify the spans we already supported in BlackLab (i.e. XML tags like `<s/>`) with the new relations. This means we need a way to encode regular spans plus their attributes as relations.

One way to encode `<s sentiment="happy"/>` to a term we can index in the `_relation` attribute is:

    span{tag:s}{sentiment:happy}

A utility function could be used in queries where needed:

    rtype('span', 'tag', 's', 'sentiment', 'happy')

is roughly equivalent to (specific way of encoding attributes may change):

    [_relation="span{tag:s}.*{sentiment:happy}.*"]

> **NOTE:** that we now encode all attribute values into the indexed term, so we only need to store the payload once and cannot mix up attributes with those of other spans/relations.
> 
> A downside is that this can greatly increase the number of unique terms in the index, which consumes more disk space and can slow down queries. We should offer the option to exclude certain attributes from indexing.
> 
> Another downside is that the regex becomes more complicated if you specify multiple attributes. We could say that tag is always the first attribute, but if you specify more than one other attribute, you will need regexes like `.*A.*B.*|.*B.*A.*`, which rapidly gets out of hand as you add more attributes. Better is probably to use e.g. `[_relation="span{tag:s}.*{sentiment:happy}.*" & _relation="span{tag:s}.*{confidence:10}.*"]`. We would always want a fixed prefix as that speeds up queries considerably.
