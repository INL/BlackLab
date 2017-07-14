package nl.inl.blacklab.index.xpath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.xpath.InlineObject.InlineObjectType;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.ExUtil;
import nl.inl.util.StringUtil;

public abstract class DocIndexerXPath extends DocIndexer {

    /** Our input document */
    private byte[] inputDocument;

    /** VTD parser (generator?) */
    private VTDGen vg;

    /** VTD navigator */
    private VTDNav nav;

    /** Our input format */
    private ConfigInputFormat config;

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

    public DocIndexerXPath() {
        config = new ConfigInputFormat();
        configure(config);
        
        for (ConfigAnnotatedField af: config.getAnnotatedFields()) {
        	
	        // Define the properties that make up our complex field
        	List<ConfigAnnotation> annotations = af.getAnnotations();
        	if (annotations.size() == 0)
        		throw new RuntimeException("No annotations defined for field " + af.getFieldName());
        	ConfigAnnotation mainAnnotation = annotations.get(0);
	        ComplexField complexField = new ComplexField(af.getFieldName(), mainAnnotation.getName(), getSensitivitySetting(mainAnnotation.getName()), false);
	        complexFields.put(af.getFieldName(), complexField);
	        complexField.addProperty(ComplexFieldUtil.PUNCTUATION_PROP_NAME, getSensitivitySetting(ComplexFieldUtil.PUNCTUATION_PROP_NAME), false);	        
	        ComplexFieldProperty propStartTag = complexField.addProperty(ComplexFieldUtil.START_TAG_PROP_NAME, getSensitivitySetting(ComplexFieldUtil.START_TAG_PROP_NAME), true);	        
	        propStartTag.setForwardIndex(false);
	        
	        IndexStructure indexStructure;
	        if (!DEBUG) {
		        indexStructure = indexer.getSearcher().getIndexStructure();
		        indexStructure.registerComplexField(complexField.getName(), complexField.getMainProperty().getName());
	        }
	        for (int i = 1; i < annotations.size(); i++) {
	        	ConfigAnnotation annot = annotations.get(i);
	        	complexField.addProperty(annot.getName(), getSensitivitySetting(annot.getName()), false);
	        }
		
	        if (!DEBUG) {
		        // If the indexmetadata file specified a list of properties that shouldn't get a forward
		        // index, make the new complex field aware of this.
		        Set<String> noForwardIndexProps = indexStructure.getComplexFieldDesc(complexField.getName()).getNoForwardIndexProps();
		        complexField.setNoForwardIndexProps(noForwardIndexProps);
	        }
        }
        
    }

    protected abstract void configure(ConfigInputFormat config);

