# BlackLab Server

## What is it?

BlackLab Server is a web service providing a REST API for accessing [BlackLab](../) corpora. This makes it easy to use BlackLab from your favourite programming language. It can be used for anything from quick analysis scripts to full-featured corpus search applications.

This page explains how to set up and use BlackLab Server.


## Features

- Supports both JSON and XML, making it very easy to use from any programming language.
- Caches results for faster response times.
- Supports keyword-in-context (KWIC) view, sorting, grouping, sampling
- Faceted search
- Capture parts of matches
- Highly configurable and tunable for performance and server load.

## Overview


### JSON or XML?

The webservice answers in JSON or XML. Selection of the desired output format is done through the HTTP Accept header (value “application/json” or “application/xml”), or by passing an extra parameter `outputformat` (value `json` or `xml`). If both are specified, the parameter has precedence. If neither are specified, the configured default format is used.

### Running results count

BlackLab Server is a stateless REST service: a particular URL will always result in the same response. There’s one exception to this: when the server has the requested set of results, it might indicate that it is still counting the total number of results, and has counted X so far. The client may repeat the request to update the running count for the user. This way, the user gets to see the first results as soon as possible, and she’ll be able to see that the total number of results is still being counted.


## Basic example

## Getting started

## Basic installation, configuration

### Using Docker

TODO

### Using Tomcat

## Installation

First, you need the BlackLab Server WAR file. You can either download the [latest release](https://github.com/INL/BlackLab/releases), or you can build it by cloning the [repository](https://github.com/INL/BlackLab GitHub) and building it using Maven.

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

### IndexTool
### via the webservice

## Manual use in the browser

## Tutorials / howtos

### User authentication, creating indices and adding data
### Convert/Tag plugins

- Create a class implementing `ConvertPlugin` or `TagPlugin`
- Make the class known to the java [SPI](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html) system.  
  In short:
  - Create a jar containing your plugin class.
  - Add a file to the jar under `/META-INF/services/` with the name `nl.inl.blacklab.indexers.preprocess.ConvertPlugin` or `nl.inl.blacklab.indexers.preprocess.TagPlugin` depending on your plugin's type.
  - Add a single line containing your class's fully-qualified class name.
  - Add your jar to BlackLab's classpath.

Configuring your plugin is possible through `blacklab.json`.  
Any options under `plugins.yourPluginId` will be passed to your plugin when it's initialized.

If your plugin was loaded successfully it can now be used by adding the following to an import format:

    tagplugin: yourPluginId
    convertPlugin: yourPluginId