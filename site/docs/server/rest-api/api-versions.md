# API versions

There are currently two supported versions of the REST API, with only minor differences between them, mostly related to consistency between responses and leaving out redundant information.

On any BLS request, include the `api` parameter to specify which API version to use. If you don't specify an API version, the default version will be used. 

To set the default API version, configure `parameters.api` in your `blacklab-server.yaml`.

Valid values for API version include: `3.0`, `4.0`, `3`, `4`, `current`, `experimental`. More may be added in the future, and support for older versions will eventually be dropped.

API version numbers roughly match the BlackLab version where they became the default API version.


## API changes from 3.0 to 4.0

These are the differences between API version 3.0 and 4.0:

- New key added to server info page (`/`): `apiVersion` (valid values: `3.0` and `4.0`; assume `3.0` if missing)
- Two keys were renamed on the corpus info page (`/CORPUSNAME`) to be more consistent: in the `versionInfo` block,
  `blacklabVersion` and `blacklabBuildTime` are now spelled with a lowercase `l`, just like on the server info page.
- The `/hits` and `/docs` responses don't include the `docFields` and `metadataFieldDisplayNames` keys anymore.
  This information can be found on the corpus info page (`/CORPUSNAME`) and need not be sent with each hit request.
- For similar reaons, the document info page (`/docs/DOC_PID`) no longer includes `docFields`, `metadataFieldDisplayNames` or
  `metadataFieldGroups` by default.
- For annotated fields, `displayOrder` will no longer include internal annotations (e.g. `punct` and `_relation`(previously called `starttag`)), as these are generally not meant to be displayed as search fields.

For all of these changes, you can add `api=3` to produce the old behaviour. You can also specify
`parameters.api=3` in `blacklab-server.yaml` (or `blacklab-webservice.yaml` for Solr).

This is meant as a transitional measure, and v3 compatibility will eventually be removed, likely in a future version 5.0.
