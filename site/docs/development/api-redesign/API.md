# New API examples

Here's some requests and (commented) JSON responses to illustrate 
a possible new REST API for Blacklab.

All new requests would get a `/v2/` prefix, and the old requests 
could still be supported for a while until eventually being removed.

Any information not generated or used by BlackLab, such as `displayName`, `description`, 
`documentFormat`, `textDirection`, `uiType`, etc. was moved into `"custom": {}` 
objects, that are only included if you specify `includeCustom=true`.

Generic parameters:
- `includeCustom`: include `"custom"` data? (boolean, default false)
- `outputformat`: output type (`wt` also supported?)

## TODO

- when grouping, at least `identityDisplay` should be a list of values, so grouping
  by multiple context parts can be displayed properly. Maybe rethink how these values
  are encoded altogether while we're at it. Standard encodings such as JSON are almost
  always better than a custom encoding.

## Server info

Request:

- `/blacklab/v2/` (no `"custom"` information included)
- `/blacklab/v2/?includeCustom=true` (`"custom"` information included, see below)

Response:

```json
{
  // What version of the API does this speak?
  // (NEW)
  "apiVersion": "2.0",
  
  // Information about the backend software
  // (were previously top-level keys)
  "software": {
    // What implementation are we talking to?
    // (NEW)
    "implementation": "aggregator-experimental",
    // What is the version number?
    // (renamed from blacklabVersion)
    "version": "4.0.0",
    // When was it built?
    // (renamed from blacklabBuildTime)
    "buildTime": "2022-10-01 09:28:24",
  },
  
  // Available corpora
  // (previously called "indices")
  "corpora": {
    
    "MyCorpus": {
      // Information BlackLab passes on without doing anything with it
      // Could e.g. be used by GUI application.
      "tokenCount": 459354,
      "documentCount": 1033, // added to summary
      "timeCreated": "2020-12-08 10:06:26", // added to summary
      "timeModified": "2020-12-08 10:06:26",
      "pidField": "pid", // pulled up from fieldInfo
      "contentViewable": true, // added to summary
      "status": "available",
      "indexFormat": "3.1", // pulled up from versionInfo
      "custom": {
        "displayName": "Brieven als Buit",
        "description": "Approximately 40,000 Dutch letters sent by sailors from the second half of the 17th to the early 19th centuries.",
        "documentFormat": "zeebrieven",
        "textDirection": "ltr",
      },
    },
    
  },
  
  "user": {
    "loggedIn": false,
    "mayCreateCorpus": false // (changed from canCreateIndex)
  },
  
  // (REMOVE)
  //"helpPageUrl": "/blacklab-server/help",
  
  // Previously top-level key.
  // Meant to be a free-for-all area where any useful
  // debug info can temporarily be added. Only added in debug mode.
  // Can also occur in (parts of) other responses?
  "debugInfo": {
    "cacheStatus": {
      // ...
    }
  },
}
```

## Corpus info

Request:

- `/blacklab/v2/corpora/CORPUS-NAME` (no `"custom"` information included)
- `/blacklab/v2/corpora/CORPUS-NAME?custom=true` (`"custom"` information included, see below)

Response:

