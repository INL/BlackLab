# BlackLab Server

## What is it?

BlackLab Server is a web service providing a REST API for accessing [BlackLab](../) corpora. This makes it easy to use BlackLab from your favourite programming language. It can be used for anything from quick analysis scripts to full-featured corpus search applications.

This page explains how to set up and use BlackLab Server.


## Overview

### JSON or XML?

The webservice answers in JSON or XML. Selection of the desired output format can be done two ways:

- by passing the HTTP header `Accept` with the value `application/json` or `application/xml`
- by passing an extra parameter `outputformat` with the value `json` or `xml`.

If both are specified, the parameter has precedence.

We'll usually use JSON in our examples.


### Running results count

BlackLab Server is mostly stateless: a particular URL will always result in the same response. An exception to this is the running result count. When you're requesting a page of results, and there are more results to the query, BlackLab Server will retrieve these results in the background. It will report how many results it has retrieved and whether it has finished or is still retrieving.

A note about retrieving versus counting. BLS has two limits for processing results: maximum number of hits to retrieve/process and maximum number of hits to count. Retrieving or processing hits means the hit is stored and will appear on the results page, is sorted, grouped, faceted, etc. If the retrieval limit is reached, BLS will still keep counting hits but will no longer store them.


## Basic example

TODO

## Basic installation, configuration

This is an overview of installing BlackLab on a server running Apache Tomcat.

::: tip Prefer Docker?
We have an [experimental Docker image](https://github.com/INL/BlackLab#using-blacklab-with-docker) available. A more user-friendly guide for usiing Blacklab with Docker will be available when we release our official image.
:::

First, you need the BlackLab Server WAR file. You can either download the [latest release](https://github.com/INL/BlackLab/releases), or you can build it by cloning the [repository](https://github.com/INL/BlackLab) and building it using Maven.

BlackLab Server needs to run in a Java application server that support servlets. We’ll assume Apache Tomcat here, but others should work almost the same.

::: warning PLEASE NOTE
BlackLab currently uses Java EE and therefore runs in Tomcat 8 and 9, but not in Tomcat 10 (which migrated to [Jakarta EE](https://eclipse-foundation.blog/2020/06/23/jakarta-ee-is-taking-off/)). If you try to run BlackLab Server on Tomcat 10, you will get a [ClassNotFoundException](https://stackoverflow.com/questions/66711660/tomcat-10-x-throws-java-lang-noclassdeffounderror-on-javax-servlet-servletreques/66712199#66712199). A future release of BlackLab will migrate to Jakarta EE.
:::

For larger indices, it is important to [give Tomcat's JVM enough heap memory](http://crunchify.com/how-to-change-jvm-heap-setting-xms-xmx-of-tomcat/). (If heap memory is low and/or fragmented, the JVM garbage collector might start taking 100% CPU moving objects in order to recover enough free space, slowing things down to a crawl.) If you are indexing unique ids for each word, you may also be able to save memory by [disabling the forward](how-to-configure-indexing.html#disable-fi) index for that 'unique id' annotation.

Create a configuration file `blacklab-server.yaml` in `/etc/blacklab/` or, if you prefer, on the application server’s classpath. Make sure the `indexLocations` setting is correctly specified (it should point to a directory containing one or more BlackLab indices as subdirectories, or to a single index directory). The minimal configuration file looks like this:

```yaml
---
configVersion: 2

# Where indexes can be found
# (list directories whose subdirectories are indexes, or directories containing a single index)
indexLocations:
- /data/index
```

(for more information about configuration BlackLab and BlackLab Server, see [Configuration files](configuration-files.html))

Place blacklab-server.war in Tomcat’s webapps directory ($TOMCAT/webapps/). Tomcat should automatically discover and deploy it, and you should be able to go to [http://servername:8080/blacklab-server/](http://servername:8080/blacklab-server/ "http://servername:8080/blacklab-server/") and see the BlackLab Server information page, which includes a list of available corpora.

To ensure the correct handling of accented characters in (search) URLs, you should make sure that your URLs are URL-encoded UTF-8 (so e.g. searching for "señor" corresponds to a request like http://myserver/blacklab-server/mycorpus/hits?patt=%22se%C3%B1or%22 . You should also [tell Tomcat](https://tomcat.apache.org/tomcat-7.0-doc/config/http.html#Common_Attributes) to interpret URLs as UTF-8 (by default, it does ISO-8859-1) by adding an attribute URIEncoding="UTF-8" to the Connector element with the attribute port="8080" in Tomcat's server.xml file.

To (significantly!) improve performance of certain operations, including sorting and grouping large result sets, you might want to consider using the [vmtouch](https://github.com/INL/BlackLab/wiki/Improve-search-speed-using-the-disk-cache "https://github.com/INL/BlackLab/wiki/Improve-search-speed-using-the-disk-cache") tool to lock the forward index files in the OS's disk cache. You could also serve these files (or the entire index) from an SSD.


## Indexing data

You can index your data using the provided commandline tool IndexTool. See [Indexing with BlackLab](/guide/indexing-with-blacklab.md).

Another option is to configure user authentication to allow users to create corpora and add their data using BlackLab Server. Search for "authentication" in the [example config file](configuration.md#complete-config-file).

There is currently no way to use BlackLab Server to add data to non-user ("global" or regular) corpora. In the future, this will be available using Solr.

## Searching your corpus

You can try most BlackLab Server requests out by typing URLs into your browser. See the [REST API documentation](rest-api/README.md#blacklab-server-rest-api-reference) for the endpoints. 

We have a full-featured corpus search frontend available. See [BlackLab Frontend](/frontend/) for more information.
