# BlackLab and BlackLab server configuration files

BlackLab and BlackLab Server settings can be configured in configuration files.

* <a href="#yaml-vs-json">YAML vs. JSON</a>
* <a href="#blacklab-vs-bls-config">BlackLab vs. BlackLab Server config</a>
* <a href="#config-file-locations">Config file locations</a>
* <a href="#minimal-config-file">Minimal config file</a>
* <a href="#complete-config-file">Complete config file</a>

<a id="yaml-vs-json"></a>

## YAML vs. JSON

These files can be in YAML or JSON format. On this page, we will use the YAML format (as it allows comments and is arguably more readable), but it the two can be easily converted back and forth (for example [here](https://www.json2yaml.com/)). Just be sure to use the `.json` extension for JSON and `.yaml` or `.yml` for YAML.

<a id="blacklab-vs-bls-config"></a>

## BlackLab vs. BlackLab Server config

The configuration files are called `blacklab-server.yaml` and `blacklab.yaml`. You most likely only need `blacklab-server.yaml`, which can contain everything `blacklab.yaml` can and more.

To be precise: BlackLab programs such as QueryTool, IndexTool and BlackLab Server always look for `blacklab.yaml`. BlackLab Server also looks for `blacklab-server.yaml`. So if you're just running BlackLab Server, you probably only need a `blacklab-server.yaml` file, but if you also want to configure some detail about how IndexTool and QueryTool (or other BlackLab-based applications) run, it can be useful to have a `blacklab.yaml` file too.

<a id="config-file-locations"></a>

## Config file locations

Where should this file (or files) be located? BlackLab looks for them in the following places:

- the directory specified in `$BLACKLAB_CONFIG_DIR`, if this environment variable was defined
- `$HOME/.blacklab`
- `/etc/blacklab`
- the Java classpath

In addition, BlackLab Server will also look for `blacklab-server.yaml` in the directory where the .war file is located, e.g. `/usr/share/tomcat/webapps`.

<a id="minimal-config-file"></a>

## Minimal config file

Here's a minimal configuration file for BlackLab Server. Name it `blacklab-server.yaml` and place it in the same directory as the `blacklab-server.war` file or in `/etc/blacklab` (or configure a different location as just described).

```yaml
---
configVersion: 2

# Where indexes can be found
# (list directories whose subdirectories are indexes, or directories containing a single index)
indexLocations:
- /data/blacklab/indexes
```

This simply tells BlackLab Server where to find its indexes.

A minimal example of `blacklab.yaml` would be no file at all, as no setting in `blacklab.yaml` is required for IndexTool or QueryTool to run.

<a id="complete-config-file"></a>

## Complete config file

Below is a fully populated version of `blacklab-server.yaml`.

**NOTE:** you don't need all these sections! Just use the ones you want to specifically influence, and leave the rest out. See the minimal config file above to get started.

**NOTE:** you can also use a file called `blacklab.yaml` if you want to configure details about running `IndexTool` and `QueryTool` as well. It can only contain the `log`, `search`, `indexing` and `plugin` sections (located at the end of this example config). This file may be useful if you want to increase the number of metadata values stored in the index metadata file, for example. If you're not sure, you probably don't need this.
 

```yaml
---
# BlackLab Server config file
# ===============================================================

# This indicates we're using the new index format.
configVersion: 2

# Where indexes can be found
# (list directories whose subdirectories are indexes, or directories containing a single index)
indexLocations:
- /data/blacklab/indexes

# Directory containing each users' private indexes
# (only works if you've configured an authentication system, see below)
userIndexes: /data/blacklab/indexes/users

# Settings related to BlackLab Server's protocol, i.e. requests and responses
protocol:

    # If false, use the new element names in responses: annotatedField, annotation, etc.
    # If true, use the old element names in responses: complexField, property, etc.
    # It is recommended to set this to false. The old element names will eventually be removed.
    useOldElementNames: false


# Defaults and maximum values for parameters
# (some values will affect server load)
parameters:

    # Are searches case/accent-sensitive or -insensitive by default?
    defaultSearchSensitivity: insensitive

    # The maximum number of hits to process (return as results, and 
    # use for sorting, grouping, etc.). -1 means no limit.
    # ("maxretrieve" parameter)
    # (higher values will put more stress on the server)
    processHits:
        default: 1000000
        max: 2000000

    # The maximum number of hits to count. -1 means no limit.
    # ("maxcount" parameter)
    # (higher values will put more stress on the server)
    countHits:
        default: -1
        max: 10000000

    # Number of results per page ("number" parameter). -1 means no limit.
    # (a very high max value might lead to performance problems)
    pageSize:
        default: 50
        max: 3000

    # Context around match ("wordsaroundhit" parameter)
    # (higher values might cause copyright issues and may stress the server)
    contextSize:
        default: 5
        max: 20

    #  Default pattern language to use.
    #  The pattlang GET parameter override this value.
    patternLanguage: corpusql

    #  Default filter language to use.
    #  The filterlang GET parameter override this value.
    filterLanguage: luceneql



#  Settings for job caching.
cache:

    # Maximum size the cache may grow to (in megabytes), or -1 for no limit.
    # (we can only approximate the cache size, because different tasks refer to the same data.
    # In the real-world we will probably stay well under this. On the other hand, cache size is 
    # a lot smaller than peak memory usage, so don't this too high either; around 15% of total 
    # memory should be an okay value)
    maxSizeMegs: 500

    # How many search tasks will we cache at most? (or -1 for no limit)
    # A note about tasks: a request to BlackLab Server routinely results in 3+ simultaneous search tasks
    # being launched: a task to get a window into the sorted hits, which launches a task to get sorted hits,
    # which launches a task to get the unsorted hits. There's also usually a separate task for keeping track
    # of the running total number of hits found (which re-uses the unsorted hits task). The reason for this
    # architecture is that the results of tasks can be more easily re-used in subsequent searches that way:
    # if the sort changes, we can still use the unsorted hits task, etc. Practical upshot of this: number of 
    # tasks does not equal number of searches. Don't set this too low.
    # Also, a better way to limit cache size is using maxSizeMegs, not by specifying an arbitrary number of tasks.
    maxNumberOfJobs: 100

    # After how much time will a completed search task be removed from the cache? (in seconds)
    # (don't set this too low; instead, set maxSizeMegs, the target size for the cache)
    maxJobAgeSec: 3600

    # After how much time should a running search be aborted?
    # (larger values put stress on the server, but allow complicated searches to complete)
    maxSearchTimeSec: 300

    # How much free memory the cache should shoot for (in megabytes) while cleaning up.
    # Because we don't have direct control over the garbage collector, we can't reliably clean up until
    # this exact number is available. Instead we just get rid of a few cached tasks whenever a
    # new task is added and we're under this target number.
    targetFreeMemMegs: 100

    # The minimum amount of free memory required to start a new search task. If this memory is not available,
    # an error message is returned.
    minFreeMemForSearchMegs: 50

    # How long the client may keep results we give them in their local (browser) cache.
    # This is used to write HTTP cache headers. Low values mean clients might re-request
    # the same information, making clients less responsive and consuming more network resources.
    # Higher values make clients more responsive but could cause problems if the data (or worse,
    # the protocol) changes after an update. A value of an hour or so seems reasonable.
    clientCacheTimeSec: 3600


# Settings related to tuning server load and client responsiveness
performance:

    # How many search tasks should be able to run simultaneously
    # (set this to take advantage of the cores/threads available to the machine;
    # probably don't set it any larger, as this won't help and might hurt)
    # (-1 to autodetect)
    maxConcurrentSearches: 6

    # How many threads may a single search task use at most?
    # (lower values will allow more simultaneous searches to run;
    # higher values improve search performance, but will crowd out other searches.
    # e.g. if you set this to the same number as maxConcurrentSearches, a single 
    # search may queue all other searches until it's done)
    maxThreadsPerSearch: 3

    # Do we want to automatically pause long-running searches if there's 
    # many simultaneous users?
    pausingEnabled: true

    # How many searches may be paused at most before we start aborting them
    # (higher values means fewer aborted searches when multiple users are searching 
    # simultaneously, but it may also lead to memory being exhausted)
    maxPausedSearches: 6

    # Pause a count if the client hasn't asked about it for 10s
    # (lower values are easier on the CPU, but might take up more memory)
    abandonedCountPauseTimeSec: 10

    # Abhort a count if the client hasn't asked about it for 30s
    # (lower values are easier on the server, but might abort a count too soon)
    abandonedCountAbortTimeSec: 30


# Settings for diagnosing problems
debug:
    #  A list of IPs that will run in debug mode.
    #  In debug mode, ...
    #  - the /cache-info resource show the contents of the job cache
    #    (other debug information resources may be added in the future)
    #  - output is prettyprinted by default (can be overriden with the prettyprint
    #    GET parameter)
    addresses:
    - 127.0.0.1       #  IPv4 localhost
    - 0:0:0:0:0:0:0:1 #  IPv6 localhost


# How to determine current user
# (you only need this if you want per-user private indices or authorization)
authentication:
    system:
        class: AuthDebugFixed
        userId: jan.niestadt@ivdnt.org
        # For CLARIN (Shibboleth), use the following authentication config:
        #class: AuthClarinEppn

    #  Clients from these IPs may choose their own user id and send it along in a GET parameter userid.
    #  This setting exists for web applications that contact the webservice (partly) through the
    #  server component. They would get the same session id for each user, making them likely 
    #  to hit the maxRunningJobsPerUser setting. Instead, they should assign session IDs for each of
    #  their clients and send them along with any request to the webservice.
    overrideUserIdIps:
    - 127.0.0.1       #  IPv4 localhost
    - 0:0:0:0:0:0:0:1 #  IPv6 localhost
    
    # This is an insecure way of authenticating to BlackLab Server by sending
    # two HTTP headers. It is only intended for testing purposes.
    # 
    # Choose a 'secret' password here. Then send your requests to BlackLab Server 
    # with the extra HTTP headers X-BlackLabAccessToken (the 'secret' password) and
    # X-BlackLabUserId (the user you wish to authenticate as).
    # 
    # Needless to say this method is insecure because it allows full access to
    # all users' corpora, and the access token could potentially leak to an
    # attacker.
    #
    # DO NOT USE EXCEPT FOR TESTING
    #debugHttpHeaderAuthToken: secret


# ---------------------------------------------------------------------------
# What follows are general BlackLab settings that can apply to different 
# BlackLab applications, not just to BlackLab Server.
# (These can go in a separate file named blacklab.yaml, which is read by all
# BlackLab applications. Make sure to include configVersion as well)
# (you generally don't need to change these if you're running BlackLab Server,
# unless you're using some of the advanced features such as indexing/plugins,
# or you're trying to diagnose problems)


# Settings related to logging
log:

    # Where to log detailed information about requests and cache stats
    sqliteDatabase: /home/jan/blacklab/sqlite_log.db

    # What subjects to log messages for
    trace:
        # BL trace settings
        indexOpening: false
        optimization: true
        queryExecution: true

        # BLS trace settings
        cache: true


# Defaults for searching
# NOTE: these are BlackLab defaults, not the defaults for the BlackLab Server parameters;
# see the parameters section for those.
search:

    # Collator to use for sorting, grouping, etc.
    collator:
        language: nl   # required
        country: NL    # optional
        #variant: x     # optional

    # Default number of words around hit.
    contextSize: 5

    # The default maximum number of hits to retrieve (and use for sorting, grouping, etc.).
    # -1 means no limit, but be careful, this may stress your server.
    maxHitsToRetrieve: 1000000
    
    # The default maximum number of hits to count.
    # -1 means no limit, but be careful, this may stress your server.
    maxHitsToCount: -1

    # How eagerly to apply "forward index matching" to certain queries
    # [advanced technical setting; don't worry about this unless you want to experiment]
    fiMatchFactor: 900


# Options for indexing operations, if enabled
# (right now, in BLS, they're only enabled for logged-in users in
#  their own private area)
indexing:

    # (By default, http downloads of e.g. metadata are not allowed)
    downloadAllowed: false

    # Where to store cached files
    downloadCacheDir: /tmp/bls-download-cache

    # Max. size of entire cache in MB
    downloadCacheSizeMegs: 100

    # Max. size of single download in MB
    downloadCacheMaxFileSizeMegs: 1

    # Max. number of zip files to keep opened
    zipFilesMaxOpen: 10
    
    # Number of threads to use for indexing operations
    # (more threads is faster, but uses more memory)
    numberOfThreads: 2
    
    # Max. number of values to store per metadata field
    maxMetadataValuesToStore: 100


# Plugin options. Plugins allow you to automatically convert files (e.g. .html, .docx) or 
# apply linguistic tagging before indexing via BLS (experimental functionality).
plugins:

    # Should we initialize plugins when they are first used?
    # (plugin initialization can take a while; during development, delayed initialization is
    # often convenient, but during production, you usually want to initialize right away)
    delayInitialization: false

    # # Individual plugin configurations
    plugins:

        # Conversion plugin
        OpenConvert:
            jarPath: "/home/jan/projects/openconvert_en_tagger/OpenConvertMaven/target/OpenConvert-0.2.0.jar"

        # Tagging plugin
        DutchTagger:
            jarPath: "/home/jan/projects/openconvert_en_tagger/DutchTagger/target/DutchTagger-0.2.0.jar"
            vectorFile:  "/home/jan/projects/openconvert_en_tagger/tagger-data/sonar.vectors.bin"
            modelFile:   "/home/jan/projects/openconvert_en_tagger/tagger-data/withMoreVectorrs"
            lexiconFile: "/home/jan/projects/openconvert_en_tagger/tagger-data/spelling.tab"

```

