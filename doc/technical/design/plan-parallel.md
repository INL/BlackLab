# Parallel corpora

A parallel corpus has multiple versions of the same documents, often multiple languages or maybe historical versions. We want to be able to match across versions, e.g. find the German translation of a Dutch word.

Documents can have several _alignments_, i.e. per sentence, per word, etc.

## How to index?

### Annotated fields

Three options:

1. everything in one annotated field, e.g. NL annotations and DE annotations, with aligment relations between the two versions. Easy to do, but that would mean a single annotated field would have two very different tokenizations (i.e. the third word in NL need not correspond to the third word in JP at all). Throughout BlackLab, it is generally assumed that an annotated field has a single tokenization, so annotations are all automatically aligned.
2. one annotated field per version, so e.g. one `contents^NL` field and one `contents^DE` field. Alignment relations between the two versions. Seems better to keep the two tokenizations in separate fields. 
3. one document per language. This seems more difficult to implement because we would have to combine hits from different documents to resolve a single query.

Option 2 seems the most viable, so that's what we're going with for the moment.

### Alignment relations: index once or twice?

Is it enough to have the relations only go from e.g. `contents^NL` to `contents^DE`, or do we need to index them both ways? The latter seems redundant.

On the other hand, the user may want to search both ways, and regular relations search only works in the direction it was indexed. Maybe we could introduce two-way relations, so that queries that go in the other direction can be resolved using the single indexed relation. So e.g. if the relations are indexed in the `NL` field, and the query asks for the Dutch translation of a German word, BlackLab should know to use the two-way relations in the `NL` field "in reverse" to answer this question.

We should try to make this a general mechanism, not a hack just for parallel corpora.

E.g. a we're searching for a relation _R_ between a source field _A_ and target field _B_, but _R_ is indexed in field _B_. BlackLab should know to use the two-way relation in field _B_ to answer this question. In other words: for relations between two fields, BlackLab will check if the relation type exists in the source field first, and if it doesn't, check if it exists in the target field as a two-way relation.

## BCQL

### Querying alignments

We're trying to find phrases with two aligned words in two languages, Dutch (`NL`) and German (`DE`). The phrases should have the structure `als ... en ...` in Dutch and `wie ... und ...` in DE, with the requirement that words in the gaps are aligned. So we're looking for phrases like `als kat en hond` in Dutch and `wie Katze und Hund` in German.

Our approach is to find the phrase in Dutch, capturing the German equivalent of the gap words while doing so, then also finding the German equivalent of the whole phrase:

    # pmatch: find (as best as possible) aligned spans in other language
    # (optional query filter for other language, optional capture name)
    palign(
      'als' (A:[] -tr::DE-> C:[]) 'en' (B:[] -tr::DE-> D:[]),
      'DE',
      'wie' [] 'und' [],
      'E'
    )

This query should be executed specifying the primary version field to search  (e.g. `contents^NL`) as a parameter.

The `palign(sourceQuery, targetLanguage, [targetQuery], [captureName])` function tries to finds the best aligned version for a hit from `sourceQuery` in the `targetLanguage`, optionally filtered by `targetQuery`, and optionally capturing the target hit as `captureName`. The function just returns the hit from the `sourceQuery` unchanged.

The `-tr::DE->` relation operation is used where we require exact alignment through an explicit alignment relation, in this case one between single words (but could also be between two sentences, paragraphs, etc.).

The results for the above query is a "regular" set of hits from the `contents^NL` field, but it contains captures from the `contents^DE` field as well (`C`, `D` and `E`). For parallel queries, captures in the response indicate the field from which it was taken.

We should carefully consider how `palign` should work in the case where there is no exact alignment between the two versions for the hit found, and with the optional target filter query.

For example:
- if we find a two-word hit in Dutch, and they have aligned equivalents in German, but those are not contiguous, do we just return a span that contains both aligned words, even if that includes words not aligned with the Dutch version? (of course, the user can capture the aligned words and we can highlight those in the frontend)
- if there's a target filter query, and the aligned version contains a match for it but doesn't match it exactly (e.g. it has a few extra words), do we keep the hit or reject it? (of course the user can always surround the filter query with `[]*` to allow for this kind of "slop", but that doesn't improve readability)

## Example data

WIP example files Jesse: `/mnt/Projecten/Corpora/Historische_Corpora/EDGeS_historical_bible_corpus/XMLConversie/`
