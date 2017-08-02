package nl.inl.blacklab.index.xpath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.VTDException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;

import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.xpath.InlineObject.InlineObjectType;
import nl.inl.util.ExUtil;
import nl.inl.util.StringUtil;

/**
 * An indexer configured using full XPath 1.0 expressions.
 */
public class DocIndexerXPath extends DocIndexerConfig {

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

    /** The config for the annotated (complex) field we're currently processing. */
    private ConfigAnnotatedField currentAnnotatedField;

    public DocIndexerXPath() {
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
        super.index();

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

        startDocument();

        // For each configured annotated field...
        for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields().values()) {
        	processAnnotatedField(annotatedField);
        }

        // For each configured metadata block..
        for (ConfigMetadataBlock b: config.getMetadataBlocks()) {
            processMetadataBlock(b);
        }

        // For each linked document...
        for (ConfigLinkedDocument ld: config.getLinkedDocuments().values()) {
            processLinkedDocument(ld);
        }

        endDocument();
    }

    protected void processAnnotatedField(ConfigAnnotatedField annotatedField)
            throws XPathParseException, XPathEvalException, NavException, VTDException {
        Map<String, Integer> tokenPositionsMap = new HashMap<>();

        // Determine some useful stuff about the field we're processing
        // and store in instance variables so our methods can access them
        setCurrentAnnotatedField(annotatedField);

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
                apTags.selectXPath(inlineTag.getPath());
                while (apTags.evalXPath() != -1) {
                    collectInlineTag(tagsAndPunct);
                }
                nav.pop();
            }
            setAddDefaultPunctuation(true);
            if (annotatedField.getPunctPath() != null) {
                // We have punctuation occurring between word tags (as opposed to
                // punctuation that is tagged as a word itself). Collect this punctuation.
                setAddDefaultPunctuation(false);
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
                    tokenPositionsMap.put(tokenPositionId, getCurrentTokenPosition());
                }

                // Does an inline object occur before this word?
                long wordFragment = nav.getContentFragment();
                int wordOffset = (int)wordFragment;
                while (nextInlineObject != null && wordOffset >= nextInlineObject.getOffset()) {
                    // Yes. Handle it.
                	if (nextInlineObject.type() == InlineObjectType.PUNCTUATION)
                		punctuation(nextInlineObject.getText());
                	else
                		inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObjectType.OPEN_TAG, nextInlineObject.getAttributes());
                    nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
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
            		punctuation(nextInlineObject.getText());
            	else
            		inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObjectType.OPEN_TAG, nextInlineObject.getAttributes());
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

                for (ConfigAnnotation annotation: standoff.getAnnotations().values()) {
                    processAnnotation(annotation, apAnnot, tokenPositions);
                }
            }
            nav.pop();
        }
    }

    protected void processMetadataBlock(ConfigMetadataBlock b) throws XPathParseException, XPathEvalException, NavException {
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
                // adding information about fields captured in forEach's,
                // such as extra processing steps
                if (f.getValuePath() == null || f.getValuePath().isEmpty())
                    continue;

                // Capture whatever this configured metadata field points to
                if (f.isForEach()) {
                    // "forEach" metadata specification
                    // (allows us to capture many metadata fields with 3 XPath expressions)
                    nav.push();
                    apMetaForEach.selectXPath(f.getForEachPath());
                    apFieldName.selectXPath(f.getName());
                    apMetadata.selectXPath(f.getValuePath());
                    while (apMetaForEach.evalXPath() != -1) {
                        // Find the fieldName and value for this forEach match
                        apFieldName.resetXPath();
                        String fieldName = apFieldName.evalXPathToString();
                        apMetadata.resetXPath();
                        String metadataValue = apMetadata.evalXPathToString();
                        metadataValue = processString(metadataValue, f.getProcess());
                        ConfigMetadataField metadataField = b.getField(fieldName);
                        if (metadataField != null) {
                            // Also execute process defined for named metadata field, if any
                            metadataValue = processString(metadataValue, metadataField.getProcess());
                        }
                        addMetadataField(fieldName, metadataValue);
                    }
                    nav.pop();
                } else {
                    // Regular metadata field; just the fieldName and an XPath expression for the value
                    apMetadata.selectXPath(f.getValuePath());
                    String metadataValue = apMetadata.evalXPathToString();
                    metadataValue = processString(metadataValue, f.getProcess());
                    addMetadataField(f.getName(), metadataValue);
                }
            }

        }
        nav.pop();
    }

    protected void processLinkedDocument(ConfigLinkedDocument ld) throws XPathParseException {
        // Resolve linkPaths to get the information needed to fetch the document
        List<String> results = new ArrayList<>();
        AutoPilot apLinkPath = createAutoPilot();
        for (ConfigLinkValue linkValue: ld.getLinkValues()) {
            String result = "";
            String valuePath = linkValue.getValuePath();
            String valueField = linkValue.getValueField();
            if (valuePath != null) {
                // Resolve value using XPath
                apLinkPath.selectXPath(valuePath);
                result = apLinkPath.evalXPathToString();
                if (result == null || result.isEmpty()) {
                    switch(ld.getIfLinkPathMissing()) {
                    case IGNORE:
                        break;
                    case WARN:
                        indexer.getListener().warning("Link path " + valuePath + " not found in document " + documentName);
                        break;
                    case FAIL:
                        throw new RuntimeException("Link path " + valuePath + " not found in document " + documentName);
                    }
                }
            } else if (valueField != null) {
                // Fetch value from Lucene doc
                result = currentLuceneDoc.get(valueField);
            }
            result = processString(result, linkValue.getProcess());
            results.add(result);
        }

        // Substitute link path results in inputFile, pathInsideArchive and documentPath
        String inputFile = replaceDollarRefs(ld.getInputFile(), results);
        String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
        String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);

        try {
            // Fetch and index the linked document
            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormat(), ld.shouldStore() ? ld.getName() : null);
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
        annotValue = processString(annotValue, annotation.getProcess());
        annotation(annotation.getName(), annotValue, 1, indexAtPositions);

        // For each configured subannotation...
        AutoPilot apValue = createAutoPilot();
        AutoPilot apName = createAutoPilot();
        AutoPilot apForEach = createAutoPilot();
        for (ConfigAnnotation subAnnot: annotation.getSubAnnotations()) {
            // Subannotation configs without a valuePath are just for
            // adding information about subannotations captured in forEach's,
            // such as extra processing steps
            if (subAnnot.getValuePath() == null || subAnnot.getValuePath().isEmpty())
                continue;

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
                    value = processString(value, subAnnot.getProcess());
                    ConfigAnnotation actualSubAnnot = annotation.getSubAnnotation(name);
                    if (actualSubAnnot != null) {
                        // Also apply process defined in named subannotation, if any
                        value = processString(value, actualSubAnnot.getProcess());
                    }
                    subAnnotation(annotation.getName(), name, value, indexAtPositions);
                }
                nav.pop();
            } else {
                // Regular metadata field; just the fieldName and an XPath expression for the value
                apValue.selectXPath(subAnnot.getValuePath());
                String value = apValue.evalXPathToString();
                value = processString(value, subAnnot.getProcess());
                subAnnotation(annotation.getName(), subAnnot.getName(), value, indexAtPositions);
            }
        }

        if (basePath != null) {
            // We pushed when we navigated to the base element; pop now.
            nav.pop();
        }
    }

    @Override
    public void indexSpecificDocument(String documentXPath) {
        super.indexSpecificDocument(documentXPath);

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

    @Override
    protected void startDocument() {
        super.startDocument();

        try {
            long fragment = nav.getElementFragment();
            documentByteOffset = (int)fragment;
            documentLengthBytes = (int)(fragment >> 32);
        } catch (NavException e) {
            throw new RuntimeException(e);
        }

        lastCharPosition = 0;
        lastCharPositionByteOffset = documentByteOffset;
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(new String(inputDocument, documentByteOffset, documentLengthBytes));
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

    protected void setCurrentAnnotatedField(ConfigAnnotatedField annotatedField) {
        currentAnnotatedField = annotatedField;
        setCurrentComplexField(currentAnnotatedField.getName());
    }

}
