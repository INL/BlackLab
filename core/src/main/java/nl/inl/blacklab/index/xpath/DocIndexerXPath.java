package nl.inl.blacklab.index.xpath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.util.BytesRef;

import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.VTDException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocIndexerFactory;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.xpath.InlineObject.InlineObjectType;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.ExUtil;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileProcessor.FileHandler;
import nl.inl.util.StringUtil;

/**
 * An indexer configured using full XPath 1.0 expressions.
 */
public class DocIndexerXPath extends DocIndexer {

    private static final boolean TRACE = true;

    /** Our input document */
    private byte[] inputDocument;

    /** What was the byte offset of the last char position we determined? */
    private int lastCharPositionByteOffset;

    /** What was the last character position we determined? */
    private int lastCharPosition;

    /** Byte position at which the document started */
    private int documentByteOffset;

    /** Length of the document in bytes */
    private int documentLengthBytes;

    /** VTD parser (generator?) */
    private VTDGen vg;

    /** VTD navigator */
    private VTDNav nav;

    /** Our input format */
    private ConfigInputFormat config;

    /** Complex fields we're indexing. */
    private Map<String, ComplexField> complexFields = new HashMap<>();

    /** The config for the annotated (complex) field we're currently processing. */
	private ConfigAnnotatedField currentAnnotatedField;

	/** The indexing object for the complex field we're currently processing. */
	private ComplexField currentComplexField;

	/** The tag property for the complex field we're currently processing. */
	private ComplexFieldProperty propStartTag;

	/** The main property for the complex field we're currently processing. */
	private ComplexFieldProperty propMain;

	/** The main property for the complex field we're currently processing. */
	private ComplexFieldProperty propPunct;

	/** Unique strings we store, so we avoid storing many copies of the same string (e.g. punctuation). */
    Map<String, String> uniqueStrings = new HashMap<>();

    boolean inited = false;

    /** If true, we're indexing into an existing Lucene document. Don't overwrite it with a new one. */
    private boolean indexingIntoExistingLuceneDoc = false;

    /** If no punctuation expression is defined, add a space between each word by default. */
    private boolean addDefaultPunctuation = true;

    /** Store documents? Can be set to false in ConfigInputFormat to if no content store is desired,
     *  or via indexSpecificDocument to prevent storing linked documents.
     */
    private boolean storeDocuments = true;

    /** The content store we should store this document in. Also stored the content store id
     *  in the field with this name with "Cid" appended, e.g. "metadataCid" if useContentStore
     *  equals "metadata". This is used for storing linked document, if desired.
     *  Normally null, meaning document should be stored in the default field and content
     *  store (usually "contents", with the id in field "contents#cid").
     */
    private String contentStoreName = null;

    public DocIndexerXPath() {
    }

    @SuppressWarnings("deprecation")
    protected void init() {
        if (inited)
            return;
        inited = true;
        storeDocuments = config.isStore();
        for (ConfigAnnotatedField af: config.getAnnotatedFields().values()) {

	        // Define the properties that make up our complex field
        	List<ConfigAnnotation> annotations = new ArrayList<>(af.getAnnotations().values());
        	if (annotations.size() == 0)
        		throw new RuntimeException("No annotations defined for field " + af.getFieldName());
        	ConfigAnnotation mainAnnotation = annotations.get(0);
	        ComplexField complexField = new ComplexField(af.getFieldName(), mainAnnotation.getName(), getSensitivitySetting(mainAnnotation), false);
	        complexFields.put(af.getFieldName(), complexField);
            ComplexFieldProperty propStartTag = complexField.addProperty(ComplexFieldUtil.START_TAG_PROP_NAME, getSensitivitySetting(ComplexFieldUtil.START_TAG_PROP_NAME), true);
	        propStartTag.setForwardIndex(false);

	        // Create properties for the other annotations
	        for (int i = 1; i < annotations.size(); i++) {
	        	ConfigAnnotation annot = annotations.get(i);
	        	complexField.addProperty(annot.getName(), getSensitivitySetting(annot), false);
	        }
	        for (ConfigStandoffAnnotations standoff: af.getStandoffAnnotations()) {
	            for (ConfigAnnotation annot: standoff.getAnnotations()) {
	                complexField.addProperty(annot.getName(), getSensitivitySetting(annot), false);
	            }
	        }
	        if (!complexField.hasProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME)) {
	            // Hasn't been created yet. Create it now.
	            complexField.addProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME, getSensitivitySetting(ComplexFieldUtil.PUNCTUATION_PROP_NAME), false);
	        }

