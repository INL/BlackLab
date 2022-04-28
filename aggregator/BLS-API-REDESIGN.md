# BlackLab Server API redesign

The BLS API has quite a few quirks that can make it confusing and annoying to work with.
If we break compatibility anyway (e.g. because we're integrating with Solr), how might
we redesign the API?

## Problems

General:
- Be consistent.
  (if information is given in multiple places, e.g. on the server info page as well
   as on the index info page, use the same structure and element names)
- Pick explanatory names.
  (e.g. "stoppedRetrievingHits" leads to the question "why". "maxHitsExceeded" might
   be easier to understand, especially if it's directly related to a configuration 
   setting "maxHits")
- Make sure configuration, query parameters and response keys use the same terminology.
  (e.g. use the term "maxHits" everywhere for the same concept)
- Group related values.
  (e.g. numberOfHitsRetrieved / numberOfDocsRetrieved / stoppedRetrievingHits
  would be better as a structure `"retrieved": { "hits": 100, "docs": "10", "max-exceeded": true }` )
- Don't include static info on dynamic (results) pages.
  (e.g. don't send display names for all metadata fields with each hits results)
- Replace left/right with before/after (makes more sense for RTL languages)
- Document the changes, and don't stray too far.
  (don't change things for change's sake, and make sure there's a clear migration 
  guide available)
- Consider supporting the old API for a while longer.
  (we don't want a lot of duplicated maintenance, but maybe an adapter could 
  be created that translates between the old and new API. Such an adapter could
  use the experimental aggregator code as a starting point)

XML-related:
- JSON should probably be our primary output format, and the XML structure should 
  just be a dumb translation from JSON, for those who need it (e.g. to pass through 
  XSLT). So e.g. no difference in concordance structure between JSON and XML.
- Avoid attributes; use elements for everything.
- Avoid dynamic XML element names (e.g. don't use map keys for XML element names.
  Not an issue if we copy JSON structure)
- When using `usecontent=orig`, don't make the content part of the XML anymore,
  escape it using CDATA (again, same as in JSON). Also consider just returning both
  the FI concordances as well as the original content (if requested), so the response
  structure doesn't fundamentally change because of one parameter value.
