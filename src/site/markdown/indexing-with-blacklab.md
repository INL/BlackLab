# Indexing with BlackLab

This page goes into detail about indexing documents with BlackLab.
For a simple tutorial, see [Adding a new input format](add-input-format.html).

## Indexing documents in a supported format

Start the IndexTool without parameters for help information:

	java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool
 
Index documents in a supported format:

    java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool create INDEX_DIR INPUT_FILES FORMAT

If you specify a directory as the INPUT_FILES, it will be scanned recursively. You can also specify a file glob (such as \*.xml; single-quote it if you're on Linux so it doesn't get expanded by the shell) or a single file. If you specify a .zip or .tar.gz file, BlackLab will automatically index the contents.

For example, if you have TEI data in /tmp/my-tei/ and want to create an index as a subdirectory of the current directory called "test-index", run the following command:

	java -cp BLACKLAB_JAR nl.inl.blacklab.tools.IndexTool create test-index /tmp/my-tei/ tei

Your data is indexed and placed in a new BlackLab index in the "test-index" directory.

## Passing indexing properties

The indexing properties configure how certain indexing processes are performed.

@@@ describe

## Configuring the index structure

What we call the "index structure" consists of some top-level index information (name, description, etc.), what word-level annotations ("properties") you want to index, what metadata fields there are and how they should be indexed, and more.

By default, a default index structure is determined by BlackLab. However, you can influence exactly how your index is created using a customized index structure file. If you specify such an index structure file when creating the index, it will be used as a template for the index metadata file, and so you won't have to specify the index structure file again when updating your index later; all the information is now in the index metadata file. It is possible to edit the index metadata file manually as well, but use caution, because it might break something.

@@@ JSON file description

### When and how to disable the forward index for a property

...

## Indexing documents in a custom format

See also [Adding an input format](add-input-format.html).

If you have text in a format that isn't supported by BlackLab yet, you will have to create a DocIndexer class to support the format. You can use the DocIndexer classes supplied with BlackLab (see the nl.inl.blacklab.indexers package) for reference, but we'll highlight the most important features here.

### Indexing word-annotated XML

If you have an XML format in which each word has its own XML tag, containing any annotations, you should derive your DocIndexer class from DocIndexerXmlHandlers. This class allows you to add 'hooks' for handling different XML elements.

(for a practical example of the following, see [Adding an input format](add-input-format.html))

The constructor of your indexing class should call the superclass constructor, declare any annotations (e.g. lemma, pos) you're going to index and finally add hooks (called handlers) for each XML element you want to do something with.

Declaring annotations (called "properties" in BlackLab) is done using the DocIndexerXmlHandlers.addProperty(String). Store the result in a final variable so you can access it from your custom handlers. Note that the "main property" (usually called "word") and the "punct" property (whitespace and punctuation between words) have already been created by DocIndexerXmlHandlers; retrieve them using the getMainProperty() and getPropPunct() methods.

Adding a handler is done using the DocIndexerXmlHandlers.addHandler(String, ElementHandler) method. The first parameter is an xpath-like expression that indicates the element to handle. The expression looks like xpath but is very limited: only element names separated by slashes; it may start with either a single (absolute path) or a double (relative path) slash. DocIndexerXmlHandlers defines several default ElementHandlers: ElementHandler (does nothing but keep track of whether we're inside this element), DocumentElementHandler (creates and adds document to the index), several metadata handlers that deal with different types of metadata elements (assuming the document contains its own metadata - see below for external metadata), InlineTagHandler (adds inline tags from the content to the index, such as <p>, <s> (sentence), <b>), several word handlers (indexes words and whitespace/punctuation between words). You can also easily create or derive your own, usually as an anonymous inner class that overrides the startElement() and endElement() methods.

You need to add a handler for your document element (signifying the start and end of your logical documents; probably just use DocumentElementHandler), your word element (signifying a word to index; probably derive from WordHandlerBase), and any inline tags you wish to index (probably just use InlineTagHandler). Optionally, you may want to add a simple ElementHandler for your "body" tag, if you wish to restrict what part of the document is actually indexed; in this case, you should expand your word and inline tag handlers to check that you're inside this body element before processing the matched element. 

A word handler derived from DefaultWordHandler might retrieve lemma and part of speech from the attributes of the start tag and add them to the properties you declared at the top of your DocIndexer-constructor. DefaultWordHandler takes care of adding values to the standard properties word and punct. For this, DefaultWordHandler assumes the word is simply the word element's text content. DefaultWordHandler also stores the character positions before and after the word (which are needed if you want to highlight in the original XML).

You should probably call consumeCharacterContent(), which clears the buffer of captured text content in the document, at the start of a document (or at the start of the body element, if you handle that separately). This prevents the first punct value containing already captured text content you don't want. Similarly, before storing the document, you should add one last punct value (using consumeCharacterContent() to get the value to store), so the last bit of whitespace/punctuation isn't skipped. BlackLab assumes that there's always an extra "dummy" token containing only the last bit of whitespace/punctuation.

### Word gaps and adding property values at a specific position

...

### Indexing non-XML file types

If your input files are not XML or are not tokenized and annotated per word, you have two options: convert them into a tokenized, per-word annotated format, or index them directly.

Indexing them directly is not covered in depth here, but involves deriving from DocIndexerAbstract or implementing the DocIndexer interface yourself. If you need help with this, please [contact us](mailto:jan.niestadt@inl.nl).

## Metadata 

### In-document metadata

Some documents contain metadata within the document. You usually want to index these as fields with your document, so you can filter on them later. You do this by adding a handler for the appropriate XML element.

There's a few helper classes for in-document metadata handling. MetadataElementHandler assumes the matched element name is the name of your metadata field and the character content is the value. MetadataAttributesHandler stores all the attributes from the matched element as metadata fields. MetadataNameValueAttributeHandler assumes the matched element has a name attribute and a value attribute (the attribute names can be specified in the constructor) and stores those as metadata fields. You can of course easily add your own handler classes to this if they don't suit your particular style of metadata.

### Metadata from an external source

Sometimes, documents link to external metadata sources, usually using an ID.

The MetadataFetcher is instantiated by DocIndexerXmlHandlers.getMetadataFetchr(), based on the metadataFetcherClass indexer property (see Indexer Properties above). This class is instantiated with the DocIndexer as a parameter, and the addMetadata() method is called just before adding a document to the index. Your particular MetadataFetcher can inspect the document to find the appropriate ID, fetch the metadata (e.g. from a file, database or webservice) and add it to the document using the DocIndexerXmlHandlers.addMetadataField() method.

Also see the two MetadataFetcher examples in nl.inl.blacklab.indexers.




