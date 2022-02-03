This package mostly contains the `SpanQuery` classes, with their associated
`SpanWeight` and `Spans` classes.

These are added to Lucene's built-in `SpanQuery` classes to be able to resolve
certain Corpus Query Language constructs efficiently.

Other notable classes in this package:

- `HitQueryContext` contains capture group information
- `DocFieldLengthGetter` allows us to get the document's length in tokens, needed for some classes
- `DocIntFieldGetter` can retrieve an integer metadata field from the Lucene document, used by some classes
