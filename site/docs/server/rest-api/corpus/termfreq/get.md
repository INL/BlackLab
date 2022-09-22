# Term frequencies

Returns frequencies per term, sorted by descending frequency.

**URL** : `/blacklab-server/<corpus-name>/termfreq`

**Method** : `GET`

#### Parameters

| Parameter    | Description                                                                                                                                                                    |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `annotation` | Annotation to get term frequencies for. Default: main annotation (usually `word`)                                                                                              |
| `sensitive`  | Whether or not to list terms case/diacritics sensitively. Default: `false`                                                                                                     |
| `first`      | First result to return. Default: `20`                                                                                                                                          |
| `number`     | Maximum number to return. Default: `20`                                                                                                                                        |
| `filter`     | [Lucene Query Language](https://lucene.apache.org/core/8_8_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description) document filter query |
| `terms`      | comma-separated list of terms for which to get the frequencies. Default: all terms                                                                                             |


## Success Response

**Code** : `200 OK`

### Content examples

```json
{
  "termFreq": {
    "en": 14221,
    "de": 10540,
    "dat": 9546,
    "van": 9313,
    "te": 6922,
    "het": 6760,
    "met": 5468,
    "een": 5261,
    "in": 5101,
    "is": 5061,
    "ik": 4784,
    "mijn": 4649,
    "niet": 4001,
    "ick": 3773,
    "als": 3724,
    "ende": 3510,
    "den": 3439,
    "die": 3370,
    "soo": 3215,
    "op": 3083
  }
}
```

## Notes

Regular grouped hits could be used as well and should be equally fast, thanks to an optimization that recognizes this type of query (`patt` = any token (`[]`), group by match) and uses a faster path.

That makes this endpoint redundant and it may be removed in a future version.
