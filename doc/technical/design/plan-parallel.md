# Parallel corpora

A parallel corpus has multiple versions of the same documents, often multiple languages or maybe historical versions. We want to be able to match across versions, e.g. find the German translation of a Dutch word.

Documents can have several _alignments_, i.e. per sentence, per word, etc.

## Summary of (provisional) technical decisions

We've decided on the following initial approach:

- We'll use one annotated field per document version.
- Version-specific field names must all end with, so e.g. annotated fields `contents__nl` and `contents__de`, or metadata fields `title__nl` and `title__de`. (Metadata) fields without a suffix apply to all versions of the document, e.g. `subject`.
- We'll index alignment relations both ways for now (e.g. `nl->de` and `de->nl`), so we don't need special logic to allow searching in both directions.
- We'll provide (rudimentary for now) support for XInclude to link XML documents together (e.g. link a document with alignments to the two contents documents those alignments refer to), but internally it will be treated as a single XML document.
- The XML will be stored in the content store of the main annotated field (which is the first one in the configuration file). The other annotated fields don't get their own content store but will automatically use the content store of the main annotated field.

The general approach is to only add the minimum of features needed to support parallel corpora, so we don't have to make too many changes to the core of BlackLab.

If any of these don't work out, we'll try one of the other approaches mentioned. See the Indexing section at the end of the document for more details.

## BCQL

### Querying alignments

We're trying to find phrases with two aligned words in two languages, Dutch (`nl`) and German (`de`). The phrases should have the structure `als ... en ...` in Dutch and `wie ... und ...` in German, with the requirement that words in the gaps are aligned. So we're looking for phrases like `als kat en hond` in Dutch and `wie Katze und Hund` in German.

Our approach is to find the phrase in Dutch and capturing alignment relations to German. It should also be possible to find the phrase in both languages and capture alignment relations between the hits.

Proposed syntax for the above:

    # Find German equivalent of Dutch phrase
    # (based on alignment relations with source within span from left side)
    @nl 'als [] 'en' []' ==>@de _

    # Find aligned Dutch and German phrases, capturing alignment relations
    # (at least one matching alignment relation must exist, or the hit is skipped)
    @nl 'als [] 'en' []' ==>@de 'wie' [] 'und' []'

The next section explains how these queries work.

### New operator: find all relations between two spans

The `==>` operator is a new type of relation operator that finds all relations where the source of the relation is part of the left side hit. It also finds a right side span that encompasses all the matching relations' targets. It also required that the right side span contains a hit for the given right side query (here `'wie' [] 'und' []`), if any such query was given.

The `@de` at the end of the relation operator shows that the relations we're looking for must be cross-field relations pointing from the current field (`contents__nl`, as indicated by the `@nl` at the start) to the `contents__de` field. It also means that we automatically look for relation class `pal` (parallel alignments). Of course, the `==>` operator still supports the same relation class/type filters if necessary.

> Be careful not to put a space before `@de`; this wouldn't discard alignment relations to different versions and will therefore yield meaningless results. (**NOTE:** we should probably recognize this situation and do the right thing automatically)

This operator returns the left span and two captures: the list of relations as `rels` and the right part as `target`. The captures will indicate relations pointing to the `contents__de` field, or the capture itself being from that field.

If the default capture names don't work for you, you rename them:

    @nl 'als [] 'en' []' A:==>@de B:('wie [] 'und' []')

### Optional new operator: find all relations with this source?

We could consider also adding a `-TYPE=>` operator which will find a list of relations where the source of the relation exactly matches the left side, and the target(s) match the right side (which can also be `_`, i.e. "don't care").

This could be useful in the case where a single word in one language matches multiple (discontinuous) words in another. The previously described `==>` operator would also find these though, so this operator might not be needed.

### Alignments between more than two versions

```
@nl 'als [] 'en' []'
    ==>@de 'wie' [] 'und' []' ;
    ==>@en 'as' [] 'and' []'
```

## Indexing

### Annotated fields

There seem to be three obvious options, of which option 2 seems like the most viable at the moment:

