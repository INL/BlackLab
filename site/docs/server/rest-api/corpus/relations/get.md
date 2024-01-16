# List spans and relations

Return an overview of spans ("inline tags") and relations classes and types in the corpus, and their approximate frequencies.

**NOTE:** as a technical detail, spans are a special relation type with the builtin relation class `__tag`, but by default, we report them separately for convenience.

**NOTE2:** the reported frequencies don't take deleted documents into account. Therefore, if you've deleted documents, the frequencies may not be accurate.

**URL** : `/blacklab-server/<corpus-name>/relations`

**Method** : `GET`

| Parameter        | Description                                                                                                                                     |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `field`          | Annotated field to get relations for. If omitted, the main annotated field is used.                                                             |
| `classes`        | Comma-separated list of relations classes to return. If omitted, return all relations classes.                                                  |
| `only-spans`     | If `true`, only returns spans. Equivalent to `classes=__tag`. Default: `false`                                                                  |
| `separate-spans` | If `true` (the default), return spans differently than other relations. Set to `false` to return them as a regular relation with class `__tag`. |


## Success Response

**Code** : `200 OK`

### Content examples

Result for `/relations`:

```json
{
    "spans": {
        "p": 1,
        "s": 2,
        "named-entity": 3
    },
    "relations": {
        "dep": {
            "nsubj": 1,
            "nobj": 2,
            "nmod": 3
        },
        "fam": {
            "mother": 1,
            "father": 2,
            "sibling": 3
        }
    }
}
```

Result for `/relations?only-spans=true`:

```json
{
    "spans": {
        "p": 1,
        "s": 2,
        "named-entity": 3
    }
}
```

Result for `/relations?classes=fam`:

```json
{
    "relations": {
        "fam": {
            "mother": 1,
            "father": 2,
            "sibling": 3
        }
    }
}
```

Result for `/relations?separate-spans=false`:

```json
{
    "relations": {
        "__tag": {
            "p": 1,
            "s": 2,
            "named-entity": 3
        },
        "dep": {
            "nsubj": 1,
            "nobj": 2,
            "nmod": 3
        },
        "fam": {
            "mother": 1,
            "father": 2,
            "sibling": 3
        }
    }
}
```