            IndexStructure indexStructure;
            if (indexer != null) {
                indexStructure = indexer.getSearcher().getIndexStructure();
                indexStructure.registerComplexField(complexField.getName(), complexField.getMainProperty().getName());

		        // If the indexmetadata file specified a list of properties that shouldn't get a forward
		        // index, make the new complex field aware of this.
		        Set<String> noForwardIndexProps = indexStructure.getComplexFieldDesc(complexField.getName()).getNoForwardIndexProps();
		        complexField.setNoForwardIndexProps(noForwardIndexProps);
	        }
        }
    }

    @SuppressWarnings("deprecation")
    private SensitivitySetting getSensitivitySetting(ConfigAnnotation mainAnnotation) {
        if (mainAnnotation.getSensitivity() == SensitivitySetting.DEFAULT) {
            return getSensitivitySetting(mainAnnotation.getName());
        }
        return mainAnnotation.getSensitivity();
    }

    public void setConfigInputFormat(ConfigInputFormat config) {
        this.config = config;
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) throws FileNotFoundException {
        try {
            setDocument(FileUtils.readFileToByteArray(file), defaultCharset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        this.inputDocument = contents;
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        try {
            setDocument(IOUtils.toByteArray(is), defaultCharset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            setDocument(IOUtils.toString(reader).getBytes(Indexer.DEFAULT_INPUT_ENCODING), Indexer.DEFAULT_INPUT_ENCODING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create AutoPilot and declare namespaces on it.
     * @return the AutoPilot
     */
    AutoPilot createAutoPilot() {
        AutoPilot ap = new AutoPilot(nav);
        if (config.isNamespaceAware()) {
            ap.declareXPathNameSpace("xml", "http://www.w3.org/XML/1998/namespace"); // builtin
            for (Entry<String, String> e: config.getNamespaces().entrySet()) {
                ap.declareXPathNameSpace(e.getKey(), e.getValue());
            }
        }
        return ap;
    }

    @Override
    public void index() throws Exception {
        init();

        // Parse use VTD-XML
        vg = new VTDGen();
        vg.setDoc(inputDocument);
        vg.parse(config.isNamespaceAware());

        nav = vg.getNav();

        // Find all documents
        AutoPilot documents = createAutoPilot();
        documents.selectXPath(config.getDocumentPath());
        while(documents.evalXPath() != -1) {
            indexDocument();
        }

    }

    /**
     * Index document from the current node.
     *
     * @throws VTDException on XPath parse (or other) error
     */
    protected void indexDocument() throws VTDException {

        Map<String, Integer> tokenPositionsMap = new HashMap<>();

        startDocument();

        // For each configured annotated field...
        for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields().values()) {

        	// Determine some useful stuff about the field we're processing
            // and store in instance variables so our methods can access them
        	currentAnnotatedField = annotatedField;
        	currentComplexField = complexFields.get(currentAnnotatedField.getFieldName());
        	propStartTag = currentComplexField.getTagProperty();
        	propMain = currentComplexField.getMainProperty();
        	propPunct = currentComplexField.getPunctProperty();

            AutoPilot words = createAutoPilot();
            words.selectXPath(annotatedField.getWordsPath());
            String tokenPositionIdPath = annotatedField.getTokenPositionIdPath();

            // For each body element...
            // (there's usually only one, but there's no reason to limit it)
            nav.push();
            AutoPilot bodies = createAutoPilot();
            bodies.selectXPath(annotatedField.getContainerPath());
            while (bodies.evalXPath() != -1) {

                // First we find all inline elements (stuff like s, p, b, etc.) and store
                // the locations of their start and end tags in a sorted list.
                // This way, we can keep track of between which words these tags occur.
                // For end tags, we will update the payload of the start tag when we encounter it,
                // just like we do in our SAX parsers.
                AutoPilot apTags = createAutoPilot();
                AutoPilot apEvalToString = createAutoPilot();
                apEvalToString.selectXPath(".");
                List<InlineObject> tagsAndPunct = new ArrayList<>();
                for (ConfigInlineTag inlineTag: annotatedField.getInlineTags()) {
                    nav.push();
                    apTags.selectXPath(inlineTag.getTagPath());
                    while (apTags.evalXPath() != -1) {
                        collectInlineTag(tagsAndPunct);
                    }
                    nav.pop();
                }
                addDefaultPunctuation = true;
                if (annotatedField.getPunctPath() != null) {
                    // We have punctuation occurring between word tags (as opposed to
                    // punctuation that is tagged as a word itself). Collect this punctuation.
                    addDefaultPunctuation = false;
                	nav.push();
                    apTags.selectXPath(annotatedField.getPunctPath());
                    while (apTags.evalXPath() != -1) {
                        apEvalToString.resetXPath();
                        String punct = apEvalToString.evalXPathToString();
                        collectPunct(tagsAndPunct, punct);
                    }
                    nav.pop();
                }
                Collections.sort(tagsAndPunct);
                Iterator<InlineObject> inlineObjectsIt = tagsAndPunct.iterator();
                InlineObject nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;

                // Now, find all words, keeping track of what inline objects occur in between.
                nav.push();
                words.resetXPath();
                while (words.evalXPath() != -1) {

                    // Capture tokenPositionId for this token position?
                    AutoPilot apAnnot = createAutoPilot();
                    if (tokenPositionIdPath != null) {
                        apAnnot.selectXPath(tokenPositionIdPath);
                        String tokenPositionId = apAnnot.evalXPathToString();
                        tokenPositionsMap.put(tokenPositionId, propMain.lastValuePosition() + 1);
                    }

                    // Does an inline object occur before this word?
                    long wordFragment = nav.getContentFragment();
                    int wordOffset = (int)wordFragment;
                    while (wordOffset >= nextInlineObject.getOffset()) {
                        // Yes. Handle it.
                    	if (nextInlineObject.type() == InlineObjectType.PUNCTUATION)
                    		punctuation(nextInlineObject);
                    	else
                    		inlineTag(nextInlineObject);
                        nextInlineObject = inlineObjectsIt.next();
                    }

                    beginWord();

                    // For each configured annotation...
                    for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                        processAnnotation(annotation, apAnnot, null);
                    }

                    endWord();
                }
                nav.pop();

                // Handle any inline objects after the last word
                while (nextInlineObject != null) {
                	if (nextInlineObject.type() == InlineObjectType.PUNCTUATION)
                		punctuation(nextInlineObject);
                	else
                		inlineTag(nextInlineObject);
                    nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
                }

            }
            nav.pop();

            // For each configured standoff annotation...
            for (ConfigStandoffAnnotations standoff: annotatedField.getStandoffAnnotations()) {
                // For each instance of this standoff annotation..
                nav.push();
                AutoPilot apStandoff = createAutoPilot();
                apStandoff.selectXPath(standoff.getPath());
                AutoPilot apTokenPos = createAutoPilot();
                apTokenPos.selectXPath(standoff.getRefTokenPositionIdPath());
                AutoPilot apEvalToString = createAutoPilot();
                apEvalToString.selectXPath(".");
                AutoPilot apAnnot = createAutoPilot();
                while (apStandoff.evalXPath() != -1) {

                    // Determine what token positions to index these values at
                    nav.push();
                    List<Integer> tokenPositions = new ArrayList<>();
                    apTokenPos.resetXPath();
                    while (apTokenPos.evalXPath() != -1) {
                        apEvalToString.resetXPath();
                        String tokenPositionId = apEvalToString.evalXPathToString();
                        Integer integer = tokenPositionsMap.get(tokenPositionId);
                        if (integer == null)
                            warn("Unresolved reference to token position: '" + tokenPositionId + "'");
                        else
                            tokenPositions.add(integer);
                    }
                    nav.pop();

                    for (ConfigAnnotation annotation: standoff.getAnnotations()) {
                        processAnnotation(annotation, apAnnot, tokenPositions);
                    }
                }
                nav.pop();
            }
        }

        // For each configured metadata block..
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {

            // For each instance of this metadata block...
            nav.push();
            AutoPilot apMetadataBlock = createAutoPilot();
            apMetadataBlock.selectXPath(b.getContainerPath());
            while (apMetadataBlock.evalXPath() != -1) {

                // For each configured metadata field...
                AutoPilot apMetadata = createAutoPilot();
                AutoPilot apFieldName = createAutoPilot();
                AutoPilot apMetaForEach = createAutoPilot();
                for (ConfigMetadataField f: b.getFields()) {

                    // Metadata field configs without a valuePath are just for
                    // adding information to indexmetadata about fields captured
                    // in forEach's
                    if (f.getValuePath() == null || f.getValuePath().isEmpty())
                        continue;

                    // Capture whatever this configured metadata field points to
                    if (f.isForEach()) {
                        // "forEach" metadata specification
                        // (allows us to capture many metadata fields with 3 XPath expressions)
                        nav.push();
                        apMetaForEach.selectXPath(f.getForEachPath());
                        apFieldName.selectXPath(f.getFieldName());
                        apMetadata.selectXPath(f.getValuePath());
                        while (apMetaForEach.evalXPath() != -1) {
                            // Find the fieldName and value for this forEach match
                            apFieldName.resetXPath();
                            String fieldName = apFieldName.evalXPathToString();
                            apMetadata.resetXPath();
                            String metadataValue = apMetadata.evalXPathToString();
                            metadata(fieldName, metadataValue);
                        }
                        nav.pop();
                    } else {
                        // Regular metadata field; just the fieldName and an XPath expression for the value
                        apMetadata.selectXPath(f.getValuePath());
                        String metadataValue = apMetadata.evalXPathToString();
                        metadata(f.getFieldName(), metadataValue);
                    }
                }

            }
            nav.pop();
        }

        // For each linked document...
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            // Resolve linkPaths to get the information needed to fetch the document
            List<String> results = new ArrayList<>();
            AutoPilot apLinkPath = createAutoPilot();
            for (String linkPath: ld.getLinkPaths()) {
                apLinkPath.selectXPath(linkPath);
                String result = apLinkPath.evalXPathToString();
                if (result == null || result.isEmpty()) {
                    switch(ld.getIfLinkPathMissing()) {
                    case IGNORE:
                        break;
                    case WARN:
                        indexer.getListener().warning("Link path " + linkPath + " not found in document " + documentName);
                        break;
                    case FAIL:
                        throw new RuntimeException("Link path " + linkPath + " not found in document " + documentName);
                    }
                }
                results.add(result);
            }

            // Substitute link path results in inputFile, pathInsideArchive and documentPath
            String inputFile = replaceDollarRefs(ld.getInputFile(), results);
            String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
            String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);

            try {
                // Fetch and index the linked document
                indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld);
            } catch (Exception e) {
                switch(ld.getIfLinkPathMissing()) {
                case IGNORE:
                case WARN:
                    indexer.getListener().warning("Could not find linked document for " + documentName + ": " + e.getMessage());
                    break;
                case FAIL:
                    throw new RuntimeException("Could not find linked document for " + documentName, e);
                }
            }
        }

        endDocument();
    }

    /**
     * Process an annotation at the current position.
     *
     * @param annotation annotation to process
     * @param apAnnot autopilot object to use (so we don't keep creating new ones)
     * @param indexAtPositions if null: index at the current position; otherwise, index at all these positions
     * @throws VTDException on XPath error
     */
    protected void processAnnotation(ConfigAnnotation annotation, AutoPilot apAnnot, List<Integer> indexAtPositions) throws VTDException {
        String basePath = annotation.getBasePath();
        if (basePath != null) {
            // Basepath given. Navigate to the (first) matching element and evaluate the other XPaths from there.
            nav.push();
            apAnnot.selectXPath(basePath);
            apAnnot.evalXPath();
        }

        String valuePath = annotation.getValuePath();

        // See if we want to capture any values and substitute them into the XPath
        int i = 1;
        for (String captureValuePath: annotation.getCaptureValuePaths()) {
            apAnnot.selectXPath(captureValuePath);
            String value = apAnnot.evalXPathToString();
            valuePath = valuePath.replace("$" + i, value);
            i++;
        }

        apAnnot.selectXPath(valuePath);
        String annotValue = apAnnot.evalXPathToString();
        annotation(annotation.getName(), annotValue, 1, indexAtPositions);

        // For each configured subannotation...
        AutoPilot apValue = createAutoPilot();
        AutoPilot apName = createAutoPilot();
        AutoPilot apForEach = createAutoPilot();
        for (ConfigAnnotation subAnnot: annotation.getSubAnnotations()) {
            // Capture this subannotation value
            if (subAnnot.isForEach()) {
                // "forEach" subannotation specification
                // (allows us to capture multiple subannotations with 3 XPath expressions)
                nav.push();
                apForEach.selectXPath(subAnnot.getForEachPath());
                apName.selectXPath(subAnnot.getName());
                apValue.selectXPath(subAnnot.getValuePath());
                while (apForEach.evalXPath() != -1) {
                    // Find the name and value for this forEach match
                    apName.resetXPath();
                    String name = apName.evalXPathToString();
                    apValue.resetXPath();
                    String value = apValue.evalXPathToString();
                    subAnnotation(annotation.getName(), name, value, indexAtPositions);
                }
                nav.pop();
            } else {
                // Regular metadata field; just the fieldName and an XPath expression for the value
                apValue.selectXPath(subAnnot.getValuePath());
                String value = apValue.evalXPathToString();
                subAnnotation(annotation.getName(), subAnnot.getName(), value, indexAtPositions);
            }
        }

        if (basePath != null) {
            // We pushed when we navigated to the base element; pop now.
            nav.pop();
        }
    }

    /**
     * Index a specific document into an existing Lucene document.
     *
     * Only supported by DocIndexerXPath.
     *
     * @param documentXPath XPath to find the document to index
     * @param indexToLuceneDoc Lucene document to index to
     * @param contentStoreName content store to store the document in, or null if it should not be stored
     */
    public void indexSpecificDocument(String documentXPath, Document indexToLuceneDoc, String contentStoreName) {
        currentLuceneDoc = indexToLuceneDoc;
        indexingIntoExistingLuceneDoc = true;
        if (contentStoreName != null)
            this.contentStoreName = contentStoreName;
        else
            storeDocuments = false;

        // Resolve document XPath
        init();

        try {
            // Parse use VTD-XML
            vg = new VTDGen();
            vg.setDoc(inputDocument);
            vg.parse(config.isNamespaceAware());

            nav = vg.getNav();

            AutoPilot documents = createAutoPilot();
            boolean docDone = false;
            if (documentXPath != null) {
                // Find our specific document
                documents.selectXPath(documentXPath);
                while(documents.evalXPath() != -1) {
                    if (docDone)
                        throw new RuntimeException("Document link " + documentXPath + " matched multiple documents in " + documentName);
                    indexDocument();
                    docDone = true;
                }
            } else {
                // Process whole file; must be 1 document
                documents.selectXPath(config.getDocumentPath());
                while(documents.evalXPath() != -1) {
                    if (docDone)
                        throw new RuntimeException("Linked file contains multiple documents (and no document path given) in " + documentName);
                    indexDocument();
                    docDone = true;
                }
            }
        } catch (Exception e1) {
            throw ExUtil.wrapRuntimeException(e1);
        }

        // Reset to some reasonable defaults
        currentLuceneDoc = null;
        indexingIntoExistingLuceneDoc = false;
        this.contentStoreName = null;
        storeDocuments = true;
    }

    /**
     * Index a linked document.
     *
     * @param inputFile where the linked document can be found (file or http(s) reference)
     * @param pathInsideArchive if input file is an archive: the path to the file we need inside the archive
     * @param documentPath XPath to the specific linked document we need
     * @param inputFormat input format of the linked document
     * @param b
     * @throws IOException on error
     */
	private void indexLinkedDocument(String inputFile, String pathInsideArchive, String documentPath, ConfigLinkedDocument ld) throws IOException {
	    String inputFormat = ld.getInputFormat();
	    boolean store = ld.isStore();

	    // Get the DocIndexerFactory
	    DocIndexerFactory docIndexerFactory = DocumentFormats.getIndexerFactory(inputFormat);

        // Fetch the input file (either by downloading it to a temporary location, or opening it from disk)
        try (InputStream is = resolveFileReference(inputFile)) {

    	    // Get the data
    	    byte [] data;
    	    String completePath = inputFile;
    	    if (inputFile.endsWith(".zip") || inputFile.endsWith(".tar") || inputFile.endsWith(".tar.gz") || inputFile.endsWith(".tgz")) {
    	        // It's an archive. Unpack the right file from it.
                completePath += "/" + pathInsideArchive;
    	        data = fetchFileFromArchive(inputFile, is, pathInsideArchive);
    	    } else {
    	        // Regular file.
    	        try {
                    data = IOUtils.toByteArray(is);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
    	    }
    	    if (data == null) {
    	        throw new RuntimeException("Error reading linked document");
    	    }

            // Index the data
    	    DocIndexer docIndexer = docIndexerFactory.get(indexer, completePath, data, Indexer.DEFAULT_INPUT_ENCODING);
    	    if (docIndexer instanceof DocIndexerXPath) {
    	        ((DocIndexerXPath)docIndexer).indexSpecificDocument(documentPath, currentLuceneDoc, store ? ld.getName() : null);
    	    } else {
    	        throw new RuntimeException("Linked document indexer must be DocIndexerXPath-based, but is " + docIndexer.getClass().getName());
    	    }
        }

    }

    private static byte[] fetchFileFromArchive(String inputFile, InputStream is, final String pathInsideArchive) {
        FileProcessor proc = new FileProcessor(false, true);
        FetchFileHandler fileHandler = new FetchFileHandler(pathInsideArchive);
        proc.setFileHandler(fileHandler);
        proc.processInputStream(inputFile, is);
        return fileHandler.bytes;
    }

    private InputStream resolveFileReference(String inputFile) throws IOException {
        if (inputFile.startsWith("http://") || inputFile.startsWith("https://")) {
            return new URL(inputFile).openStream();
        }
        if (indexer == null)
            return new FileInputStream(new File(inputFile));
        File f = indexer.getLinkedFile(inputFile);
        if (f == null)
            throw new FileNotFoundException("References file not found: " + f);
        if (!f.canRead())
            throw new IOException("Cannot read referenced file " + f);
        return new FileInputStream(f);
    }

    private static String replaceDollarRefs(String pattern, List<String> replacements) {
	    if (pattern != null) {
    	    int i = 1;
            for (String replacement: replacements) {
                pattern = pattern.replace("$" + i, replacement);
                i++;
            }
	    }
        return pattern;
    }

    /**
     * Add open and close InlineObject objects for the current element to the list.
     *
     * @param inlineObject list to add the new open/close tag objects to
     * @throws NavException
     */
	private void collectInlineTag(List<InlineObject> inlineObject) throws NavException {
		// Get the element and content fragments
		// (element fragment = from start of start tag to end of end tag;
		//  content fragment = from end of start tag to start of end tag)
		long contentFragment = nav.getContentFragment();
		int contentOffset = (int)contentFragment;
		int contentLength = (int)(contentFragment >> 32);
		int contentEnd = contentOffset + contentLength;
		long elementFragment = nav.getElementFragment();
		int elementOffset = (int)elementFragment;
		int elementLength = (int)(elementFragment >> 32);
		int elementEnd = elementOffset + elementLength;

		// Calculate start/end tag offset and length
		int startTagOffset = elementOffset;
		int startTagLength = contentOffset - elementOffset;
		int endTagOffset = contentEnd;
		int endTagLength = elementEnd - contentEnd;

		// Find element name
		int currentIndex = nav.getCurrentIndex();
		String elementName = dedupe(nav.toString(currentIndex));

		// Add the inline tags to the list
		InlineObject openTag = new InlineObject(elementName, startTagOffset, startTagLength, InlineObjectType.OPEN_TAG, getAttributes());
		InlineObject closeTag = new InlineObject(elementName, endTagOffset, endTagLength, InlineObjectType.CLOSE_TAG, null);
		openTag.setMatchingTag(closeTag);
		closeTag.setMatchingTag(openTag);
		inlineObject.add(openTag);
		inlineObject.add(closeTag);
	}

    /**
     * Add InlineObject for a punctuation text node.
     *
     * @param inlineObjects list to add the punct object to
     * @throws NavException
     */
	private void collectPunct(List<InlineObject> inlineObjects, String text) throws NavException {
		int i = nav.getCurrentIndex();
		int offset = nav.getTokenOffset(i);
		int length = nav.getTokenLength(i);

		// Make sure we only keep 1 copy of identical punct texts in memory
		text = dedupe(StringUtil.normalizeWhitespace(text));

		// Add the punct to the list
		inlineObjects.add(new InlineObject(text, offset, offset + length, InlineObjectType.PUNCTUATION, null));
	}

	/** If we've already seen this string, return the original copy.
	 *
	 * This prevents us from storing many copies of the same string.
	 */
    private String dedupe(String possibleDupe) {
        String original = uniqueStrings.get(possibleDupe);
        if (original != null)
            return original;
        uniqueStrings.put(possibleDupe, possibleDupe);
        return possibleDupe;
    }

    /**
     * Gets attribute map for current element
     * @return
     */
    private Map<String, String> getAttributes() {
		nav.push();
		AutoPilot apAttr = new AutoPilot(nav);
		apAttr.selectAttr("*");
		int i = -1;
		Map<String, String> attr = new HashMap<>();
		try {
			while ((i = apAttr.iterateAttr()) != -1) {
				String name = nav.toString(i);
				String value = nav.toString(i + 1);
				attr.put(name, value);
			}
		} catch (NavException e) {
			throw new RuntimeException(e);
		}
		nav.pop();
		return attr;
	}

    void trace(String msg) {
        if (TRACE)
            System.out.print(msg);
    }

    void traceln(String msg) {
        if (TRACE)
            System.out.println(msg);
    }

    // HANDLERS

    protected void startDocument() {
    	traceln("START DOCUMENT");

        try {
            long fragment = nav.getElementFragment();
            documentByteOffset = (int)fragment;
            documentLengthBytes = (int)(fragment >> 32);
        } catch (NavException e) {
            throw new RuntimeException(e);
        }

        lastCharPosition = 0;
        lastCharPositionByteOffset = documentByteOffset;

        if (!indexingIntoExistingLuceneDoc) {
            currentLuceneDoc = new Document();
            if (indexer != null)
            	currentLuceneDoc.add(new Field("fromInputFile", documentName, indexer.getMetadataFieldType(false)));

            // DEPRECATED for these types of indexer, but still supported for now
            addMetadataFieldsFromParameters();
        }
        if (indexer != null)
            indexer.getListener().documentStarted(documentName);
    }

	protected void endDocument() {
		traceln("END DOCUMENT");

    	// The "main complex field" is the field that stores the document content id for now.
    	// (We will change this eventually so the document content id is not stored with a complex field
    	// but as a metadata field instead.)
    	// The main complex field is a field named "contents" or, if that does not exist, the first
    	// complex field
    	String mainComplexField = null;

        for (ComplexField complexField: complexFields.values()) {
        	if (mainComplexField == null)
        		mainComplexField = complexField.getName();
        	else if (complexField.getName().equals("contents"))
        		mainComplexField = complexField.getName();

            ComplexFieldProperty propMain = complexField.getMainProperty();

            // Make sure all the properties have an equal number of values.
            // See what property has the highest position
            // (in practice, only starttags and endtags should be able to have
            // a position one higher than the rest)
            int lastValuePos = 0;
            for (ComplexFieldProperty prop: complexField.getProperties()) {
                if (prop.lastValuePosition() > lastValuePos)
                    lastValuePos = prop.lastValuePosition();
            }

            // Make sure we always have one more token than the number of
            // words, so there's room for any tags after the last word, and we
            // know we should always skip the last token when matching.
            if (propMain.lastValuePosition() == lastValuePos)
                lastValuePos++;

            // Add empty values to all lagging properties
            for (ComplexFieldProperty prop: complexField.getProperties()) {
                while (prop.lastValuePosition() < lastValuePos) {
                    prop.addValue("");
                    if (prop.hasPayload())
                        prop.addPayload(null);
                    if (prop == propMain) {
                        complexField.addStartChar(getCharacterPosition());
                        complexField.addEndChar(getCharacterPosition());
                    }
                }
            }
            // Store the different properties of the complex field that
            // were gathered in lists while parsing.
//            System.out.println("Adding to lucene doc: " + complexField.getName());
//            System.out.println("Values: " + complexField.getMainProperty().getValues());
            complexField.addToLuceneDoc(currentLuceneDoc);

            // Add all properties to forward index
            for (ComplexFieldProperty prop: complexField.getProperties()) {
                if (!prop.hasForwardIndex())
                    continue;

                // Add property (case-sensitive tokens) to forward index and add
                // id to Lucene doc
                String propName = prop.getName();
                String fieldName = ComplexFieldUtil.propertyField(
                        complexField.getName(), propName);
                if (indexer != null) {
	                int fiid = indexer.addToForwardIndex(fieldName, prop);
	                currentLuceneDoc.add(new IntField(ComplexFieldUtil
	                        .forwardIndexIdField(fieldName), fiid, Store.YES));
                }
            }

        }

        if (storeDocuments) {
            // Finish storing the document in the document store,
            // retrieve the content id, and store that in Lucene.
            // (Note that we do this after adding the dummy token, so the character
            // positions for the dummy token still make (some) sense)
            String document = new String(inputDocument, documentByteOffset, documentLengthBytes);
            String contentStoreName, contentIdFieldName;
            if (this.contentStoreName == null) {
                if (mainComplexField == null) {
                    contentStoreName = "metadata";
                    contentIdFieldName = "metadataCid";
                } else {
                    contentStoreName = mainComplexField;
                    contentIdFieldName = ComplexFieldUtil.contentIdField(mainComplexField);
                }
            } else {
                contentStoreName = this.contentStoreName;
                contentIdFieldName = contentStoreName + "Cid";
            }
            int contentId = -1;
            if (indexer != null) {
                ContentStore contentStore = indexer.getContentStore(contentStoreName);
                contentId = contentStore.store(document);
            }
            currentLuceneDoc.add(new IntField(contentIdFieldName, contentId, Store.YES));
        }

        if (indexer != null) {
	        // See what metadatafields are missing or empty and add unknown value
	        // if desired.
	        IndexStructure struct = indexer.getSearcher().getIndexStructure();
	        for (String fieldName: struct.getMetadataFields()) {
	            MetadataFieldDesc fd = struct.getMetadataFieldDesc(fieldName);
	            boolean missing = false, empty = false;
	            String currentValue = currentLuceneDoc.get(fieldName);
	            if (currentValue == null)
	                missing = true;
	            else if (currentValue.length() == 0)
	                empty = true;
	            UnknownCondition cond = fd.getUnknownCondition();
	            boolean useUnknownValue = false;
	            switch (cond) {
	            case EMPTY:
	                useUnknownValue = empty;
	                break;
	            case MISSING:
	                useUnknownValue = missing;
	                break;
	            case MISSING_OR_EMPTY:
	                useUnknownValue = missing | empty;
	                break;
	            case NEVER:
	                useUnknownValue = false;
	                break;
	            }
	            if (useUnknownValue)
	                addMetadataField(optTranslateFieldName(fieldName), fd.getUnknownValue());
	        }
        }

        try {
            // Add Lucene doc to indexer
        	if (indexer != null)
            	indexer.add(currentLuceneDoc);
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }

        for (ComplexField complexField: complexFields.values()) {
            // Reset complex field for next document
            complexField.clear();
        }

        // Report progress
        if (indexer != null) {
            indexer.getListener().charsDone(documentByteOffset + documentLengthBytes); // bytes, not chars; oh well
        	reportTokensProcessed(wordsDone);
        }
        wordsDone = 0;
        if (indexer != null)
        	indexer.getListener().documentDone(documentName);

        currentLuceneDoc = null;

        // Stop if required
        if (indexer != null) {
        	if (!indexer.continueIndexing())
        		throw new MaxDocsReachedException();
        }

        uniqueStrings.clear();
    }

	private String optTranslateFieldName(String from) {
        String to = config.getIndexFieldAs().get(from);
        return to == null ? from : to;
    }

    /** File handler that reads a single file into a byte array. */
    private static final class FetchFileHandler implements FileHandler {

        private final String pathToFile;

        byte[] bytes;

        FetchFileHandler(String pathInsideArchive) {
            this.pathToFile = pathInsideArchive;
        }

        @Override
        public void stream(String path, InputStream f) {
            if (path.equals(pathToFile)) {
                try {
                    bytes = IOUtils.toByteArray(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void file(String path, File f) {
            throw new UnsupportedOperationException();
        }
    }

    /* Position of start tags and their index in the property arrays, so we can add payload when we find the end tags */
    class OpenTagInfo {
        public int position;
        public int index;

        public OpenTagInfo(int position, int index) {
            this.position = position;
            this.index = index;
        }
    }

    List<OpenTagInfo> openTags = new ArrayList<>();

	protected void inlineTag(InlineObject tag) {
        if (tag.type() == InlineObjectType.OPEN_TAG) {
    		trace("<" + tag.getText() + ">");

            int lastStartTagPos = propStartTag.lastValuePosition();
            int currentPos = propMain.lastValuePosition() + 1;
            int posIncrement = currentPos - lastStartTagPos;
            propStartTag.addValue(tag.getText(), posIncrement);
            propStartTag.addPayload(null);
            int startTagIndex = propStartTag.getLastValueIndex();
            openTags.add(new OpenTagInfo(currentPos, startTagIndex));

            Map<String, String> attributes = tag.getAttributes();
            for (Entry<String, String> e: attributes.entrySet()) {
                // Index element attribute values
                String name = e.getKey();
                String value = e.getValue();
                propStartTag.addValue("@" + name.toLowerCase() + "__" + value.toLowerCase(), 0);
                propStartTag.addPayload(null);
            }

        } else {
    		traceln("</" + tag.getText() + ">");

            int currentPos = propMain.lastValuePosition() + 1;

            // Add payload to start tag property indicating end position
            OpenTagInfo openTag = openTags.remove(openTags.size() - 1);
            byte[] payload = ByteBuffer.allocate(4).putInt(currentPos).array();
            propStartTag.setPayloadAtIndex(openTag.index, new BytesRef(payload));
        }
    }

	StringBuilder punctuation = new StringBuilder();

    private void punctuation(InlineObject punct) {
    	trace(punct.getText());

    	punctuation.append(punct.getText());
	}

    protected void beginWord() {
        int pos = getCharacterPosition();
        currentComplexField.addStartChar(pos);
        trace("@" + pos);
    }

    protected void endWord() {
        String punct = punctuation.length() == 0 && addDefaultPunctuation ? " " : punctuation.toString();
        propPunct.addValue(punct);
    	currentComplexField.addEndChar(getCharacterPosition());

        wordsDone++;
        if (punctuation.length() > 10000)
            punctuation = new StringBuilder(); // let's not hold on to this much memory
        else
            punctuation.delete(0, punctuation.length());
    }

    /**
     * Index an annotation.
     *
     * Can be used to add annotation(s) at the current position (indexAtPositions == null),
     * or to add annotations at specific positions (indexAtPositions contains positions). The latter
     * is used for standoff annotations.
     *
     * @param name annotation name
     * @param value annotation value
     * @param increment if indexAtPosition == 1: the token increment to use
     * @param indexAtPositions if null: index at the current position; otherwise: index at these positions
     */
    protected void annotation(String name, String value, int increment, List<Integer> indexAtPositions) {
    	ComplexFieldProperty property = currentComplexField.getProperty(name);
    	if (indexAtPositions == null) {
            if (name.equals("word"))
                trace(value + " ");
            if (name.equals("rating"))
                trace("{" + value + "}");
    	    property.addValue(value, increment);
    	    //System.out.println("Field " + currentComplexField.getName() + ", property " + property.getName() + ": added " + value + ", lastValuePos = " + property.lastValuePosition());
    	} else {
            //System.out.println("Field " + currentComplexField.getName() + ", property " + property.getName() + ", value " + value + " (STANDOFF)");
    	    for (Integer position: indexAtPositions) {
    	        property.addValueAtPosition(value, position);
                //System.out.println("  added at position " + position);
    	    }
    	}
    }

    /**
     * Index a subannotation.
     *
     * Can be used to add subannotation(s) at the current position (indexAtPositions == null),
     * or to add annotations at specific positions (indexAtPositions contains positions). The latter
     * is used for standoff annotations.
     *
     * @param mainName annotation name
     * @param subName subannotation name
     * @param value annotation value
     * @param indexAtPositions if null: index at the current position; otherwise: index at these positions
     */
    private void subAnnotation(String mainName, String subName, String value, List<Integer> indexAtPositions) {
        String sep = ComplexFieldUtil.SUBPROPERTY_SEPARATOR;
        String newVal = sep + subName + sep + value;
        annotation(mainName, newVal, 0, indexAtPositions); // increment 0 because we don't want to advance to the next token yet
    }

    protected void metadata(String fieldName, String value) {
        fieldName = optTranslateFieldName(fieldName);
    	traceln("METADATA " + fieldName + "=" + value);

    	if (fieldName != null && value != null) {
    		if (indexer != null)
    			addMetadataField(fieldName, value);
    	} else {
    		warn("Incomplete metadata field: " + fieldName + "=" + value);
    	}
    }

    protected void warn(String msg) {
        if (indexer != null)
        	indexer.getListener().warning(msg);
        else
        	System.err.println(msg);
    }

	@Override
	public int getCharacterPosition() {
	    // VTD-XML provides no way of getting the current character position,
	    // only the byte position.
	    // In order to keep track of character position (which we need for Lucene's term vector),
	    // we fetch the bytes processed since this method was last called, convert them to a String,
	    // and use the string length to adjust the character position.
	    // Note that this only works if this method is called for increasing byte positions,
	    // which is true because we only use it for word tags.
		try {
            long fragment = nav.getElementFragment();
            int currentByteOffset = (int)fragment;
            if (currentByteOffset > lastCharPositionByteOffset) {
                int length = currentByteOffset - lastCharPositionByteOffset;
                String str = new String(inputDocument, lastCharPositionByteOffset, length);
                lastCharPosition += str.length();
                lastCharPositionByteOffset = currentByteOffset;
            }
            return lastCharPosition;
        } catch (NavException e) {
            throw new RuntimeException(e);
        }
	}


}
