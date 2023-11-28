# Parallel corpora

A parallel corpus has multiple versions of the same documents, often multiple languages or maybe historical versions. We want to be able to match across versions, e.g. find the German translation of a Dutch word.

Documents can have several _alignments_, i.e. per sentence, per word, etc.

## How to index?

### Annotated fields

There seem to be three obvious options, of which option 2 seems like the most viable at the moment:

1. everything in one annotated field, e.g. Dutch annotations and German annotations, with aligment relations between the two versions. Easy to do, but that would mean a single annotated field would have two very different tokenizations (i.e. the 3rd word in Dutch need not correspond to the 3rd word in German at all). Throughout BlackLab, it is generally assumed that an annotated field has a single tokenization, so annotations are all automatically aligned.
2. one annotated field per version, so e.g. one field for Dutch and one for German. Alignment relations between the two versions. Seems better to keep the two tokenizations in separate fields. 
3. one Lucene document per version of an input document. This seems more difficult to implement because we would have to combine hits from different documents to resolve a single query.

If option 2 doesn't work out, we may try one of the others.

### Field naming convention

In order for BlackLab to smoothly work with alignment between different fields representing versions of the same document, we should adopt a naming convention for these fields. This convention should respect the existing restrictions on field names in BlackLab.

Each field should have the same prefix name (e.g. `contents`), then a special separator (we'll use two dots `..`), then the version code (e.g. language). So the Dutch version of the contents field would be `contents..nl`, and the German version `contents..de`.

Users should not use the double dot notation in their non-parallel corpora to avoid confusion.

### Alignment relations: index once or twice?

Is it enough to have the relations only go from e.g. the `nl` field to the `de` field, or do we need to index them both ways? The latter seems redundant.

On the other hand, the user may want to search both ways, and regular relations search only works in the direction it was indexed. Maybe we could introduce two-way relations, so that queries that go in the other direction can be resolved using the single indexed relation. So e.g. if the relations are indexed in the `nl` field, and the query asks for the Dutch translation of a German word, BlackLab should know to use the two-way relations in the `nl` field "in reverse" to answer this question.

We should try to make this a general mechanism, not a hack just for parallel corpora.

E.g. a we're searching for a relation _R_ between a source field _A_ and target field _B_, but _R_ is indexed in field _B_. BlackLab should know to use the two-way relation in field _B_ to answer this question. In other words: for relations between two fields, BlackLab will check if the relation type exists in the source field first, and if it doesn't, check if it exists in the target field as a two-way relation.

## BCQL

### Querying alignments

We're trying to find phrases with two aligned words in two languages, Dutch (`nl`) and German (`de`). The phrases should have the structure `als ... en ...` in Dutch and `wie ... und ...` in German, with the requirement that words in the gaps are aligned. So we're looking for phrases like `als kat en hond` in Dutch and `wie Katze und Hund` in German.

Our approach is to find the phrase in Dutch, capturing the German equivalent of the gap words while doing so, then also finding the German equivalent of the whole phrase:

    # pver: start searching in the specified version
    # (palign allows "crossing over" to other versions)
    pver('nl', 
      # palign: find (as best as possible) aligned spans in other version
      # (optional query filter for other version, optional capture name)
      palign(
        # source query, executed on Dutch field
        'als' (A:[] -@de-> C:[]) 'en' (B:[] -@de-> D:[]),

        'de',              # target version, German in this case
        'wie' [] 'und' [], # (optional) target query, executed on German field
        'E'                # (optional) target capture name
      )
    )

If the `pattfield` specified is either `contents` or e.g. `contents..de` (which may simply be the default field for the corpus), the `pver('nl', ...)` call ensures that the primary field searched will be `contents..nl`.

The `palign(sourceQuery, targetVersion, [targetQuery], [captureName])` function tries to finds the best aligned version for a hit from `sourceQuery` in the `targetVersion`, optionally filtered by `targetQuery` (which is executed on the `targetVersion` version), and optionally capturing the target hit as `captureName`. The function just returns the hit from the `sourceQuery`, with any alignment information in captures.

The `-@de->` relation operation (meaning "aligns with German version") is used where we require exact alignment through an explicit alignment relation, in this case one between single words (but could also be between two sentences, paragraphs, etc.).

The results for the above query is a "regular" set of hits from the `contents..nl` field, but it contains captures from the `contents..de` field as well (`C`, `D` and `E`). For parallel queries, captures in the response indicate the field from which it was taken.

We should carefully consider how `palign` should work in the case where there is no exact alignment between the two versions for the hit found, and with the optional target filter query.

For example:
- if we find a two-word hit in Dutch, and they have aligned equivalents in German, but those are not contiguous, do we just return a span that contains both aligned words, even if that includes words not aligned with the Dutch version? (of course, the user can capture the aligned words and we can highlight those in the frontend)
- if there's a target filter query, and the aligned version contains a match for it but doesn't match it exactly (e.g. it has a few extra words), do we keep the hit or reject it? (of course the user can always surround the filter query with `[]*` to allow for this kind of "slop", but that doesn't improve readability)

## Indexing

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

We'll see if we can make XInclude work first, and only resort to the custom approach if we hit a roadblock.

See [Saxon and XInclude](https://www.saxonica.com/html/documentation10/sourcedocs/XInclude.html).

### Content stores

We'll keep the BlackLab property that each annotated field (optionally) has its own content store. If we stay with linked files, that just means each annotated field gets one of the contents files in its store.

We don't plan to store the alignment XML. Hopefully we don't need it and can resolve any alignment questions using the indexed alignment relations.

### Metadata

Does every version have its own metadata? Does the combined "superdocument" have its own metadata? Probably yes for both.

We can use prefixes to distinguish between metadata for each version.

Open question: does BlackLab need to "understand" what metadata fields belong with which version? Or is that something left to the client? For now we'll assume the latter.
