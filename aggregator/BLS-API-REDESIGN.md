# BlackLab Server API redesign

The BLS API has quite a few quirks that can make it confusing and annoying to work with.
If we break compatibility anyway (e.g. because we're integrating with Solr), how might
we redesign the API?

## Problems

XML-related:
- Avoid XML attributes.
- Avoid dynamic XML element names.
  (e.g. when outputting a key-value map, don't use the keys for element names,
   use `<entry><key>indexName</key><value>my-fun-index</value></entry>`)

General:
- Replace left/right with before/after (makes more sense for RTL languages)
- Don't include static info on dynamic (results) pages.
  (e.g. don't send display names for all metadata fields with each hits results)
- Be consistent.
  (if information is given in multiple places, e.g. on the server info page as well
   as on the index info page, use the same structure and element names)
- Pick explanatory names.
  (e.g. "stoppedRetrievingHits" leads to the question "why". "maxHitsExceeded" might
   be easier to understand, espcially if it's directly related to a configuration setting
   "maxHits")
- Make sure configuration, query parameters and response keys all match up.
  (e.g. use the term "maxHits" everywhere for the same concept)
- Group related values.
  (e.g. numberOfHitsRetrieved / numberOfDocsRetrieved / stoppedRetrievingHits
  would be better as a structure `"retrieved": { "hits": 100, "docs": "10", "max-exceeded": true }` )
- Document the changes, and don't stray too far.
  (don't change things for change's sake, and make sure there's a migration guide available)
- Consider supporting the old API for a while longer.
  (we don't want a lot of duplicated maintenance, but maybe a simple adapter could be created 
   that translates between the old and new API?)
