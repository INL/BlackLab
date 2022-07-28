# Delete user input format

**URL** : `/blacklab-server/input-formats/<name>`

**Method** : `DELETE`

**Auth required**: YES

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

**Code**: `404 Not Found`

### Content example

```json
{
    code: "FORMAT_NOT_FOUND",
    message: "Specified format was not found"
}
```
**Code**: `400 Bad Request`

### Content example

```json
{
    code: "CANNOT_DELETE_INDEX",
    message: "Could not delete format. The format is still being used by a corpus."
}
```
