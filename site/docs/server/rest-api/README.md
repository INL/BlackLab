# BlackLab Server REST API reference

::: warning
This is a work in progress. Many of the requests have not been documented yet. In the meantime, see the [overview](../overview.md).
:::

(used this [template](https://github.com/jamescooke/restapidocs/tree/master/examples))

## Non-corpora endpoints

These endpoints do not deal with one corpus, but with the server environment as a whole.

All URLs should start with `/blacklab-server`.

### General

* [List corpora and server information](get.md) : `GET /`

### Input format endpoints

These give you information about input format configurations that BlackLab has access to: built-in formats as well as external format configuration files it found.

There's also operations to add, update and delete private user formats; those are only available if user authentication and private user corpora are enabled.

* [List input formats](input-formats/get.md) : `GET /input-formats`
* [Get input format configuration](input-formats/name/get.md): `GET /input-formats/<name>`
* [Autogenerate XSLT from input format](input-formats/name/xslt/get.md): `GET /input-formats/<name>/xslt`
* [Add or update user input format](input-formats/post.md) : `POST /input-formats`
* [Delete user input format](input-formats/name/delete.md) : `DELETE /input-formats/<name>`

### Debug-related endpoints

Can only be used in debug mode.

* [Information about the cache](cache-info.md): `GET /cache-info`
* [Clear the cache](cache-clear.md): `POST /cache-clear`

## Corpora-related endpoints

These endpoints deal with one corpus.

All URLs should start with `/blacklab-server/<corpus-name>`.

### General

* [Information about the corpus](corpus/get.md) : `GET /`
* [Information about fields in the corpus](corpus/fields/get.md) : `GET /fields`
* [Corpus status](corpus/status/get.md) : `GET /status`

### Search

* [Find hits](corpus/hits/get.md) : `GET /hits`
* [Group hits](corpus/hits-grouped/get.md) : `GET /hits?group=...`
* [Term frequencies](corpus/termfreq/get.md) : `GET /termfreq`
* [Autocomplete for annotated and metadata fields](corpus/autocomplete/get.md) : `GET /autocomplete`

### Documents

* [List or find documents in corpus](corpus/docs/get.md) : `GET /docs`
* [Group documents](corpus/docs/get.md) : `GET /docs?group=...`
* [Get document metadata](corpus/docs/pid/get.md) : `GET /docs/<pid>`
* [Get document contents](corpus/docs/pid/contents/get.md) : `GET /docs/<pid>/contents`
* [Get document snippet](corpus/docs/pid/snippet/get.md) : `GET /docs/<pid>/snippet`

### Creating, adding data and deleting user corpora

If user authentication and private user corpora are enabled, these can be used to manage the user's own corpora.

* [Create user corpus](corpus/post.md) : `POST /`
* [Delete user corpus](corpus/delete.md) : `DELETE /`
* [Add data to user corpus](corpus/docs/post.md) : `POST /docs`
* [Get user corpus sharing settings](corpus/sharing/get.md) : `GET /sharing`
* [Update user corpus sharing settings](corpus/sharing/post.md) : `POST /sharing`
