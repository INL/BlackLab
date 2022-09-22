# BlackLab Server

## What is it?

BlackLab Server is a web service providing a REST API for accessing BlackLab corpora. This makes it easy to use BlackLab from your favourite programming language. It can be used for anything from quick analysis scripts to full-featured corpus search applications.

This page explains how to set up and use BlackLab Server.


## Basic installation, configuration

::: tip Prefer Docker?
We have an [experimental Docker image](https://github.com/INL/BlackLab#using-blacklab-with-docker) available. A more user-friendly guide for usiing Blacklab with Docker will be available when we release our official image.
:::

### Java JRE

Install a JRE (Java runtime environment). BlackLab requires at least version 11, but version 17 or another newer version should work as well.

### Tomcat

BlackLab Server needs a Java application server to run. We will use Apache Tomcat.

Install Tomcat on your machine. See the [official docs](https://tomcat.apache.org/tomcat-9.0-doc/setup.html) or an OS-specific guide like [this one for Ubuntu](https://linuxize.com/post/how-to-install-tomcat-9-on-ubuntu-20-04/).

::: warning Tomcat 10 not yet supported
BlackLab currently uses Java EE and therefore runs in Tomcat 8 and 9, but not in Tomcat 10 (which migrated to [Jakarta EE](https://eclipse-foundation.blog/2020/06/23/jakarta-ee-is-taking-off/)). If you try to run BlackLab Server on Tomcat 10, you will get a [ClassNotFoundException](https://stackoverflow.com/questions/66711660/tomcat-10-x-throws-java-lang-noclassdeffounderror-on-javax-servlet-servletreques/66712199#66712199). A future release of BlackLab will migrate to Jakarta EE.
:::

### Configuration file

Create a configuration file `/etc/blacklab/blacklab-server.yaml`.

::: details <b>TIP:</b> Other locations for the configuration file
If `/etc/blacklab` is not practical for you, you can also place `blacklab-server.yaml` here:

- the directory specified in `$BLACKLAB_CONFIG_DIR`, if Tomcat is started with this environment variable set
- `$HOME/.blacklab/` (if you're running Tomcat under your own user account, e.g. on a development machine; `$HOME` refers to your home directory)  
- somewhere on Tomcat's Java classpath
:::

The minimal configuration file only needs to specify a location for your corpora. Create a directory for your corpora, e.g. `/data/index` and refer to it in your `blacklab-server.yaml` file:

```yaml
---
configVersion: 2

# Where BlackLab can find corpora
indexLocations:
- /data/index
```

Your corpora would be in directories `/data/index/corpus1`, `/data/index/corpus2`, etc.


### BlackLab Server WAR

Download the BlackLab Server WAR (Java web application archive). You can either:
- download the binary attached to the [latest release](https://github.com/INL/BlackLab/releases) (the file should be called `blacklab-server-<VERSION>.war`) or
- clone the [repository](https://github.com/INL/BlackLab) and build it using Maven (`mvn package`; WAR file will be in `server/target/blacklab-server-<VERSION>.war` ).

Place `blacklab-server.war` in Tomcat’s `webapps` directory (`$TOMCAT/webapps/`, where `$TOMCAT` is the directory where Tomcat is installed). Tomcat should automatically discover and deploy it, and you should be able to go to [http://servername:8080/blacklab-server/](http://servername:8080/blacklab-server/ "http://servername:8080/blacklab-server/") and see the BlackLab Server information page, which includes a list of available corpora.

::: details <b>TIP:</b> Unicode URLs
To ensure the correct handling of accented characters in (search) URLs, you should [configure Tomcat](https://tomcat.apache.org/tomcat-9.0-doc/config/http.html#Common_Attributes) to interpret URLs as UTF-8 (by default, it does ISO-8859-1) by adding an attribute `URIEncoding="UTF-8"` to the `<Connector/>` element with the attribute `port="8080"` in Tomcat's `server.xml` file.

Of course, make sure that URLs you send to BlackLab are URL-encoded using UTF-8 (so e.g. searching for `"señor"` corresponds to a request like `http://myserver/blacklab-server/mycorpus/hits?patt=%22se%C3%B1or%22` . [BlackLab Frontend](/frontend/) does this by default.
:::

::: details <b>TIP:</b> Memory usage
For larger indices, it is important to [give Tomcat's JVM enough heap memory](http://crunchify.com/how-to-change-jvm-heap-setting-xms-xmx-of-tomcat/). (If heap memory is low and/or fragmented, the JVM garbage collector might start taking 100% CPU moving objects in order to recover enough free space, slowing things down to a crawl.) If you are indexing unique ids for each word, you may also be able to save memory by [disabling the forward](how-to-configure-indexing.html#disable-fi) index for that 'unique id' annotation.

We used to also recommend locking the forward index in memory using the `vmtouch` utility, but we now believe it's better to leave disk cache management to the operating system.

:::

## Indexing data

You can index your data using the provided commandline tool IndexTool. See [Indexing with BlackLab](/guide/indexing-with-blacklab.md).

Another option is to configure user authentication to allow users to create corpora and add their data using BlackLab Server. See [here](http://localhost:8081/BlackLab/server/howtos.html#let-users-manage-their-own-corpora) to get started.

There is currently no way to use BlackLab Server to add data to non-user ("global" or regular) corpora. In the future, this will be available using Solr.

## Searching your corpus

You can try most BlackLab Server requests out by typing URLs into your browser. See [How to use](overview.md) and the [API reference](rest-api/README.md#blacklab-server-rest-api-reference) for more information. 

> **TODO:** provide a very short introduction here

We have a full-featured corpus search frontend available. See [BlackLab Frontend](/frontend/) for more information.


## What's next?

- [Take a guided tour](overview.md)
- [See all the API endpoints](rest-api)
- [Learn how to use it from your favourite language](from-different-languages.md)
- [Configuration options for BlackLab Server](configuration.md).

