# How to configure indexing in BlackLab

NOTE: this describes the new way of indexing, using index format configuration files, available starting from BlackLab 1.7.0. The older, more direct way of indexing is still supported (although parts may be removed or changed in future versions); see [here](indexing-with-blacklab.html). 

* <a href="#index-supported-format">Indexing documents in a supported format</a>
* <a href="#supported-formats">Supported formats</a>
* <a href="#input-format-config-overview">Basic overview of a configuration file</a>
* <a href="#sensitivity">Configuring case- and diacritics sensitivity per property</a>
* <a href="#disable-fi">Why and how to disable the forward index for a property</a>
* <a href="#word-annotated">Indexing word-annotated XML</a>
* <a href="#multiple-values">Multiple values at one position, position gaps</a>
* <a href="#standoff-annotations">Standoff annotations</a>
* <a href="#subproperties">Subannotations, for e.g. part of speech features</a>
* <a href="#payloads">Storing extra information with property values, using payloads</a>
* <a href="#tabular">Indexing tabular (CSV/TSV/SketchEngine) files</a>
* <a href="#plaintext">Indexing plain text files</a>
* <a href="#processing-values">Processing values</a>
* <a href="#metadata">Metadata</a>
    * <a href="#metadata-in-document">Embedded (in-document) metadata</a>
    * <a href="#metadata-external">Linking to external (metadata) files</a>
* <a href="#edit-index-metadata">Editing the index metadata</a>


<a id="input-format-config-overview"></a>

<a id="index-supported-format"></a>

## Indexing documents in a supported format

Start the IndexTool without parameters for help information:

    java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool
 
To create a new index:

    java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool create INDEX_DIR INPUT_FILES FORMAT

To add documents to an existing index:

    java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool add INDEX_DIR INPUT_FILES FORMAT

If you specify a directory as the INPUT_FILES, it will be scanned recursively. You can also specify a file glob (such as \*.xml; single-quote it if you're on Linux so it doesn't get expanded by the shell) or a single file. If you specify a .zip or .tar.gz file, BlackLab will automatically index the contents.

For example, if you have TEI data in /tmp/my-tei/ and want to create an index as a subdirectory of the current directory called "test-index", run the following command:

    java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool create test-index /tmp/my-tei/ tei

Your data is indexed and placed in a new BlackLab index in the "test-index" directory.

NOTE: if you don't specify a glob, IndexTool will index all files by default. You can specify a glob (like "\*.xml" or "data-\*") to change this.

To delete documents from an index:

    java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool delete INDEX_DIR FILTER_QUERY
    
Here, FILTER_QUERY is a metadata filter query in Lucene query language that matches the documents to delete. Deleting documents and re-adding them can be used to update documents.

<a id="supported-formats"></a>

## Supported formats

Here's a list of built-in input formats:

