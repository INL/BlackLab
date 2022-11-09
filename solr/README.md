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
  <!-- any parameters go here (none yet)
  <str name="xsltFile">./xslt/article.xslt</str>
  <str name="inputField">xml</str>
  -->
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
