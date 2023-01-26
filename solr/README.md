# Solr integration (experimental)

This provides classes for integrating BlackLab with Solr. The ultimate goal of this is to enable distributed search via SolrCloud. This is a work in progress. 

To enable this plugin for your core, in your `solrconfig.xml`, add this to the `<config>` section:

```xml
<!-- Load the blacklab-solr plugin -->
<lib dir="${solr.install.dir:/opt/solr}/contrib/blacklab-solr/lib/" regex="blacklab-solr.*\.jar" />
```

Add the `blacklab-search` search component, and specify the XSLT file and the Solr field containing the input XML:

```xml
<!-- Our Apply XSLT SearchComponent -->
<searchComponent name="blacklab-search" class="org.ivdnt.blacklab.solr.BlackLabSearchComponent" >
    
    <!-- Where to find a core's BlackLab config file (value shown below is the default path).
         Each core gets their own config file (although certain settings are engine-wide...)
    -->
    <str name="configFile">conf/blacklab-webservice.yaml</str>

</searchComponent>
```

To run the plugin on your `/select` handler, add this to the `<requestHandler name="/select" ...>` element:

```xml
<!-- After all other components (standard Solr per-document search) have run, run the BlackLab (per-hit) search -->
<arr name="last-components">
  <str>blacklab-search</str>
</arr>
```

## Docker

A `Dockerfile` is included which adds this to a Solr image. Build the image with this command:

    docker build -t instituutnederlandsetaal/blacklab-solr:1 -f Dockerfile .

You can derive your own `Dockerfile` from this. Here's an example that adds a Solr configuration dir to the image and creates a core based on that configuration:

```Dockerfile
# Based on Solr + XSLT plugin image.
# Creates our core (using the config).
FROM instituutnederlandsetaal/blacklab-solr:1

# Copy the configuration files for our core
COPY . /opt/solr/server/solr/configsets/blacklab/conf

# Pre-create core (using the config copied above)
# as soon as the container is started.
CMD ["solr-precreate", "my-blacklab-corpus", "/opt/solr/server/solr/configsets/blacklab"]
```

## Requests

In addition to standard Solr parameters like `q` and `fq` for document 
filtering, you can use all of the same parameters BlackLab Server uses,
but you should prefix them with `bl.`. Some examples are shown below.

Note that we always pass `rows=0` to Solr, because we don't want Solr's 
document results; BlackLab will send a list of hits and include the document info
for these hits automatically.

Find hits: https://server/solr/corename/select?bl.op=hits&bl.patt=%22the%22&q=*%3A*&rows=0

As an alternative to passing separate `bl.NAME` parameters, you can also pass a JSON
structure with all the parameters in a parameter called `bl.req`, e.g.:

```json
{ "op": "hits", "patt": "\"the\"" }
```

The full URL in this case would be: https://server/solr/corename/select?bl.req=%7B%22op%22%3A%22hits%22%2C%22patt%22%3A%22%5C%22the%5C%22%22%7D&q=*%3A*&rows=0

The JSON structure for `group` and `viewgroup` is not a string with separators, but an array of arrays:

```json
{
  "op": "hits",
  "patt": "\"the\"",
  "group": [ [ "field", "title" ] ],
  "viewgroup": [ [ "str", "interview about city" ] ]
}
```

the above `group` and `viewgroup` parts correspond to `bl.group=field:title&bl.viewgroup=str:interview about city`.

The values of `bl.op` are:

| bl.op              | Operation                                        | BLS URL equivalent        | Required parameters |
|--------------------|--------------------------------------------------|---------------------------|---------------------|
| server-info        | Server information                               | /                         |                     |
| corpus-info        | Corpus information, including fields and values  | /CORPUS                   |                     |
| corpus-status      | Corpus (indexing) status                         | /CORPUS/status            |                     |
| field-info         | Info about (metadata or annotated) field         | /CORPUS/field/FIELDNAME   | field               |
| hits               | Search (and optionally group) hits               | /CORPUS/hits              |                     |
| docs               | Search (and optionally group) documents          | /CORPUS/docs              |                     |
| doc-info           | Get document metadata and other information      | /CORPUS/docs/PID          | docpid              |
| doc-contents       | Get the full contents of a document (if allowed) | /CORPUS/docs/PID/contents | docpid              |
| termfreq           | Calculate term frequencies                       | /CORPUS/termfreq          |                     |
| autocomplete       | Return terms matching a prefix in a field        | /CORPUS/autocomplete      |                     |
| list-input-formats |                                                  | /CORPUS/input-formats     |                     |

(WIP)

bl.op=docs&bl.patt="the"

bl.op=docs&bl.patt="the"&bl.group=field:title&bl.viewgroup=str:interview about conference experience and impressions of city

bl.op=doc-info&bl.docpid=PRint602&bl.listvalues=lemma,word&bl.wordstart=100&bl.wordend=200&bl.field=title&bl.patt="the"&bl.group=field:title&bl.viewgroup=str:interview about conference experience and impressions of city

bl.op=doc-contents&bl.docpid=PRint602

bl.op=doc-snippet&bl.docpid=PRint602&bl.listvalues=lemma,word&bl.wordstart=100&bl.wordend=200&bl.field=title&bl.patt="the"&bl.group=field:title&bl.viewgroup=str:interview about conference experience and impressions of city

bl.op=termfreq&bl.field=contents&bl.annotation=lemma

bl.op=autocomplete&bl.field=contents&bl.annotation=lemma&bl.term=a
