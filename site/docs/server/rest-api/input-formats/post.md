# Add or update user input format

**URL** : `/blacklab-server/input-formats`

**Method** : `POST`

**Auth required**: YES

## Input data

- `data`: a file upload of the configuration file

## Success Response

**Code** : `200 OK`

### Content example

```json
{
    code: "SUCCESS",
    message: "Format added."
}
```

## Error Response

**Code**: `400 Bad Request`

### Content example

```json
{
    code: "CANNOT_CREATE_INDEX",
    message: "Could not create/overwrite format. The server is not configured with support for user content."
}
```

## TODO

- Succes should probably return `201 Created`
- The success message should indicate whether the format was created or added.
- `CANNOT_CREATE_INDEX` is a misnomer and should probably be changed to a more generic CANNOT_CREATE.
- Maybe there should be an `overwrite` parameter that indicates whether or not it is your intention to overwrite an existing format.
