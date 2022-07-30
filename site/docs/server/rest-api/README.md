# BlackLab Server REST API reference

::: warning
This is a work in progress. Some of the endpoints have not been documented yet.

Also see the [overview](../overview.md).
:::

<!-- (used this [template](https://github.com/jamescooke/restapidocs/tree/master/examples)) -->

## Root endpoint

This endpoint gives information about the server environment and available corpora.

* [List of corpora and server information](get.md) : `GET /`


## Corpus-related endpoints

These endpoints deal with a specific corpus.

All URLs should start with `/blacklab-server/<corpus-name>`.

### Information about the corpus

* [General information, list of fields](corpus/get.md) : `GET /`
* [Information about a field in the corpus](corpus/fields/fieldname/get.md) : `GET /fields/<fieldname>`
* [Corpus status](corpus/status/get.md) : `GET /status`

### Find hits or documents

Search for individual matches of a text pattern.

* [Find hits; group hits](corpus/hits/get.md) : `GET /hits`
* [Find documents; list all documents; group documents](corpus/docs/get.md) : `GET /docs`

### Information about a document

Search for documents matching a text pattern and/or metadata query.

* [Document metadata](corpus/docs/pid/get.md) : `GET /docs/<pid>`
* [Document contents](corpus/docs/pid/contents/get.md) : `GET /docs/<pid>/contents`
* [Document snippet](corpus/docs/pid/snippet/get.md) : `GET /docs/<pid>/snippet`

### Other search

* [Term frequencies](corpus/termfreq/get.md) : `GET /termfreq`
* [Autocomplete](corpus/autocomplete/get.md) : `GET /autocomplete`

### Manage user corpora

If user authentication and private user corpora are enabled, these can be used to manage the user's own corpora: creating/deleting, adding data and sharing.

* [Create user corpus](corpus/post.md) : `POST /`
* [Delete user corpus](corpus/delete.md) : `DELETE /`
* [Add data to user corpus](corpus/docs/post.md) : `POST /docs`
* [Get user corpus sharing settings](corpus/sharing/get.md) : `GET /sharing`
* [Update user corpus sharing settings](corpus/sharing/post.md) : `POST /sharing`

## Other global endpoints

These endpoints are not tied to a specific corpus.  All URLs should start with `/blacklab-server`.

### Input format endpoints

These give you information about input format configurations that BlackLab has access to: built-in formats as well as external format configuration files it found.

There's also operations to add, update and delete private user formats; those are only available if user authentication and private user corpora are enabled.

* [List input formats](input-formats/get.md) : `GET /input-formats`
* [Get input format configuration](input-formats/name/get.md): `GET /input-formats/<name>`
* [Autogenerate XSLT from input format](input-formats/name/xslt/get.md): `GET /input-formats/<name>/xslt`
* [Add or update user input format](input-formats/post.md) : `POST /input-formats`
* [Delete user input format](input-formats/name/delete.md) : `DELETE /input-formats/<name>`

### Debug endpoints

Can only be used in debug mode.

* [Information about the cache](cache-info.md): `GET /cache-info`
* [Clear the cache](cache-clear.md): `POST /cache-clear`