1. everything in one annotated field, e.g. Dutch annotations and German annotations, with aligment relations between the two versions. Easy to do, but that would mean a single annotated field would have two very different tokenizations (i.e. the 3rd word in Dutch need not correspond to the 3rd word in German at all). Throughout BlackLab, it is generally assumed that an annotated field has a single tokenization, so annotations are all automatically aligned.
2. one annotated field per version, so e.g. one field for Dutch and one for German. Alignment relations between the two versions. Seems better to keep the two tokenizations in separate fields.
3. one Lucene document per version of an input document. This seems more difficult to implement because we would have to combine hits from different documents to resolve a single query.

If option 2 doesn't work out, we may try one of the others.

### Field naming convention

In order for BlackLab to smoothly work with alignment between different fields representing versions of the same document, we should adopt a naming convention for these fields. This convention should respect the existing restrictions on field names in BlackLab.

Each field should have the same prefix name (e.g. `contents`), then a special separator (we'll use two underscores `__`), then the version code (e.g. language). So the Dutch version of the contents field would be `contents__nl`, and the German version `contents__de`.

Metadata fields specific to a version should use the same convention, so `title__nl` is the title of the Dutch version, etc. There may also be metadata fields that are not specific to a version, e.g. `subject` (subject of these documents). These don't get a suffix.

Users should not use double underscore in other field names to avoid issues.

### Alignment relations: index once or twice?

Is it enough to have the relations only go from e.g. the `nl` field to the `de` field, or do we need to index them both ways? The latter is the easier approach, but might waste some disk space and memory.

The user may want to search both ways, and regular relations search only works in the direction it was indexed. Maybe we could introduce two-way relations, so that queries that go in the other direction can be resolved using the single indexed relation. So e.g. if the relations are indexed in the `nl` field, and the query asks for the Dutch translation of a German word, BlackLab should know to use the two-way relations in the `nl` field "in reverse" to answer this question.

If we do this, it would need to be a general mechanism, not a hack just for parallel corpora.

E.g. a we're searching for a relation _R_ between a source field _A_ and target field _B_, but _R_ is indexed in field _B_. BlackLab should know to use the two-way relation in field _B_ to answer this question. In other words: for relations between two fields, BlackLab will check if the relation type exists in the source field first, and if it doesn't, check if it exists in the target field as a two-way relation.

For now, let's just index the relations both ways, to avoid unnecessary complexity. If necessary, we can add the two-way relation mechanism later.

### Linking or embedding?

The example data (see below) consist of alignment files that link to the different versions of the content files.

Do we stay with this approach of several linked files, or do we embed the different versions in a single file?

- A single files with everything seems easier to index, but may become very large and seems awkward to store and display (in pages) / highlight. At the very least we'd need to keep track of the character offsets where different versions start and end so we can 'cut' the separate versions.
- A separate file per version is a bit trickier to index, but easier when storing and displaying.

For now, we'll stick with separate files.

### Linking mechanism

If we stick with linked files, do we use XInclude or our own linking mechanism?

- XInclude is a standard mechanism, which is generally a good thing, but it might be trickier to track when we enter a new file, keep track of character offsets in each file separately, etc. It does make referencing by `xml:id` easier, though, because logically everything is the same document.

- A custom approach allows us more freedom to track which file we're in and which character position we're at. But it's nonstandard, and we'd have to resolve `xml:id` references across different files, which becomes a bit messier.

We've built rudimentary XInclude support for now. We may improve upon this implementation later, or if problems arise, we may resort to the custom approach.

> See [Saxon and XInclude](https://www.saxonica.com/html/documentation10/sourcedocs/XInclude.html). We don't use this though, because we need to be able to track the character positions in the combined documents, not the individual files. Our initial implementation just recognizes XInclude tags using a regular expression and replaced them with the contents of the linked file. This is primitive and requires the tag to be written a certain way, but works well enough for now.

### Content stores

The complete document (after integrating the linked documents) will be stored in the content store for the main annotated field. The other annotated fields will not get their own content store, but will automatically use the content store of the main annotated field.

Advantage is that we have access to all the content and alignment relations in XML; disadvantage is that we still need some way to keep track of which part of the XML belongs to which version (so we can display a specific version). It may also cause issues if documents get very large.

### Metadata

Does every version have its own metadata? Does the combined "superdocument" have its own metadata? Probably yes for both.

We can use the same suffixes as for annotated fields to distinguish between metadata for each version.

Open question: does BlackLab need to "understand" what metadata fields belong with which version? Or is that something left to the client? For now we'll assume the latter.