    @Override
    public void setDocument(File file, Charset charset) throws FileNotFoundException {
        try {
            inputDocument = FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(byte[] contents, Charset cs) {
        this.inputDocument = contents;
    }

    @Override
    public void setDocument(InputStream is, Charset cs) {
        try {
            this.inputDocument = IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            this.inputDocument = IOUtils.toString(reader).getBytes("utf-8");
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

        // Parse use VTD-XML
        vg = new VTDGen();
        vg.setDoc(inputDocument);
        vg.parse(config.isNamespaceAware());

        nav = vg.getNav();

        // Find all documents
        AutoPilot documents = createAutoPilot();
        documents.selectXPath(config.getXPathDocuments());
        while(documents.evalXPath() != -1) {

            long documentFragment = nav.getContentFragment();
            int documentOffset = (int)documentFragment;
            int documentLength = (int)(documentFragment >> 32);

            startDocument(documentOffset, documentLength);

            // For each configured annotated field...
            for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields()) {
            	
            	// Store some useful stuff about the field we're processing
            	currentAnnotatedField = annotatedField;
        		currentComplexField = complexFields.get(currentAnnotatedField.getFieldName());
        		propStartTag = currentComplexField.getTagProperty();
        		propMain = currentComplexField.getMainProperty();
        		propPunct = currentComplexField.getPunctProperty();

                AutoPilot words = createAutoPilot();
                words.selectXPath(annotatedField.getXPathWords());

                // For each body element...
                // (there's usually only one, but there's no reason to limit it)
                nav.push();
                AutoPilot bodies = createAutoPilot();
                bodies.selectXPath(annotatedField.getXPathBody());
                while (bodies.evalXPath() != -1) {

                    // First we find all inline elements (stuff like s, p, b, etc.) and store
                    // the locations of their start and end tags in a sorted list.
                    // This way, we can keep track of between which words these tags occur.
                    // For end tags, we will update the payload of the start tag when we encounter it,
                    // just like we do in our SAX parsers.
                    AutoPilot apTags = createAutoPilot();
                    List<InlineObject> tagsAndPunct = new ArrayList<>();
                    for (String xpInlineTag: annotatedField.getXPathsInlineTag()) {
                        nav.push();
                        apTags.selectXPath(xpInlineTag);
                        while (apTags.evalXPath() != -1) {
                            collectInlineTag(tagsAndPunct);
                        }
                        nav.pop();
                    }
                    if (annotatedField.getXPathPunct() != null) {
                    	nav.push();
                        apTags.selectXPath(annotatedField.getXPathPunct());
                        while (apTags.evalXPath() != -1) {
                            collectPunct(tagsAndPunct);
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

                        long wordFragment = nav.getContentFragment();
                        int wordOffset = (int)wordFragment;

                        // Does an inline object occur before this word?
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
                        AutoPilot apAnnot = createAutoPilot();
                        for (ConfigAnnotation annotation: annotatedField.getAnnotations()) {
                            nav.push();
                        	apAnnot.selectXPath(annotation.getXPathValue());
                            String annotValue = apAnnot.evalXPathToString();
                            annotation(annotation.getName(), annotValue);
                            nav.pop();
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
            }

            // For each metadata block..
            nav.push();
            AutoPilot apMetadataBlock = createAutoPilot();
            apMetadataBlock.selectXPath(config.getXPathMetadataContainer());
            while (apMetadataBlock.evalXPath() != -1) {

                // For each configured metadata field...
                AutoPilot apMetadata = createAutoPilot();
                AutoPilot apFieldName = createAutoPilot();
                AutoPilot apMetaForEach = createAutoPilot();
                for (ConfigMetadataField f: config.getMetadataFields()) {

                    // Capture whatever this configured metadata field points to
                    nav.push();
                    if (f.isForEach()) {
                        // "forEach" metadata specification
                        // (allows us to capture many metadata fields with 3 XPath expressions)
                        apMetaForEach.selectXPath(f.getXPathForEach());
                        apFieldName.selectXPath(f.getFieldName());
                        apMetadata.selectXPath(f.getXPathValue());
                        while (apMetaForEach.evalXPath() != -1) {
                            // Find the fieldName and value for this forEach match
                            apFieldName.resetXPath();
                            String fieldName = apFieldName.evalXPathToString();
                            apMetadata.resetXPath();
                            String metadataValue = apMetadata.evalXPathToString();
                            metadata(fieldName, metadataValue);
                        }
                    } else {
                        // Regular metadata field; just the fieldName and an XPath expression for the value
                        apMetadata.selectXPath(f.getXPathValue());
                        String metadataValue = apMetadata.evalXPathToString();
                        metadata(f.getFieldName(), metadataValue);
                    }
                    nav.pop();
                }

            }
            nav.pop();

            endDocument(documentOffset, documentLength);
        }

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
		String elementName = nav.toString(currentIndex);

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
	private void collectPunct(List<InlineObject> inlineObjects) throws NavException {
		int i = nav.getCurrentIndex();
		int offset = nav.getTokenOffset(i);
		int length = nav.getTokenLength(i);
		
//		long contentFragment = nav.getContentFragment();
//		int contentOffset = (int)contentFragment;
//		int contentLength = (int)(contentFragment >> 32);
//		int contentEnd = contentOffset + contentLength;

		// Find text
		//int currentIndex = nav.getCurrentIndex();
		String text = nav.toString(i);
		text = StringUtil.normalizeWhitespace(text);

		// Add the punct to the list
		inlineObjects.add(new InlineObject(text, offset, offset + length, InlineObjectType.PUNCTUATION, null));
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

//	/** Return the exact original code for an inline start or end tag. */
//    private String getInlineTagCode(InlineObject tag) {
//        ByteArrayOutputStream os = new ByteArrayOutputStream();
//        try {
//            nav.dumpFragment(tag.fragment(), os);
//            return new String(os.toByteArray(), StandardCharsets.UTF_8);
//        } catch (NavException | IOException e) {
//            throw new RuntimeException(e);
//        }
//    }

    // HANDLERS
    
    private static final boolean DEBUG = true;
    
    void trace(String msg) {
    	if (DEBUG)
    		System.out.print(msg);
    }

    void traceln(String msg) {
    	if (DEBUG)
    		System.out.println(msg);
    }

    protected void startDocument(int offset, int length) {
    	traceln("START DOCUMENT");
    	
        startCaptureContent();
        
        currentLuceneDoc = new Document();
        if (!DEBUG)
        	currentLuceneDoc.add(new Field("fromInputFile", documentName, indexer.getMetadataFieldType(false)));
        
        // DEPRECATED for these types of indexer, but still supported for now
        addMetadataFieldsFromParameters();

        if (!DEBUG)
        	indexer.getListener().documentStarted(documentName);
    }

	protected void endDocument(int offset, int length) {
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
                if (!DEBUG) {
	                int fiid = indexer.addToForwardIndex(fieldName, prop);
	                currentLuceneDoc.add(new IntField(ComplexFieldUtil
	                        .forwardIndexIdField(fieldName), fiid, Store.YES));
                }
            }
            
            // Reset complex field for next document
            complexField.clear();

        }
        
        // Finish storing the document in the document store (parts of it
        // may already have been written because we write in chunks to save memory),
        // retrieve the content id, and store that in Lucene.
        // (Note that we do this after adding the dummy token, so the character
        // positions for the dummy token still make (some) sense)
        int contentId = storeCapturedContent();
        if (mainComplexField == null) {
        	// If we don't have complex fields, we're probably parsing metadata.
        	mainComplexField = "metadata";
        }
        currentLuceneDoc.add(new IntField(ComplexFieldUtil
                .contentIdField(mainComplexField), contentId,
                Store.YES));

//        // If there's an external metadata fetcher, call it now so it can
//        // add the metadata for this document and (optionally) store the
//        // metadata document in the content store (and the corresponding id in the
//        // Lucene doc)
//        MetadataFetcher m = getMetadataFetcher();
//        if (m != null) {
//            m.addMetadata();
//        }

        if (!DEBUG) {
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
	                addMetadataField(fieldName, fd.getUnknownValue());
	        }
        }

        try {
            // Add Lucene doc to indexer
        	if (!DEBUG)
            	indexer.add(currentLuceneDoc);
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }

        // Report progress
        reportCharsProcessed();
        if (!DEBUG)
        	reportTokensProcessed(wordsDone);
        wordsDone = 0;
        if (!DEBUG)
        	indexer.getListener().documentDone(documentName);

        currentLuceneDoc = null;

        // Stop if required
        if (!DEBUG) {
        	if (!indexer.continueIndexing())
        		throw new MaxDocsReachedException();
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

    private void punctuation(InlineObject punct) {
    	trace(punct.getText());
    	
    	propPunct.addValue(punct.getText());
	}

    protected void beginWord() {
        currentComplexField.addStartChar(getCharacterPosition());
    }

    protected void endWord() {
    	currentComplexField.addEndChar(getCharacterPosition());

        // Report progress regularly but not too often
        wordsDone++;
        if (wordsDone >= 5000) {
            reportCharsProcessed();
            reportTokensProcessed(wordsDone);
            wordsDone = 0;
        }
    }

    protected void annotation(String name, String value) {
    	if (name.equals("word"))
    		trace(value + " ");
    	
    	currentComplexField.getProperty(name).addValue(value);
    }

    protected void metadata(String fieldName, String value) {
    	traceln("METADATA " + fieldName + "=" + value);
    	
    	if (fieldName != null && value != null) {
    		if (!DEBUG)
    			addMetadataField(fieldName, value);
    	} else {
    		if (!DEBUG)
    			indexer.getListener().warning("Incomplete metadata field: " + fieldName + "=" + value);
    		else
    			System.err.println("Incomplete metadata field: " + fieldName + "=" + value);
    	}
    }

	@Override
	public void reportCharsProcessed() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int getCharacterPosition() {
		// TODO Auto-generated method stub
		return 0;
	}
    
	protected void startCaptureContent() {
		// TODO Auto-generated method stub
		
	}

    protected int storeCapturedContent() {
		// TODO Auto-generated method stub
		return 0;
	}

    

}