```json
{
  "name": "BaB",

  "tokenCount": 459354,
  "documentCount": 1033, // added to summary
  "timeCreated": "2020-12-08 10:06:26", // added to summary
  "timeModified": "2020-12-08 10:06:26",
  "pidField": "pid", // pulled up from fieldInfo
  "contentViewable": true, // added to summary
  "status": "available",
  "indexFormat": "3.1", // pulled up from versionInfo
    
  // Software used to create this
  // (renamed from versionInfo)
  "createdBy": {
    "implementation": "blacklab",
    "version": "2.0.0-RC1",
    "buildTime": "2019-12-18 11:33:58"
  },

  // Information about our annotated fields
  "annotatedFields": {
    "contents": {
      // (removed, duplicate)
      //"fieldName": "contents",
      // (removed, superfluous)
      //"isAnnotatedField": true,
      "hasContentStore": true,
      "hasXmlTags": true,
      // (removed, always true)
      //"hasLengthTokens": true,
      "mainAnnotation": "word_or_lemma",
      "custom": {
        "displayName": "Contents",
        "description": "Contents of the documents.",
        "displayOrder": [
          "word_or_lemma",
          "word",
          "word_xml",
          "lemma",
          "grouping_lemma",
          "isclitic",
          "iswordpart",
          "grouping_pos",
          "grouping_pos_full",
          "lempos",
          "wordform_group_id",
          "pos",
          "pos_type",
          "pos_subtype",
          "pos_wordpart",
          "starttag",
          "punct"
        ]
      },
      "annotations": {
        "word_or_lemma": {
          "custom": {
            "displayName": "Word or lemma",
            "description": "",
            "uiType": "combobox"
          },
          "hasForwardIndex": true,
          "sensitivity": "ONLY_INSENSITIVE",
          "offsetsAlternative": "i",
          "isInternal": false
        },
        "word": {
          "custom": {
            "displayName": "Word",
            "description": "",
            "uiType": "combobox"
          },
          "hasForwardIndex": true,
          "sensitivity": "SENSITIVE_AND_INSENSITIVE",
          "offsetsAlternative": "",
          "isInternal": false
        }
      }
    }
  },

  "metadataFields": {
    "adr_loc_land_norm": {
      // Removed, duplicate
      //"fieldName": "adr_loc_land_norm",
      // Removed, superfluous
      //"isAnnotatedField": false,
      "type": "TOKENIZED",
      "analyzer": "DEFAULT",
      "unknownCondition": "NEVER",
      "unknownValue": "unknown",
      "fieldValues": {
        "België": 27,
        "China": 3,
        "Curaçao": 37,
        "Denemarken": 2,
        "Duitsland": 10,
        "Engeland": 1,
        "Frankrijk": 13,
        "Ghana": 1,
        "Guadeloupe": 4,
        "Guyana": 2,
        "India": 23,
        "Indonesië": 71,
        "Italië": 11,
        "Maleisië": 1,
        "Martinique": 2,
        "Nederland": 532,
        "Polen": 1,
        "Saint Croix": 1,
        "Saint Kitts": 17,
        "Sint Eustatius": 8,
        "Spanje": 11,
        "Sri Lanka": 43,
        "Suriname": 1,
        "Thailand": 1,
        "Zuid-Afrika": 2
      },
      "valueListComplete": true,
      "custom": {
        "displayName": "Country",
        "description": "",
        "uiType": "",
        "displayValues": {
          "Nederland": "NL"
        }
      }
    }
  },

  // Any custom information for applications using
  // this index, such as information for generating a 
  // GUI, etc.
  "custom": {
    // (pushed down from top-level)
    "displayName": "Brieven als Buit",
    "description": "Approximately 40,000 Dutch letters sent by sailors from the second half of the 17th to the early 19th centuries.",
    "documentFormat": "zeebrieven",
    "textDirection": "ltr",
    
    // (pushed down from top-level; renamed from fieldInfo)
    "specialFields": {
      "titleField": "title",
      "authorField": "author",
      "dateField": "witnessYear_from"
    },

    // (pushed down from top-level)
    "metadataFieldGroups": [
      {
        "name": "Letter",
        "fields": [
          "datum_jaar",
          "type_brief",
          "autograaf",
          "signatuur"
        ]
      },
      {
        "name": "Sender",
        "fields": [
          "afz_naam_norm",
          "afz_geslacht",
          "afz_klasse",
          "afz_geb_lftcat",
          "regiocode",
          "afz_rel_tot_adr"
        ]
      },
      {
        "name": "Addressee",
        "fields": [
          "adr_naam_norm",
          "adr_loc_plaats_norm",
          "adr_loc_land_norm",
          "adr_loc_regio_norm",
          "adr_loc_schip_norm"
        ]
      },
      {
        "name": "Sent from",
        "fields": [
          "afz_loc_plaats_norm",
          "afz_loc_land_norm",
          "afz_loc_regio_norm",
          "afz_loc_schip_norm"
        ]
      }
    ],

    // (pushed down from top-level)
    "annotationGroups": {
      "contents": [
        {
          "name": "Basics",
          "annotations": [
            "word",
            "lemma",
            "grouping_lemma",
            "pos",
            "grouping_pos",
            "grouping_pos_full",
            "isclitic",
            "iswordpart"
          ]
        },
        {
          "name": "Part of Speech features",
          "annotations": [
            "pos_type",
            "pos_wordpart"
          ]
        }
      ]
    }
  },
  
}
```