* alto (OCR XML format; see http://www.loc.gov/standards/alto/)
* folia (a corpus XML format popular in the Netherlands; see https://proycon.github.io/folia/)
* tei (a popular XML format for linguistic resources, including corpora. this variant indexes content inside the 'body' element; assumes part of speech is found in an attribute called 'type'. creating your own variant is easy (see below); see http://www.tei-c.org/)
* chat (Codes for the Human Analysis of Transcripts, format created for the CHILDES project)
* csv (comma-separated values, as exported by MS Excel)
* pagexml (OCR XML format)
* tsv (tab-separated values)
* tsv-frog (output of the Frog linguistic tagger; see https://languagemachines.github.io/frog/)
* tsv-sketch (Sketch Engine word-per-line input format, including metadata and inline tags)
* txt (plain text files)

Adding support for your own format is quite straightforward now, and can be done by writing a configuration file (described on this page) or, if you really want, by [writing Java code](add-input-format.html).

If you've written a configuration file (more on that below), you can use it with IndexTool by specifying the format name (which must match the name of the .yaml or .json file) as the FORMAT parameter on the commandline. IndexTool will search a number of directories, including the current directory and the parent of the input directory for format files with that name.

<a id="passing-indexing-parameters"></a>

## Basic overview of a configuration file

Let's see how to write a configuration file for a simple custom corpus format.

Suppose our XML files look like this:

    <?xml version="1.0" ?>
    <root>
        <document>
            <metadata id='1234'>
                <meta name='title'>How to configure indexing</meta>
                <meta name='author'>Jan Niestadt</meta>
                <meta name='description'>Shedding some light on this indexing business!</meta>
            </metadata>
            <text>
                <s>
                    <w lemma='this' pos='PRO'>This</w>
                    <w lemma='be' pos='VRB'>is</w>
                    <w lemma='a' pos='ART'>a</w>
                    <w lemma='test' pos='NOU'>test</w>.
                </s>
            </text>
        </document>
        <!-- ...more documents... -->
    </root>

Below is the configuration file you would need to index files of this type. This uses [YAML](http://yaml.org/) (good introduction [here](http://docs.ansible.com/ansible/latest/YAMLSyntax.html)), but you can also use [JSON](http://json.org/) if you prefer.

Note that the settings with names ending in "Path" are XPath 1.0 expressions (at least if you're parsing XML files - more on other file types later).

    # Identifier by which this format is found
    # (the format should be saved in a file with the same name, plus of course the
    #  .yaml extension (or .json if you prefer that format)
    name: simple-input-format
    
    # What element starts a new document?
    documentPath: //document
    
    # Annotated, CQL-searchable fields
    annotatedFields:
    
      # Document contents
      contents:
    
        # What element (relative to documentPath) contains this field's contents?
        containerPath: text
    
        # What are our word tags? (relative to containerPath)
        wordPath: .//w
    
        # What annotation can each word have? How do we index them?
        # (annotations are also called "(word) properties" in BlackLab)
        # (valuePaths relative to wordPath)
        # NOTE: forEachPath is NOT allowed for annotations, because we need to know all annotations before indexing,
        #       and with forEachPath you could run in to an unknown new annotation mid-way through.
        annotations:
    
          # Text of the <w/> element contains the word form
        - name: word
          valuePath: .
    
          # lemma attribute contains the lemma (headword)
        - name: lemma
          valuePath: "@lemma"
    
          # pos attribute contains the part of speech
        - name: pos
          valuePath: "@pos"
    
        # What tags occurring between the word tags do we wish to index? (relative to containerPath) 
        inlineTags:
          # Sentence tags
          - path: .//s
    
    # Embedded metadata in document
    metadata:
    
      # What element contains the metadata (relative to documentPath)
      containerPath: metadata
    
      # What metadata fields do we have?
      fields:
    
        # <metadata/> tag has an id attribute we want to index as docId
      - name: docId
        valuePath: "@id"
    
        # Each <meta/> tag corresponds with a metadata field
      - forEachPath: meta
        namePath: "@name"   # name attribute contains field name
        valuePath: .        # element text is the field value

This page will address how to accomplish specific things with the input format configuration. For a more complete picture that can serve as a reference, see the [annotated input format configuration file example](annotated-input-config.html). 

<a id="sensitivity"></a>

## Configuring case- and diacritics sensitivity per annotation

You can also configure what "sensitivity alternatives" (case/diacritics sensitivity) to index for each annotation (also called 'property'), using the "sensitivity" setting:

    - name: word
      valuePath: .
      sensitivity: sensitive_insensitive

Valid values for sensitivity are:

* sensitive or s: case+diacritics sensitive only
* insensitive or i: case+diacritics insensitive only
* sensitive_insensitive or si: case+diacritics sensitive and insensitive
* all: all four combinations of case-sensitivity and diacritics-sensivity

What alternatives are indexed determines how specifically you can specify the desired sensitivity when searching. Each alternative increases index size.

If you don't configure these, BlackLab will pick default values:
* annotations named "word" or "lemma" get "sensitive_insensitive"
* (internal property "punct" (punctuation between words, if any) always gets "insensitive" and internal property "starttag" (inline tags like p, s or b) always gets "sensitive")
* all other annotations get "insensitive"


<a id="disable-fi"></a>

## Why and how to disable the forward index for an annotation

By default, all annotations get a forward index. The forward index is the complement to Lucene's reverse index, and can 
quickly answer the question "what value appears in position X of document Y?". This functionality is used to generate
snippets (such as for keyword-in-context (KWIC) views), to sort and group based on context words (such as sorting on the word left of the hit) and will in the future be used to speed up certain query types.

However, forward indices take up a lot of disk space and can take up a lot of memory, and they are not always needed for every 
annotation. You should probably have a forward index for at least the word annotation, and for any annotation you'd like to sort/group on or that you use heavily in searching, or that you'd like to display in KWIC views. But if you add an annotation that is only used in certain special cases, you can decide to disable the forward index for that annotation. You can do this by adding a setting named "forwardIndex" with the value "false" to the annotation config:

    - name: wordId
      valuePath: @id
      forwardIndex: false

A note about forward indices and indexing multiple values at a single corpus position (such as happens when indexing subannotations): as of right now, the forward index will only store the first value indexed at any position. This is the value used for grouping and sorting on this annotation. In the future we may add the ability to store multiple values for a token position in the forward index, although it is likely that the first value will always be the one used for sorting and grouping.

Note that if you want KWICs or snippets that include annotations without a forward index (as well the rest of the original XML), you can switch to using the original XML to generate KWICs and snippets, at the cost of speed. To do this, pass `usecontent=orig` to BlackLab Server, or call `Hits.settings().setConcordanceType(ConcordanceType.CONTENT_STORE)`

<a id="multiple-values"></a>

## Multiple values at one position, position gaps, payloads

Other than subannotations and standoff annotations (see below) the input format configuration file doesn't currently support indexing multiple values at a single position or position gaps. If you really need one of these and it cannot be achieved with either standoff annotations or subannotations, please contact us and we may add support for your use case. Otherwise, see [indexing with BlackLab](indexing-with-blacklab.html) for the Java-based way to accomplish this.

Payloads (a Lucene feature for storing extra data at each token position) are used to index inline tags, but are not otherwise used. If you write a Java-based indexer it is possible to add your own payloads.

<a id="standoff-annotations"></a>

## Standoff annotations

Standoff annotations are annotations that are specified in a different part of the document.
For example:

    <?xml version="1.0" ?>
    <root>
        <document>
            <text>
                <w id='p1'>This</w>
                <w id='p2'>is</w>
                <w id='p3'>a</w>
                <w id='p4'>test</w>.
            </text>
            <standoff>
                <annotation ref='p1' lemma='this' pos='PRO' />
                <annotation ref='p2' lemma='be' pos='VRB' />
                <annotation ref='p3' lemma='a' pos='ART' />
                <annotation ref='p4' lemma='test' pos='NOU' />
            </standoff>
        </document>
    </root>

To index these types of annotations, use a configuration like this one:

    name: simple-input-format
    documentPath: //document
    annotatedFields:
      contents:
        containerPath: text
        wordPath: .//w
        
        # If specified, the token position for each id will be saved,
        # so you can index standoff annotations referring to this id later.
        tokenPositionIdPath: "@id"

        annotations:
        - name: word
          valuePath: .
        standoffAnnotations:
        - path: standoff/annotation      # Element containing what to index (relative to documentPath)
          refTokenPositionIdPath: "@ref" # What token position(s) to index these values at
                                         # (may have multiple matches per path element; values will 
                                         # be indexed at all those positions)
          annotations:           # The actual annotations (structure identical to regular annotations)
          - name: lemma
            valuePath: "@lemma"
          - name: pos
            valuePath: "@pos"

### Standoff annotations without a unique token id

There is an alternate way of doing standoff annotations that does not rely on a unique token id like the method described
above (although you will need some way to connect the standoff annotation to the word, obviously). It is not recommended as it is likely to be significantly slower, but in some cases, it may be useful.

Let's say you want to index a color with every word, and your document looks like this:

    <?xml version="1.0" ?>
    <root>
        <colors>
            <color id='1'>blue</color>
            <color id='2'>green</color>
            <color id='3'>red</color>
        </colors>
        <document>
            <text>
                <w colorId='1'>This</w>
                <w colorId='1'>is</w>
                <w colorId='3'>a</w>
                <w colorId='2'>test</w>.
            </text>
        </document>
    </root>

A standoff annotation of this type is defined in the same section as regular non-standoff annotations. It relies on capturing one or more values to help us locate the color we want to index at each position. These captured values are then substituted in the valuePath that fetches the color value:

    - name: color
      captureValuePaths:                  # value(s) we need from the current word to find the color
      - "@colorId"
      valuePath: /root/colors[@id='$1']   # how to get the value for this annotation from the document,
                                          # using the value(s) captured.


<a id="subproperties"></a>

## Subannotations, for e.g. making part of speech features separately searchable (EXPERIMENTAL)

Note that this feature is still (somewhat) experimental and details may change in future versions.

Part of speech sometimes consists of several features in addition to the main PoS, e.g. "NOU-C(gender=n,number=sg)". It would be nice to be able to search each of these features separately without resorting to complex regular expressions. BlackLab supports subannotations to achieve this.

Suppose your XML looks like this:

    <?xml version="1.0" ?>
    <root>
        <document>
            <text>
                <w>
                    <t>Veel</t>
                    <pos class='VNW(onbep,grad)' head='ADJ'>
                        <feat class="onbep" subset="lwtype"/>
                        <feat class="grad" subset="pdtype"/>
                    </pos>
                    <lemma class='veel' />
                </w>
                <w>
                    <t>gedaan</t>
                    <pos class='WW(vd,zonder)' head='WW'>
                        <feat class="vd" subset="wvorm" />
                        <feat class="zonder" subset="buiging" />
                    </pos>
                    <lemma class="doen"/>
                </w>
            </text>
        </document>
    </root>

Here's how to define subproperties:

    name: simple-input-format
    documentPath: //document
    annotatedFields:
      contents:
        containerPath: text
        wordPath: .//w
        
        # If specified, the token position for each id will be saved,
        # so you can index standoff annotations referring to this id later.
        tokenPositionIdPath: "@id"

        annotations:
        - name: word
          valuePath: t
        - name: lemma
          valuePath: lemma/@class
        - name: pos
          basePath: pos         # "base element" to match for this annotation.
                                # (other XPath expressions for this annotation are relative to this)
          valuePath: "@class"   # main value for the annotation
          subAnnotations:       # structure of each subannotation is the same as a regular annotation
          - name: head         
            valuePath: "@head"  # "main" part of speech is found in head attribute of <pos/> element
          - forEachPath: "feat" # other features are found in <feat/> elements
            namePath: "@subset" # subset attribute contains the subannotation name
            valuePath: "@class" # class attribute contains the subannotation value

Note that these subproperties will not have their own forward index; only the main annotation has one, and it includes only the first value indexed at any location, so the subproperty values aren't stored there either.

Adding a few subproperties per token position like this will make the index slightly larger, but it shouldn't affect performance or index size too much.

<a id="tabular"></a>

## Indexing tabular (CSV/TSV/SketchEngine) files

BlackLab works best with XML files, because they can contain any kind of (sub)annotations, (embedded or linked) metadata, inline tags, and so on. However, if your data is in a non-XML type like CSV, TSV or plain text, and you'd rather not convert it, you can still index it.

For CSV/TSV files, indexing them directly can be done by defining a tabular input format. These are "word-per-line" (WPL) formats, meaning that each line will be interpreted as a single token. Annotations simply specify the column number (or column name, if your input files have them).

(Technical note: BlackLab uses [Apache commons-csv](https://commons.apache.org/proper/commons-csv/) to parse tabular files. Not all settings are exposed at the moment. If you find yourself needing access to a setting that isn't exposed via de configuration file yet, please contact us)

Here's a simple example configuration that will parse tab-delimited files produces by the [Frog](https://languagemachines.github.io/frog/) tool:

    name: simple-tsv
    fileType: tabular

    # Options for tabular format
    tabularOptions:

      # TSV (tab-separated values) or CSV (comma-separated values, like Excel)
      type: tsv

      # Does the file have column names in the first line? [default: false]
      columnNames: false
      
      # The delimiter character to use between column values
      # [default: comma (",") for CSV, tab ("\t") for TSV]
      delimiter: ","
      
      # The quote character used around column values (where necessary)
      # [default: double quote ("\"")]
      quote: "\""
      
    annotatedFields:
      contents:
        annotations:
        - name: word
          valuePath: 2    # (1-based) column number or column name (if file has them) 
        - name: lemma
          valuePath: 3
        - name: pos
          valuePath: 5
          
The Sketch Engine takes a tab-delimited WPL input format that document tags, inline tags and "glue tags" (which indicate that there should be no space between two tokens). Here's a short example:

    <doc id="1" title="Test document" author="Jan Niestadt"> 
    <s> 
    This    PRO     this
    is      VRB     be
    a       ART     a
    test    NOU     test
    <g/>
    .       SENT    .
    </s>
    </doc>  

Here's a configuration to index this format:

    name: sketch-wpl
    fileType: tabular
    tabularOptions:
      type: tsv
      inlineTags: true  # allows inline tags such as in Sketch WPL format
                        # all inline tags encountered will be indexed
      glueTags: true    # interprets <g/> to be a glue tag such as in Sketch WPL format
    documentPath: doc   # looks for document elements such as in Sketch WPL format
                        # (attributes are automatically indexed as metadata)
    annotatedFields:
      contents:
        annotations:
        - name: word
          valuePath: 1
        - name: lemma
          valuePath: 3
        - name: pos
          valuePath: 2

If you want to index metadata from another file along with each document, you have to use valueField in the linkValues section (see below). In this case, in addition to 'fromInputFile' you can also use any document element attributes, because those are added as metadata fields automatically. So if the document element has an 'id' attribute, you could use that as a linkValue to locate the metadata file.

<a id="plaintext"></a>

## Indexing plain text files

Plain text files don't allow you to use a lot of BlackLab's features and hence don't require a lot of configuration either. If you need specific indexing features for non-tabular, non-XML file formats, please let us know and we will consider adding them. For now, here's how to configure a plain text input format:

    name: plain-text
    fileType: text

    annotatedFields:
      contents:
        annotations:
        - name: word
          valuePath: .

Note that a plain text format may only have a single annotated field. You cannot specify containerPath or wordPath. For each annotation you define, valuePath must be "." ("the current word"), but you can specify different processing steps for different annotations if you want.

There is one way to index metadata information along with plain text files, which is to look up the metadata based on the input file. The example below uses processing steps; see the relevant section below, and see the section on linking to external files for more information on that subject.

To index metadata information based on the input file path, use a section such as this one:

    linkedDocuments:
      metadata:
        store: true   # Should we store the linked document?
    
        # Values we need for locating the linked document
        # (matching values will be substituted for $1-$9 below)
        linkValues:
        - valueField: fromInputFile       # fetch the "fromInputFile" field from the Lucene doc
                                          # (this is the original path to the file that was indexed)
          process:
            # Normalize slashes
          - action: replace
            find: "\\\\"
            replace: "/"
            # Keep only the last two path parts (which indicate location inside metadata zip file)
          - action: replace
            find: "^.*/([^/]+/[^/]+)/?$"
            replace: "$1"
          - action: replace
            find: "\\.txt$"
            replace: ".cmdi"
        #- valueField: id                 # plain text has no other fields, but TSV with document elements
                                          # could, and those fields could also be used (see documentPath 
                                          # below)
    
        # How to fetch the linked input file containing the linked document.
        # File or http(s) reference. May contain $x (x = 1-9), which will be replaced 
        # with (processed) linkValue
        inputFile: http://server.example.com/metadata.zip
    
        # (Optional)
        # If the linked input file is an archive (zip is recommended), this is the path 
        # inside the archive where the file can be found. May contain $x (x = 1-9), which 
        # will be replaced with (processed) linkValue
        pathInsideArchive: some/dir/$1
    
        # Format of the linked input file
        inputFormat: cmdi

        # (Optional)
        # XPath to the (single) linked document to process.
        # If omitted, the entire file is processed, and must contain only one document.
        # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
        #documentPath: /root/metadata[@docId = $2]


<a id="processing-values"></a>

## Processing values 

It is often useful to do some simple processing on a value just before it's added to the index. This could be a simple search and replace, or combining two fields into one for easier searching, etc.

It is possible to perform string processing on [standoff] (sub)annotations, metadata values, and linkValues (in the linked document section, see "Linking to external (metadata) files").

For example, to process a metadata field value, simply add a "process" key with a list of actions to perform, like so:

    metadata:
      containerPath: metadata
      fields:
      - name: author
        valuePath: author
        
        # Do some processing on the contents of the author element before indexing
        process:
        
          # If empty, set a default value
          # (note that this could also be achieved using unknownCondition/unknownValue)
        - action: default
          value: "(unknown)"
                              
          # Normalize spaces
        - action: replace
          find: "\\s\\s+"
          replace: " "

These are all the available processing steps:

- `replace(find, replace)`: do a regex search for 'find' and replace each match with 'replace'. Group references may be used.
- `default(value)` or `default(field)`: if the field is empty, set its value to either the specified value or the value of the specified field. If you refer to a field, make sure it is defined before this field (fields are processed in order).
- `append(value)` or `append(field)`: append the specified value or the value of the specified field, using a space as the separator character. You may also specify a different `separator` is you wish, including the empty string (`""`).
- `split(separator, keep)`: split the field's value on the given separator and keep only the part indicated by keep (a 1-based integer). If `keep` is omitted, keep the first part. If `separator` is omitted, use `;`.

If you would like a new processing step to be added, please let us know.

<a id="metadata"></a>

## Metadata 

<a id="metadata-in-document"></a>

### Embedded (in-document) metadata

The basic overview (see above) included a way to index embedded metadata. Let's say this is our input file:

    <?xml version="1.0" ?>
    <root>
        <document>
            <text>
                <!-- ... document contents... -->
            </text>
            <metadata id='1234'>
                <meta name='title'>How to configure indexing</meta>
                <meta name='author'>Jan Niestadt</meta>
                <meta name='description'>Shedding some light on this indexing business!</meta>
            </metadata>
        </document>
    </root>

To configure how metadata should be indexed, you can either name each metadata field you want to index separately, or you can use "forEachPath" to index a number of similar elements as metadata:

    # Embedded metadata in document
    metadata:
    
      # What element contains the metadata (relative to documentPath)
      containerPath: metadata
    
      # What metadata fields do we have?
      fields:
    
        # <metadata/> tag has an id attribute we want to index as docId
      - name: docId
        valuePath: "@id"
    
        # Each <meta/> tag corresponds with a metadata field
      - forEachPath: meta
        namePath: "@name"   # name attribute contains field name
        valuePath: .        # element text is the field value

It's also possible to process metadata values before they are indexed (see Processing values above)


<a id="metadata-external"></a>

### Linking to external (metadata) files

Sometimes, documents link to external metadata sources, usually using an ID. You can configure linking to external files using a top-level element `linkedDocuments`. If our data looks like this:

    <?xml version="1.0" ?>
    <root>
        <document>
            <text>
                <!-- ... document contents... -->
            </text>
            <externalMetadata id="54321" />
        </document>
    </root>
    
And the metadata for this document can be found at http://example.com/metadata?id=54321, this is how to configure the document linking:

    # Any document(s) we also want to index while indexing this one
    # Usually just our external metadata.
    linkedDocuments:
    
      # Name for what this linked document represents; used to choose a field name
      # when storing the document. "metadata" is usually a good choice.
      metadata:
      
        # Should we store the linked document in our index?
        # (in this case, a field metadataCid will be created that contains a content
        #  store id, allowing you to fetch the original content of the document later)
        store: true
    
        # Values we need for locating the linked document
        # (matching values will be substituted for $1-$9 below)
        linkValues:
        
          # The value we need to determine the URL to our metadata
          # (relative to documentPath)
        - valuePath: externalMetadata/@id
    
        # How to fetch the linked input file containing the linked document.
        # File or http(s) reference. May contain $x (x = 1-9), which will be replaced 
        # with linkValue
        inputFile: http://example.com/metadata?id=$1
    
        # (Optional)
        # If the linked input file is an archive (zip is recommended because it allows 
        # random access), this is the path inside the archive where the file can be found. 
        # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
        #pathInsideArchive: some/dir/$1
    
        # Format identifier for indexing the linked file
        inputFormat: my-metadata-format

        # (Optional)
        # XPath to the (single) linked document to process.
        # If omitted, the entire file is processed, and must contain only one document.
        # May contain $x (x = 1-9), which will be replaced with (processed) linkValue
        #documentPath: /root/metadata[@docId = $2]

As you can see, it's possible to use local files or files via http; you can use archives and specify how to find the relevant metadata inside the archive; and if the linked file contains the metadata for multiple documents, you can specify a path to the specific metadata for this document.

Linking to external files is mostly done to fetch metadata to accompany a "contents" file, but there's no reason why you couldn't turn the tables if you wanted, and index a set of metadata files that link to the corresponding "contents" file. The mechanism is universal; it would even be possible to link to a document that links to another document, although that may not be very useful.

### Add a fixed metadata field to each document

You can add a field with a fixed value to every document indexed. This could be useful if you plan to add several data sets to one index and want to make sure each document is tagged with the data set name.

    metadata:
    
      containerPath: metadata
    
      fields:

        # Regular metadata field    
      - name: author
        valuePath: author
    
        # Metadata field with fixed value
      - name: collection
        value: blacklab-docs

### Controlling how metadata is fetched and indexed

By default, metadata fields are tokenized, but it can sometimes be useful to index a metadata field without tokenizing it. One example of this is a field containing the document id: if your document ids contain characters that normally would indicate a token boundary, like a period (.) , your document id would be split into several tokens, which is usually not what you want.

To prevent a metadata field from being tokenized:

    metadata:
    
      containerPath: metadata
    
      fields:

        # This field should not be split into words
      - name: docId
        valuePath: @docId
        type: untokenized


<a id="edit-index-metadata"></a>

## Editing the index metadata directly

Each BlackLab index gets a file containing "index metadata". This file is in YAML format (used to be JSON). This contains information such as the time the index was generated and the BlackLab version used, plus information about annotations and metadata fields. Some of the information is generated as part of the indexing process, and some of the information is copied directly from the input format configuration file if specified. This information is mostly used by applications to learn about the structure of the index, get human-friendly names for the various parts, and decide what UI widget to show for a metadata field.

In addition to specifying this information in your input format configuration file, it is also possible to edit the index metadata file manually. If you do this, be careful, because it might break something. It is best to use a text editor with support for YAML, and to validate the resulting file with a YAML validator such as [YAML Lint](http://www.yamllint.com/). Also remember that if you edit the index metadata file, and you later decide to generate a new index from scratch, your changes to the metadata file will be lost. If possible, it is therefore preferable to put this information in the input format configuration file directly.  

Here's a commented example of indexmetadata.yaml:

    ---
    # Display name for the index and short description
    # (not used by BlackLab. None of the display name or description values
    #  are used by BlackLab directly, but applications can retrieve them if they want)
    displayName: OpenSonar
    description: The OpenSonar corpus.
    
    # What was the name of the input format first added to this index?
    # (it is possible to add documents of different formats to the same index,
    #  so this is not a guarantee that all documents have this format)
    documentFormat: OpenSonarFolia
    
    # Total number of tokens in this corpus.
    tokenCount: 12345

    # Information about the index format and when and how it was created
    # (don't change this)
    versionInfo:
      blackLabBuildTime: "2017-08-01 00:00:00"
      blackLabVersion: "1.7.0"
      indexFormat: "3.1"
      timeCreated: "2017-07-31 16:03:37"
      timeModified: "2017-07-31 16:03:37"
      alwaysAddClosingToken: true
      tagLengthInPayload: true
    
    # Information about annotated (complex) and metadata fields
    fieldInfo:
      titleField: title            # ((optional, detected if omitted); field in the index containing
                                   #  document title; may be used by applications)
      authorField: author          # ((optional) field in the index containing author information;
                                   #  may be used by applications)
      dateField: date              # ((optional) field in the index containing document date 
                                   #  information; may be used by applications)
      pidField: id                 # ((optional, recommended) field in the index containing unique 
                                   #  document id; may be used by applications to refer to documents;
                                   #  may be used by BlackLab to directly update documents without 
                                   #  the client having to manually delete the previous version)
      defaultAnalyzerName: DEFAULT # The type of analyzer to use for metadata fields
                                   # by default (DEFAULT|whitespace|standard|nontokenizing)
      contentViewable: false       # is the user allowed to retrieve whole documents? 
      documentFormat: ''           # (not used by BlackLab. may be used by application to 
                                   #  e.g. select which XSLT to use)
      unknownValue: unknown        # what value to index if field value is unknown [unknown]
      unknownCondition: NEVER      # When is a field value considered unknown?
                                   # (other options: MISSING, EMPTY, MISSING_OR_EMPTY) [NEVER]
      
      # Information about specific metadata fields
      metadataFields:
      
        # Information about the author field
        author:
          displayName: author                      # (optional) How to display in interface
          description: The author of the document. # (optional) Description (e.g. tooltip) in interface
          type: tokenized                          # ..or text, numeric, untokenized [tokenized]
          analyzer: default                        # ..(or whitespace|standard|nontokenizing) [default]
          unknownValue: unknown                    # overrides default unknownValue for this field
          unknownCondition: MISSING_OR_EMPTY       # overrides default unknownCondition for this field
          uiType: select                           # (optional) Widget to use in interface (text|select|range)
          values:                                  # values indexed in this field
          - firstValue
          - secondValue
          valueListComplete: true                  # are all values listed here, or just a few (max. 50)?
          displayValues:                           # (optional) How to display certain values in this field
            tolkien: J.R.R. Tolkien                #   e.g. display value "tolkien" as "J.R.R. Tolkien"
            adams: Douglas Adams
          displayOrder:                            # (optional) Specific order to display values in
          - tolkien
          - adams
      
      # (optional)
      # This block allows you to define groups of metadata fields.
      # BlackLab Server will include this information on the index structure page.
      # This can be useful if you want to generate a user interface based on index metadata. 
      metadataFieldGroups:
      - name: First group
        fields:
        - author
        - title
      - name: Second group
        fields:
        - date
        - keywords
        
      # Information about annotated fields (also called "complex fields" in BlackLab)
      complexFields:
      
        # Information about the contents field
        contents:
          mainProperty: word         # used for concordances; contains char. offsets
          displayName: contents      # (optional) how to display in GUI
          description: The text contents of the document.
          noForwardIndexProps: ''    # (optional) space-separated list of annotation (property)
                                     # names that shouldn't get a forward index
          displayOrder:              # (optional) Order to display annotation search fields
          - word
          - lemma
          - pos
          
Please note: the settings pidField, titleField, authorField, dateField refer to the name of the field in the Lucene index, not an XML element name.

