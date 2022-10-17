# Document contents

Retrieve the original input document, presuming it's stored in the corpus (it is by default).

**URL** : `/blacklab-server/<corpus-name>/docs/<pid>/contents`

**Method** : `GET`

#### Parameters

All parameters are optional.  `wordstart` and `wordend` refer to token position in a document, 0-based.

Partial contents XML output will be wrapped in `<blacklabResponse/>` element to ensure a single root element.

| Parameter   | Description                                                                                                                                                                  |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patt`      | Pattern to highlight in the document. `<hl>...</hl>` tags will be added to highlight hits.                                                                                   |
| `wordstart` | First word position we want returned. -1 for document start.<br/>**NOTE:** when greater than -1, any content before the first word will _not_ be included in the response!   |
| `wordend`   | First word position we don't want returned. -1 for document end.<br/>**NOTE:** when greater than -1, any content after the last word will _not_ be included in the response! |


## Success Response

**Code** : `200 OK`

### Content examples

_(the original input document, be it XML or some other format)_

