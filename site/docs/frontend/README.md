# BlackLab Frontend

::: warning WORK IN PROGRESS

This is a work in progress. For now, see the [GitHub repo](https://github.com/INL/corpus-frontend/#readme) for documentation.

:::

## What is it?

BlackLab Frontend is a web application for searching BlackLab corpora.

It can also be configured to allow individual users to upload corpora to be indexed and searched.


## Getting started

This page focuses on the basics to help users get up and running quickly. If you need more detailed information about features, configuration and customization, see the [GitHub repo](https://github.com/INL/corpus-frontend/#readme). 

### Basic installation, configuration

#### Using Docker

First, create a BlackLab Server instance using the main BlackLab repository (see [here](/server/#using-docker)).

Then, to add a Frontend instance, run the following commands from this repository:

```
DOCKER_BUILDKIT=1 docker-compose build
docker-compose up 
```

The config file `./docker/config/corpus-frontend.properties` is mounted inside the container. See below for the configuration details.


#### Using Tomcat

BlackLab Frontend requires Java 8 or higher and Tomcat 7, 8 or 9. Recommended are Java 11+ and Tomcat 9. (Tomcat 10 is not yet supported by BlackLab Server and Frontend)

BlackLab Frontend needs an instance of BlackLab Server to talk to; see [here](/server). For best compatibilty, ensure that the (major) version number of both applications matches. The latest releases or latest development versions of both applications should always work together.

BlackLab Frontend releases can be downloaded [here](https://github.com/INL/corpus-frontend/releases).  If you prefer building the WAR file yourself: clone this repository and use `mvn package`.

Install the application by adding `corpus-frontend.war` to Tomcat's `webapps` directory.

Optionally, create a file `corpus-frontend.properties` (name must be the same as the `.war` file) in the same directory as the BlackLab Server config file (e.g. `/etc/blacklab/`). See the [Configuration section](#Backend-configuration) for more information.

Navigate to `http://localhost:8080/corpus-frontend/` and you should see a list of available corpora you can search.


### Using the application

## UI reference

## Detailed configuration

## Tutorials / howtos

Customizing functionality



## COPIED FROM GITHUB README

## Configuration


### Backend configuration

The backend is configured through a config file (normally `corpus-frontend.properties`). **NOTE** the path of the configfile is determined by the servlet contextPath without the leading `/`, i.e. for `/TEST/corpus-frontend` a config file with the path `TEST/corpus-frontend.properties` will be looked for in the below locations!
The config file must be located in one of the following locations (in order of priority):
- `AUTOSEARCH_CONFIG_DIR` environment variable.
- `/etc/blacklab/` (`C:\etc\blacklab` on windows)
- `/vol1/etc/blacklab/` (`C:\vol1\etc\blacklab` on windows)
- `tmp` environment variable. (usually `C:\Users\%yourusername%\AppData\Local\Temp` or `C:\Windows\Temp` on windows. `/tmp` or `/var/tmp` on linux)

Example file and defaults:

```properties

# The url under which the back-end can reach blacklab-server.
# Separate from the front-end to allow connections for proxy situations
#  where the paths or ports may differ internally and externally.
blsUrl=http://localhost:8080/blacklab-server/

# The url under which the front-end can reach blacklab-server.
blsUrlExternal=/blacklab-server/

# Optional directory where you can place files to further configure and customize
#  the interface on a per-corpus basis.
# Files should be placed in a directory with the name of your corpus, e.g. files
#  for a corpus 'MyCorpus' should be placed under 'corporaInterfaceDataDir/MyCorpus/'.
corporaInterfaceDataDir=/etc/blacklab/projectconfigs/

# For unconfigured corpora, the directory where defaults may be found (optional).
# The name of a directory directly under the corpusInterfaceDataDir.
# Files such as the help and about page will be loaded from here
#  if they are not configured/available for a corpus.
# If this directory does not exist or is not configured,
#  we'll use internal fallback files for all essential data.
corporaInterfaceDefault=default

# Path to frontend javascript files (can be configured to aid development, e.g.
#  loading from an external server so the java web server does not need
#  to constantly reload, and hot-reloading/refreshing of javascript can be used).
jspath=/corpus-frontend/js

# An optional banner message that shows above the navbar.
#  It can be hidden by the user by clicking an embedded button, and stores a cookie to keep it hidden for a week.
#  A new banner message will require the user to explicitly hide it again.
# Simply remove this property to disable the banner.
bannerMessage=<span class="fa fa-exclamation-triangle"></span> Configure this however you see fit, HTML is allowed here!

# Disable xslt and search.xml caching, useful during development.
cache=true

# Show or hide the debug info checkbox in the settings menu on the search page.
# N.B. The debug checkbox will always be visible when using webpack-dev-server during development.
# It can also be toggled by calling `debug.show()` and `debug.hide()` in the browser console.
debugInfo=false

```


### Adding corpora

Corpora may be [added manually](http://inl.github.io/BlackLab/indexing-with-blacklab.html) or [uploaded by users](#Allowing-users-to-add-corpora) (if configured).

After a corpus has been added, the corpus-frontend will automatically detect it, a restart should not be required.


### Allowing users to add corpora

#### Configuring BlackLab

To allow this, BlackLab needs to be configured properly (user support needs to be enabled and user directories need to be configured).
See [here](http://inl.github.io/BlackLab/blacklab-server-overview.html#examples) for the BlackLab documentation on this (scroll down a little).

When this is done, two new sections will appear on the main corpus overview page.
They allow you to define your own configurations to customize how blacklab will index your data, create private corpora (up to 10), and add data to them.

**Per corpus configuration is not supported for user corpora created through the Corpus-Frontend.**

#### Formats

Out of the box, users can create corpora and upload data in any of the formats supported by BlackLab (`tei`, `folia`, `chat`, `tsv`, `plaintext` and more).
In addition, users can also define their own formats or extend the builtin formats.

#### Index url

There is also a hidden/experimental page (`/corpus-frontend/upload/`) for externally linking to the corpus-frontend to automatically index a file from the web.
It can be used it to link to the frontend from external web services that output indexable files.
It requires user uploading to be enabled, and there should be a cookie/query parameter present to configure the user name.
Parameters are passed as query parameters:
```properties
file=http://my-service.tld/my-file.zip
# optional
format=folia
# optional
corpus=my-corpus-name
```

If the user does not own a corpus with this name yet, it's automatically created.
After indexing is complete, the user is redirected to the search page.


### Frontend configuration

**Per corpus configuration is not supported for user corpora.**
Though you sort of can by overriding the defaults that apply to all corpora in your instance.

By placing certain files in the `corporaInterfaceDataDir` it's possible to customize several aspects of a corpus.
Files must be placed in a subdirectory with the same name as the corpus; files for `MyCorpus` should be placed in `corporaInterfaceDataDir/MyCorpus/...`

When a file is not found for a corpus, the frontend will then check the following locations
- The directory specified in `corporaInterfaceDefault`
- [Inside the WAR](src/main/resources/interface-default/)

------------

The data directory may contain the following files and subdirectories:

- `Search.xml`
  Allows you to (among other things) set the navbar links and inject custom css/js, or enable/disable pagination in documents.
  See [the default configuration](src/main/resources/interface-default/search.xml).
- `help.inc`
  Html content placed in the body of the `MyCorpus/help/` page.
- `about.inc`
  Html content placed in the body of the `MyCorpus/about/` page.
- `.xsl` files
  These are used to transform documents in your corpus into something that can be displayed on the `article/` page.
  Two files can be placed here:
    - `article.xsl`, the most important one, for your document's content (previously this was `article_${formatName}.xsl` (e.g. `article_tei.xsl` or `article_folia.xsl`). This will still work for now, however, this is deprecated).
      A small note: if you'd like to enable tooltips displaying more info on the words of your corpus, you can use the `data-tooltip-preview` (when just hovering) and `data-tooltip-content` (when clicking on the tooltip) attributes on any html element to create a tooltip. Alternatively if you don't want to write your own html, you can use `title` for the preview, combined with one or more `data-${valueName}` to create a little table. `title="greeting" data-lemma="hi" data-speaker="jim"` will create a preview containing `greeting` which can be clicked on to show a table with the details`.
    - `meta.xsl` for your document's metadata (shown under the metadata tab on the page)
      **Note:** this stylesheet does not receive the raw document, but rather the results of `/blacklab-server/docs/${documentId}`, containing only the indexed metadata.
- `static/`
  A sandbox where you can place whatever other files you may need, such as custom js, css, fonts, logo's etc.
  These files are public, and can be accessed through `MyCorpus/static/path/to/my.file`.

---

The interface may be customized in three different ways:
- [search.xml](#search.xml)
- The config (`.blf.yaml` / `.blf.json`) used to create the corpus
- Javascript & CSS

#### **Search.xml**

Allows you to set a custom display name, load custom JS/CSS, edit the shown columns for results, configure Google Analytics, and more.
See [the default configuration](src/main/resources/interface-default/search.xml) for more information.

