/**
 * Remove variable values such as build time, version, etc. from response.
 *
 * This enables us to compare responses from tests.
 *
 * @param response response to sanitize
 * @param removeParametersFromResponse if true, also remove summary.searchParam (for comparing different
 *   requests that should have same results) * @return response with variable values replaces with fixed ones
 */
function sanitizeBlsResponse(response, removeParametersFromResponse = false) {
    const keysToMakeConstant = {
        // Server information page
        blacklabBuildTime: true,
        blacklabVersion: true,
        indices: {
            test: {
                timeModified: true
            }
        },
        cacheStatus: true,

        // Corpus information page
        versionInfo: {
            blacklabBuildTime: true, // API v3 inconsistent name
            blacklabVersion: true,   // API v3 inconsistent name
            blackLabBuildTime: true,
            blackLabVersion: true,
            indexFormat: true,
            timeCreated: true,
            timeModified: true
        },

        // Hits/docs response
        summary: {
            searchTime: true,
            countTime: true
        },

        // Top-level timeModified key on index status page (e.g. /test/status/)
        timeModified: true
    };
    if (removeParametersFromResponse) {
        keysToMakeConstant.summary.searchParam = true;
    }

    const stripDir = (value, key) => {
        if (key === 'fromInputFile' && typeof value === 'string')
            return value.replace(/^.*[/\\]([^/\\]+)$/, "$1");
        return value;
    };

    return sanitizeResponse(response, keysToMakeConstant, stripDir);
}

/**
 * Clean up a response object for comparison.
 *
 * Replaces specified values in JSON object structure with constant ones,
 * and optionally applies a function to apply to values in the response.
 *
 * This enables us to transform responses into a form that we can easily compare.
 *
 * @param response response to clean up
 * @param keysToMakeConstant (potentially nested) object structure of keys that, when found, should be set to a
 *   constant value. Note that only the keys of this object are used. Alternatively, a single string or a list of
 *   strings is also allowed. If null or undefined are specified, an empty object is used.
 * @param transformValueFunc (optional) function to apply to all values copied from the response (i.e. values not
 *   being made constant)
 * @return sanitized response
 */
function sanitizeResponse(response, keysToMakeConstant, transformValueFunc = ((v, k = undefined) => v) ) {

    if (Array.isArray(response)) {
        // Process each element in the array recursively
        return response.map(v => sanitizeResponse(v, keysToMakeConstant, transformValueFunc));
    } else if (!(typeof response === 'object')) {
        // Regular value (probably an array element); just call the transform function and return
        return transformValueFunc(response);
    }

    // Make sure keysToMakeConstant is an object
    let recursive = false;
    if (Array.isArray(keysToMakeConstant)) {
        keysToMakeConstant = Object.fromEntries(keysToMakeConstant.map(v => [v, true]));
    } else if (typeof keysToMakeConstant === 'object') {
        recursive = true;
    } else if (typeof keysToMakeConstant === 'string') {
        keysToMakeConstant = { [keysToMakeConstant]: true };
    } else {
        keysToMakeConstant = {};
    }

    // Replace any of the keys from keysToMakeConstant with constant values,
    // and perform any other fixes if fixValueFunc was supplied.
    const cleanedData = {};
    for (let key in response) {
        const value = response[key];
        if (key in keysToMakeConstant) {
            // This is (or contains) a variable value we don't want to compare.
            if (recursive && typeof keysToMakeConstant[key] === 'object' && typeof value === 'object' && !Array.isArray(value)) {
                // Subobject; recursively fix this part of the response
                cleanedData[key] = sanitizeResponse(value, keysToMakeConstant[key], transformValueFunc);
            } else {
                // Single value or array. Make this response value fixed
                cleanedData[key] = "VALUE_REMOVED";
            }
        } else {
            // No values to make constant, just regular values we want to compare.
            if (Array.isArray(value)) {
                // Call ourselves to process the array
                // Note that we apply transformValueFunc on the result again so we can pass the key for the array,
                // otherwise key-specific rules won't work.
                cleanedData[key] = value.map(v => transformValueFunc(sanitizeResponse(v, keysToMakeConstant[key], transformValueFunc), key));
            } else if (typeof value === 'object') {
                // Object; call ourselves recursively to sanitize it
                cleanedData[key] = sanitizeResponse(value, keysToMakeConstant[key], transformValueFunc);
            } else {
                // Regular value; call transform function.
                cleanedData[key] = transformValueFunc(value, key);
            }
        }
    }
    return cleanedData;
}

