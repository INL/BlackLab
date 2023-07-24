# API versions

We're evolving the BlackLab web service API over time, using versioning.

There's currently three major versions:
- `3.0`: legacy API version, used by BlackLab up to 3.x
- `4.0`: newer, mostly compatible API version, used by BlackLab 4.x. Adds some keys and endpoints and makes slight changes, mostly to improve consistency and reduce redundancy. The additions are still experimental, just like v5 of the API.
- `5.0-exp`: STILL EXPERIMENTAL. stricter, cleaner version of the `4.0` API, removing parameters and response keys from `3.0`. Because this is still experimental, we may make breaking changes. Useful for testing.

On any BLS request, include the `api` parameter to specify which API version to use. So if you want to keep using the older API for now, add `api=3`. If you don't specify an API version, the default version will be used. 

To set the default API version, configure `parameters.api` in your `blacklab-server.yaml` (or `blacklab-webservice.yaml` for Solr).

Valid values for API version include:
- Exact version, e.g. `4.0`
- Major version, e.g. `4` (most recent "regular" release in the 4.x series, i.e. without a `-..` suffix)
- `cur` or `current`, the current stable version (currently `4.0`)
- `exp` or `experimental`, always points to the highest version available (currently `5.0-exp`)

New versions may be added in the future, and support for older versions will eventually be dropped.

API version numbers roughly match the BlackLab version where they became (or will become) the default API version.

Note that the additions to API v4 are currently considered experimental, until the release of BlackLab 4.0. So where it states "you should use this for future compatibility", know that right now, there may still be breaking changes in the newly added stuff before the 4.0 release.


## API changes from 3.0 to 4.0

These are the differences between API version 3.0 and 4.0:

- Server info page:
  - New key added (`/`): `apiVersion` (valid values: `3.0` and `4.0`; assume `3.0` if missing)
  - In addition to `indices`, the new `corpora` key was added that provides the same information in a slightly different format. You should use `corpora` instead of `indices` for future compatibility.
  - In addition to being reported under `fieldInfo`, `pidField` is now also a top-level key. You should use this version of the key for future compatibility. (the other special fields in `fieldInfo` will be moved to `custom` in v5)
- Corpus info page (`/CORPUSNAME`):
  - Two keys were renamed to be more consistent: in the `versionInfo` block,
  `blacklabVersion` and `blacklabBuildTime` are now spelled with a lowercase `l`, just like on the server info page. This is unlikely to break any clients.
- Annotated fields: 
  - `displayOrder` will no longer include internal annotations (e.g. `punct` and `_relation`, previously called `starttag`), as these are generally not meant to be displayed as search fields.
- Results:
  - In addition to `captureGroups`, `matchInfos` will be reported that includes the same information as well as any inline tags and relations matched. You should use this instead of `captureGroups` for future compatibility.
  - `before`/`after` are the new, preferred alternatives to `left`/`right` for sorting/grouping on context. Not all languages are LTR, so this makes more sense. Response structures in API v4 still use `left`/`right` for compatibility, but will eventually be updated as well. These properties can now get a number of tokens as an extra parameter, e.g. `before:lemma:i:2`.
  - For grouping on context, `wordleft`/`wordright` have been deprecated. Use `before`/`after` with 1 token instead.
- New endpoints were added for all operations on corpora, at `/corpora/CORPUSNAME/...` (for now alongside existing endpoints `/CORPUSNAME`). These endpoints are available in API v4 but only "speak" API v5 (see below). You should move to these endpoints for future compatibility.

## API changes from 4.0 to 5.0-exp

- Old endpoints related to corpora have been removed. Use the new `/corpora/...` endpoints introduced in API v4 instead.
- All XML responses:
  - XML responses don't use dynamic values as element names anymore, but instead adopt a `<entry><key>...</key><value>...</value></entry>` structure. This avoids problems with and simplifies maintaining the (less-used) XML format. Affects: `summary/searchParams`, `docInfos`, `users[]` in `/sharing` response.
  - Wherever the XML used attributes for map entries, e.g. `<entry key="key">value</entry>`, this was changed to `<entry><key>...</key><value>...</value></entry>` as well.
- server, corpus, field info:
  - `indexName` in responses has been replaced with `corpusName`.
  - Corpora, metadata and annotated field and annotations report certain properties (such as `displayName`, `description`) inside a `custom` block now etc. These are all ignored by BlackLab but may be useful for client applications such as BlackLab Frontend. They are only included in responses if you specify `custom=true`.
- Server info page:
  - Dropped `indices`. Use `corpora` instead.
- Document info page (`/docs/DOC_PID`):
  - No longer includes `metadataFieldDisplayNames`, `metadataFieldGroups` or `docFields`. This information can be found on the corpus info page and need not be sent with each document info request.
- Results pages:
  - No longer include `metadataFieldDisplayNames`, `metadataFieldGroups` or `docFields`. This information can be found on the corpus info page and need not be sent with each search request.
  - `summary` has been restructured to group related values together. Keys have been renamed for clarity.
  - Dropped `captureGroups`. Use `matchInfos` instead.
  - response keys `left`/`right` have been replaced with `before`/`after` in the `/hits` response.
  - `docInfos` now have a `metadata` subobject instead of mixing metadata with `mayView` and `lengthInTokens`.
