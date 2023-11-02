# Parallel corpora

A parallel corpus has multiple versions of the same documents, often multiple languages or maybe historical versions. We want to be able to match across versions, e.g. find the Japanese translation of a Dutch word.

## How to index?

### Annotated fields

Three options:

- everything in one annotated field, e.g. NL annotations and JP annotations, with aligment relations between the two versions. Easy to do but odd to have different oken alignments in 1 annotated field.

- one annotated field per version, so e.g. one `contents_nl` field and one `contents_jp` field. Alignment relations between the two versions. Seems better to keep the two tokenizations in separate fields.

- 1 document per taal. Lijkt lastiger te implementeren omdat je hits uit verschillende documenten met elkaar moet gaan combineren.


### Alignment relations: index once or twice?

Is it enough to have the relations only go from version A to version B, or do we need to index them both ways? That seems redundant.

On the other hand, the user may want to search both ways, and regular relations search only works in the direction it was indexed. Maybe we could introduce "virtual" relations that go the other way that are internally rewritten to use the single indexed relation.

## BCQL

### Version selector?

We need to way to specify that part of the query is for the NL version and part is for the JP version.

Maybe something like:

    par('nl', 'de', ("hond" -tr-> A:[pos="NOUN"]) ([] -tr-> "und") ("kat" -tr-> B:[pos="NOUN"]))

Now we know that the left side of the `-tr->` is for the NL version and the right side is for the DE version (although how does BlackLab know to apply this for the `-tr->` relation but not for others?).

We probably stil need a way to explicitly state that a part of the query or a single token refers to the NL version, e.g. something like `nl."hond"` or `de.("liebe" [lemma="Hund"])`.


WIP example files Jesse: `/mnt/Projecten/Corpora/Historische_Corpora/EDGeS_historical_bible_corpus/XMLConversie/`
