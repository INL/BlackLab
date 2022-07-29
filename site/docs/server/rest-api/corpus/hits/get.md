# Find hits

Find occurrences of a text pattern in the corpus, optionally filtered on document metadata fields.

This resource can also [group hits](../hits-grouped/get.md) (returning a list of groups), or [show the contents](../hits-viewgroup/get.md) of one of the resulting groups.

**URL** : `/blacklab-server/<corpus-name>/hits`

**Method** : `GET`

#### Basic parameters

- `patt`: (Corpus Query Language) pattern to search for
- `filter`: (optional) Lucene Query Language document filter query
- `wordsaroundhit`: (optional) number of words to show around each hit. Defaults to 5.


#### Advanced parameters

Some less commonly used parameters for advanced use cases. These are all optional.

- `pattlang`: pattern language to use for `patt`. Defaults to `corpusql` (Corpus Query Language). The other values (`contextql` and `luceneql`) have very limited support at the moment.  Other, more useful query languages may be added in the future.
- `pattfield`: the annotated field to search using `patt`, if your corpus contains multiple annotated fields. Most corpora only contain one. Defaults to the first (or only) annotated field defined.
- `filterlang`: filter language to use for `filter`. Defaults to `luceneql`. `contextql` is also supported, but very limited. More options may be added in the future.
- `docpid`: filter on a single document pid.
- 

## Success Response

**Code** : `200 OK`

### Content examples

```json

```

## TODO

- This endpoint does a lot of different things, producing different responses. This can be confusing. Maybe we should separate the different uses into multiple endpoints.