const response = {
    "summary": {
        "searchParam": {
            "group": "field:title",
            "indexname": "test",
            "number": "30",
            "op": "docs",
            "patt": "\"a\"",
            "sort": "size,identity",
            "viewgroup": "str:interview about conference experience and impressions of city",
            "wordsaroundhit": "1"
        },
        "searchTime": "VALUE_REMOVED",
        "countTime": "VALUE_REMOVED",
        "windowFirstResult": 0,
        "requestedWindowSize": 30,
        "actualWindowSize": 1,
        "windowHasPrevious": false,
        "windowHasNext": false,
        "stillCounting": false,
        "numberOfHits": 8,
        "numberOfHitsRetrieved": 8,
        "numberOfDocs": 1,
        "numberOfDocsRetrieved": 1,
        "docFields": {
            "pidField": "pid",
            "titleField": "title"
        },
        "metadataFieldDisplayNames": {
            "fromInputFile": "From input file",
            "pid": "Pid",
            "title": "Title"
        }
    },
    "docs": [
        {
            "docPid": "PRint602",
            "numberOfHits": 8,
            "docInfo": {
                "fromInputFile": [
                    "PRint602.xml"
                ],
                "pid": [
                    "PRint602"
                ],
                "title": [
                    "interview about conference experience and impressions of city"
                ],
                "lengthInTokens": 268,
                "mayView": true
            },
            "snippets": [
                {
                    "left": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "mhm",
                            "and",
                            "we",
                            "be",
                            "do"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "mhm",
                            "and",
                            "we",
                            "are",
                            "doing"
                        ]
                    },
                    "match": {
                        "punct": [
                            " "
                        ],
                        "lemma": [
                            "a"
                        ],
                        "pos": [
                            ""
                        ],
                        "word": [
                            "a"
                        ]
                    },
                    "right": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "project",
                            "on",
                            "",
                            "communication",
                            "and"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "project",
                            "on",
                            "p_intercultural",
                            "communication",
                            "and"
                        ]
                    }
                },
                {
                    "left": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "something",
                            "or",
                            "be",
                            "you",
                            "just"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "something",
                            "or",
                            "are",
                            "you",
                            "just"
                        ]
                    },
                    "match": {
                        "punct": [
                            " "
                        ],
                        "lemma": [
                            "a"
                        ],
                        "pos": [
                            ""
                        ],
                        "word": [
                            "a"
                        ]
                    },
                    "right": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "listener",
                            "to",
                            "this",
                            "",
                            "erm"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "listener",
                            "to",
                            "this",
                            "_0",
                            "erm"
                        ]
                    }
                },
                {
                    "left": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "erm",
                            "er",
                            "er",
                            "i",
                            "be"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "erm",
                            "er",
                            "er",
                            "i",
                            "'m"
                        ]
                    },
                    "match": {
                        "punct": [
                            " "
                        ],
                        "lemma": [
                            "a"
                        ],
                        "pos": [
                            ""
                        ],
                        "word": [
                            "a"
                        ]
                    },
                    "right": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "",
                            "a",
                            "session",
                            "chair",
                            "okay"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "sess-",
                            "a",
                            "session",
                            "chair",
                            "okay"
                        ]
                    }
                },
                {
                    "left": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "er",
                            "i",
                            "be",
                            "a",
                            ""
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "er",
                            "i",
                            "'m",
                            "a",
                            "sess-"
                        ]
                    },
                    "match": {
                        "punct": [
                            " "
                        ],
                        "lemma": [
                            "a"
                        ],
                        "pos": [
                            ""
                        ],
                        "word": [
                            "a"
                        ]
                    },
                    "right": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "session",
                            "chair",
                            "okay",
                            "you",
                            "be"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "session",
                            "chair",
                            "okay",
                            "you",
                            "are"
                        ]
                    }
                },
                {
                    "left": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "session",
                            "chair",
                            "okay",
                            "you",
                            "be"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "session",
                            "chair",
                            "okay",
                            "you",
                            "are"
                        ]
                    },
                    "match": {
                        "punct": [
                            " "
                        ],
                        "lemma": [
                            "a"
                        ],
                        "pos": [
                            ""
                        ],
                        "word": [
                            "a"
                        ]
                    },
                    "right": {
                        "punct": [
                            " ",
                            " ",
                            " ",
                            " ",
                            " "
                        ],
                        "lemma": [
                            "session",
                            "chair",
                            "yah",
                            "yah",
                            "yah"
                        ],
                        "pos": [
                            "",
                            "",
                            "",
                            "",
                            ""
                        ],
                        "word": [
                            "session",
                            "chair",
                            "yah",
                            "yah",
                            "yah"
                        ]
                    }
                }
            ]
        }
    ]
};

const san = sanitizeBlsResponse(response, false);
console.log(JSON.stringify(san.docs[0].docInfo));
//console.log(JSON.stringify(san));
