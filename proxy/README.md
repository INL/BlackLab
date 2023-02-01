# BlackLab Reverse Proxy Server

A reverse proxy between the client and a BlackLab backend (either BLS or Solr). 

One goal of this is API compatibility, to offer one API regardless of whether you're using (an older version of) BLS or Solr+BlackLab.

Another goal is security: it is generally not recommended to open the Solr port directly to the internet. This provides a layer of security, enabling only the BlackLab functionality and shielding any other Solr functionality (and with it, potential security issues).

Work in progress.

## Configuration

Create a configuration file named `proxy.yaml` in one of these locations: `$HOME/.blacklab/`, `/etc/blacklab/`, with 
the following structure (for proxying to Solr+BlackLab):

```yaml
proxyTarget:
  url: http://localhost:8983/solr
  protocol: solr
  defaultCorpusName: testcore      # needed for "server-wide" BLS operations (Solr request always needs a core)
```

To proxy to a BlackLab Server instance instead:

```yaml
proxyTarget:
  url: http://localhost:8080/blacklab-server
  protocol: bls
```