## Hits request

Path: `/blacklab/v2/corpora/CORPUS-NAME/hits`

Parameters:
- `patt`: CorpusQL pattern to search
- `sort`: how to sort the hits
- `first`: first row to return (`start` also supported?)
- `number`: number of rows to return (`rows` also supported?)
- `filter`: document filter query (`q` also supported?)

Example request: `/blacklab/v2/corpora/CORPUS-NAME/hits?patt="schip"&start=0&rows=100&outputformat=json`

Response:

```json
{
  "parameters": {
    "first": "0",
    "number": "100",
    "indexname": "CORPUS-NAME",
    "patt": "\"schip\"",
    "outputformat": "json"
  },
  "stats": {
    "time": {
      "search": 4,
      "count": 16003
    },
    "window": {
      "first": 0,
      "numberRequested": 2,
      "number": 2,
      "hasNext": true // (hasPrevious == first > 0)
    },
    "progress": {
      "completed": true, // if false, we're still retrieving and/sor counting
      "counted": {
        "hits": 553766,
        "docs": 281822,
        "limitReached": false // == stoppedCountingHits
      },
      "retrieved": {
        "hits": 553766,
        "docs": 281822
        "limitReached": false // == stoppedRetrievingHits
      }
    }
  },
  "hits": [
    {
      "doc": "WR-P-E-A-0000008964",
      "start": 6,
      "end": 7,
      "before": {
        "lemma": [
          "nie"
        ],
        "pos": [
          "BW()"
        ],
        "word": [
          "nie"
        ]
      },
      "match": {
        "lemma": [
          "waar"
        ],
        "pos": [
          "VNW(vb,adv-pron,obl,vol,3o,getal)"
        ],
        "word": [
          "waar"
        ]
      },
      "after": {
        "lemma": [
          ""
        ],
        "pos": [
          ""
        ],
        "word": [
          ""
        ]
      }
    },
    {
      "docPid": "WR-P-E-A-0000016858",
      "start": 28,
      "end": 29,
      "before": {
        "lemma": [
          "kweenimeren"
        ],
        "pos": [
          "WW(pv,tgw,ev)"
        ],
        "word": [
          "kweenimeer"
        ]
      },
      "match": {
        "lemma": [
          "waar"
        ],
        "pos": [
          "VNW(vb,adv-pron,obl,vol,3o,getal)"
        ],
        "word": [
          "waar"
        ]
      },
      "after": {
        "lemma": [
          ""
        ],
        "pos": [
          ""
        ],
        "word": [
          ""
        ]
      }
    }
  ],
  "docInfos": {
    "WR-P-E-A-0000016858": {
      "metadata": {
        "OriginalLanguage": [ "Unspecified" ], 
        "LicenseCode": [ "SoNaR.1-0.VL.031" ],
        "AuthorNameOrPseudonym": [ "Unspecified" ],
        "CollectionCode": [ "TMF - Topic: Actualiteit" ]
      },
      "lengthInTokens": 29,
      "mayView": true  // omit unless we implement granular permissions?
    },
    "WR-P-E-A-0000008964": {
      "metadata": {
        "Continent": [ "Europe" ],
        "PublicationPlace": [ "Unspecified" ],
      },
      "lengthInTokens": 7,
      "mayView": true  // omit unless we implement granular permissions?
    }
  }
}
```
