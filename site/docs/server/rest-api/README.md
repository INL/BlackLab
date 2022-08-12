# BlackLab Server REST API reference

::: warning
This is a work in progress. Some of the endpoints have not been documented yet.

Also see the [overview](../overview.md).
:::

<!-- (used this [template](https://github.com/jamescooke/restapidocs/tree/master/examples)) -->

## Root endpoint

This endpoint returns available corpora and server information.

* [Corpora and server information](get.md) : `GET /blacklab-server`


## Corpus-related endpoints

These endpoints deal with a specific corpus.

All URLs should start with `/blacklab-server/<corpus-name>`.

### Information about the corpus

Information about the corpus such as size, documentFormat, fields, and status.

* [Corpus information](corpus/get.md) : `GET /`
* [Corpus status](corpus/status/get.md) : `GET /status`
* [Field information](corpus/fields/fieldname/get.md) : `GET /fields/<fieldname>`

### Find hits or documents

Search for individual matches of a text pattern, or for documents matching criteria.

* [Find hits / group hits](corpus/hits/get.md) : `GET /hits`
* [Find documents / group documents](corpus/docs/get.md) : `GET /docs`

### Information about a document

Retrieve metadata and (parts of) the content of a document.

* [Document metadata](corpus/docs/pid/get.md) : `GET /docs/<pid>`
* [Document contents](corpus/docs/pid/contents/get.md) : `GET /docs/<pid>/contents`
* [Document snippet](corpus/docs/pid/snippet/get.md) : `GET /docs/<pid>/snippet`

### Other search

* [Term frequencies](corpus/termfreq/get.md) : `GET /termfreq`
* [Autocomplete](corpus/autocomplete/field/get.md) : `GET /autocomplete`

## Manage user corpora

If user authentication and private user corpora are enabled, these can be used to manage the user's own corpora: creating/deleting, adding data and sharing.

All URLs should start with `/blacklab-server`.

* [Create user corpus](post.md) : `POST /`
* [Delete user corpus](corpus/delete.md) : `DELETE /<corpus-name>`
* [Add data to user corpus](corpus/docs/post.md) : `POST /<corpus-name>/docs`
* [Get user corpus sharing settings](corpus/sharing/get.md) : `GET /<corpus-name>/sharing`
* [Update user corpus sharing settings](corpus/sharing/post.md) : `POST /<corpus-name>/sharing`

## Other global endpoints

These endpoints are not tied to a specific corpus. All URLs should start with `/blacklab-server`.

### Input format endpoints

These give you information about input format configurations that BlackLab has access to: built-in formats, external format configuration files it found, and user formats if available.

There's also operations to add, update and delete private user formats; those are only available if user authentication and private user corpora are enabled.

* [List input formats](input-formats/get.md) : `GET /input-formats`
* [Input format configuration](input-formats/name/get.md): `GET /input-formats/<name>`
* [Input format XSLT](input-formats/name/xslt/get.md): `GET /input-formats/<name>/xslt`
* [Add or update input format](input-formats/post.md) : `POST /input-formats`
* [Delete input format](input-formats/name/delete.md) : `DELETE /input-formats/<name>`

### Debug endpoints

Can only be used in debug mode.

* [Information about the cache](cache-info.md): `GET /cache-info`
* [Clear the cache](cache-clear.md): `POST /cache-clear`

