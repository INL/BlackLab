package nl.inl.blacklab.index.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import nl.inl.blacklab.index.config.InlineObject.InlineObjectType;
import nl.inl.util.ExUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.XmlUtil;

/**
 * An indexer configured using full XPath 1.0 expressions.
 */
public class DocIndexerXPath extends DocIndexerConfig {

    private static enum FragmentPosition {
        BEFORE_OPEN_TAG,
        AFTER_OPEN_TAG,
        BEFORE_CLOSE_TAG,
        AFTER_CLOSE_TAG
    }

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

    /** Where the current position is relative to the current fragment */
    private FragmentPosition fragPos = FragmentPosition.BEFORE_OPEN_TAG;

    /** Fragment positions in ancestors */
    private List<FragmentPosition> fragPosStack = new ArrayList<>();

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
        if (config.shouldResolveNamedEntityReferences()) {
            // Document contains old DTD-style named entity declarations. Resolve them because VTD-XML can't deal with these.
            String doc = XmlUtil.readXmlAndResolveReferences(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contents), defaultCharset)));
            contents = doc.getBytes(defaultCharset);
        }
        this.inputDocument = contents;
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        try {
            setDocument(IOUtils.toByteArray(is), defaultCharset);
            is.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        // NOP, we already closed our input after we read it
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            setDocument(IOUtils.toString(reader).getBytes(Indexer.DEFAULT_INPUT_ENCODING), Indexer.DEFAULT_INPUT_ENCODING);
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Map from XPath expression to compiled XPath. */
    Map<String, AutoPilot> compiledXPaths = new HashMap<>();

    /** Map from XPath expression to compiled XPath. */
    Map<AutoPilot, String> autoPilotsInUse = new HashMap<>();

    /**
     * Create AutoPilot and declare namespaces on it.
     * @param xpathExpr xpath expression for the AutoPilot
     * @return the AutoPilot
     * @throws XPathParseException
     */
    AutoPilot acquireAutoPilot(String xpathExpr) throws XPathParseException {
        AutoPilot ap = compiledXPaths.remove(xpathExpr);
        if (ap == null) {
            ap = new AutoPilot(nav);
            if (config.isNamespaceAware()) {
                ap.declareXPathNameSpace("xml", "http://www.w3.org/XML/1998/namespace"); // builtin
                for (Entry<String, String> e: config.getNamespaces().entrySet()) {
                    ap.declareXPathNameSpace(e.getKey(), e.getValue());
                }
            }
            try {
                ap.selectXPath(xpathExpr);
            } catch (XPathParseException e) {
                throw new RuntimeException("Error in XPath expression " + xpathExpr + " : " + e.getMessage(), e);
            }
        } else {
            ap.resetXPath();
        }
        autoPilotsInUse.put(ap, xpathExpr);
        return ap;
    }

    void releaseAutoPilot(AutoPilot ap) {
        String xpathExpr = autoPilotsInUse.remove(ap);
        compiledXPaths.put(xpathExpr, ap);
    }

    @Override
    public void index() throws Exception {
        super.index();

        // Parse use VTD-XML
        vg = new VTDGen();
        vg.setDoc(inputDocument);
        // Whitespace in between elements is normally ignored,
        // but we explicitly allow whitespace in between elements to be collected here.
        // This allows punctuation xpath to match this whitespace, in case punctuation/whitespace in the document isn't contained in a dedicated element or attribute.
        // This doesn't mean that this whitespace is always used, it just enables the punctuation xpath to find this whitespace if it explicitly matches it.
        vg.enableIgnoredWhiteSpace(true);
        vg.parse(config.isNamespaceAware());

        nav = vg.getNav();

        // Find all documents
        AutoPilot documents = acquireAutoPilot(config.getDocumentPath());
        while(documents.evalXPath() != -1) {
            indexDocument();
        }
        releaseAutoPilot(documents);

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

        // Precompile XPaths for words, evalToString, inline tags, punct and (sub)annotations
        AutoPilot words = acquireAutoPilot(annotatedField.getWordsPath());
        AutoPilot apEvalToString = acquireAutoPilot(".");
        List<AutoPilot> apsInlineTag = new ArrayList<>();
        for (ConfigInlineTag inlineTag: annotatedField.getInlineTags()) {
            AutoPilot apInlineTag = acquireAutoPilot(inlineTag.getPath());
            apsInlineTag.add(apInlineTag);
        }
        AutoPilot apPunct = null;
        if (annotatedField.getPunctPath() != null)
            apPunct = acquireAutoPilot(annotatedField.getPunctPath());
        String tokenPositionIdPath = annotatedField.getTokenPositionIdPath();
        AutoPilot apTokenPositionId = null;
        if (tokenPositionIdPath != null) {
            apTokenPositionId = acquireAutoPilot(tokenPositionIdPath);
        }

        // For each body element...
        // (there's usually only one, but there's no reason to limit it)
        navpush();
        AutoPilot bodies = acquireAutoPilot(annotatedField.getContainerPath());
        while (bodies.evalXPath() != -1) {

            // First we find all inline elements (stuff like s, p, b, etc.) and store
            // the locations of their start and end tags in a sorted list.
            // This way, we can keep track of between which words these tags occur.
            // For end tags, we will update the payload of the start tag when we encounter it,
            // just like we do in our SAX parsers.
            List<InlineObject> tagsAndPunct = new ArrayList<>();
            for (AutoPilot apInlineTag: apsInlineTag) {
                navpush();
                apInlineTag.resetXPath();
                while (apInlineTag.evalXPath() != -1) {
                    collectInlineTag(tagsAndPunct);
                }
                navpop();
            }
            setAddDefaultPunctuation(true);
            if (apPunct != null) {
                // We have punctuation occurring between word tags (as opposed to
                // punctuation that is tagged as a word itself). Collect this punctuation.
                setAddDefaultPunctuation(false);
            	navpush();
            	apPunct.resetXPath();
                while (apPunct.evalXPath() != -1) {
                    apEvalToString.resetXPath();
                    String punct = apEvalToString.evalXPathToString();
                    collectPunct(tagsAndPunct, punct);
                }
                navpop();
            }
            Collections.sort(tagsAndPunct);
            Iterator<InlineObject> inlineObjectsIt = tagsAndPunct.iterator();
            InlineObject nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;

            // Now, find all words, keeping track of what inline objects occur in between.
            navpush();
            words.resetXPath();
            while (words.evalXPath() != -1) {

                // Capture tokenPositionId for this token position?
                if (apTokenPositionId != null) {
                    apTokenPositionId.resetXPath();
                    String tokenPositionId = apTokenPositionId.evalXPathToString();
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

                fragPos = FragmentPosition.BEFORE_OPEN_TAG;
                beginWord();

                // For each configured annotation...
                for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                    processAnnotation(annotation, null);
                }

                fragPos = FragmentPosition.AFTER_CLOSE_TAG;
                endWord();
            }
            navpop();

            // Handle any inline objects after the last word
            while (nextInlineObject != null) {
            	if (nextInlineObject.type() == InlineObjectType.PUNCTUATION)
            		punctuation(nextInlineObject.getText());
            	else
            		inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObjectType.OPEN_TAG, nextInlineObject.getAttributes());
                nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
            }

        }
        navpop();

        // For each configured standoff annotation...
        for (ConfigStandoffAnnotations standoff: annotatedField.getStandoffAnnotations()) {
            // For each instance of this standoff annotation..
            navpush();
            AutoPilot apStandoff = acquireAutoPilot(standoff.getPath());
            AutoPilot apTokenPos = acquireAutoPilot(standoff.getRefTokenPositionIdPath());
            while (apStandoff.evalXPath() != -1) {

                // Determine what token positions to index these values at
                navpush();
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
                navpop();

                for (ConfigAnnotation annotation: standoff.getAnnotations().values()) {
                    processAnnotation(annotation, tokenPositions);
                }
            }
            releaseAutoPilot(apStandoff);
            releaseAutoPilot(apTokenPos);
            navpop();
        }

        releaseAutoPilot(words);
        releaseAutoPilot(apEvalToString);
        for (AutoPilot ap: apsInlineTag) {
            releaseAutoPilot(ap);
        }
        if (apPunct != null)
            releaseAutoPilot(apPunct);
        if (apTokenPositionId != null)
            releaseAutoPilot(apTokenPositionId);
        releaseAutoPilot(bodies);
    }

    protected void navpush() {
        nav.push();
        fragPosStack.add(fragPos);
        fragPos = FragmentPosition.BEFORE_OPEN_TAG;
    }

    protected void navpop() {
        nav.pop();
        fragPos = fragPosStack.remove(fragPosStack.size() - 1);
    }

    protected void processMetadataBlock(ConfigMetadataBlock b) throws XPathParseException, XPathEvalException, NavException {
        // For each instance of this metadata block...
        navpush();
        AutoPilot apMetadataBlock = acquireAutoPilot(b.getContainerPath());
        while (apMetadataBlock.evalXPath() != -1) {

            // For each configured metadata field...
            for (ConfigMetadataField f: b.getFields()) {

                // Metadata field configs without a valuePath are just for
                // adding information about fields captured in forEach's,
                // such as extra processing steps
                if (f.getValuePath() == null || f.getValuePath().isEmpty())
                    continue;

                // Capture whatever this configured metadata field points to
                AutoPilot apMetadata = acquireAutoPilot(f.getValuePath());
                if (f.isForEach()) {
                    // "forEach" metadata specification
                    // (allows us to capture many metadata fields with 3 XPath expressions)
                    navpush();
                    AutoPilot apMetaForEach = acquireAutoPilot(f.getForEachPath());
                    AutoPilot apFieldName = acquireAutoPilot(f.getName());
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
                    releaseAutoPilot(apMetaForEach);
                    releaseAutoPilot(apFieldName);
                    navpop();
                } else {
                    // Regular metadata field; just the fieldName and an XPath expression for the value
                    String metadataValue = apMetadata.evalXPathToString();
                    metadataValue = processString(metadataValue, f.getProcess());
                    addMetadataField(f.getName(), metadataValue);
                }
                releaseAutoPilot(apMetadata);
            }

        }
        releaseAutoPilot(apMetadataBlock);
        navpop();
    }

    protected void processLinkedDocument(ConfigLinkedDocument ld) throws XPathParseException {
        // Resolve linkPaths to get the information needed to fetch the document
        List<String> results = new ArrayList<>();
        for (ConfigLinkValue linkValue: ld.getLinkValues()) {
            String result = "";
            String valuePath = linkValue.getValuePath();
            String valueField = linkValue.getValueField();
            if (valuePath != null) {
                // Resolve value using XPath
                AutoPilot apLinkPath = acquireAutoPilot(valuePath);
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
                releaseAutoPilot(apLinkPath);
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
            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormatIdentifier(), ld.shouldStore() ? ld.getName() : null);
        } catch (Exception e) {
            String moreInfo = "(inputFile = " + inputFile;
            if (pathInsideArchive != null)
                moreInfo += ", pathInsideArchive = " + pathInsideArchive;
            if (documentPath != null)
                moreInfo += ", documentPath = " + documentPath;
            moreInfo += ")";
            switch(ld.getIfLinkPathMissing()) {
            case IGNORE:
            case WARN:
                indexer.getListener().warning("Could not find or parse linked document for " + documentName + moreInfo + ": " + e.getMessage());
                break;
            case FAIL:
                throw new RuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
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
    protected void processAnnotation(ConfigAnnotation annotation, List<Integer> indexAtPositions) throws VTDException {
        String basePath = annotation.getBasePath();
        if (basePath != null) {
            // Basepath given. Navigate to the (first) matching element and evaluate the other XPaths from there.
            navpush();
            AutoPilot apBase = acquireAutoPilot(basePath);
            apBase.evalXPath();
            releaseAutoPilot(apBase);
        }

        String valuePath = annotation.getValuePath();

        // See if we want to capture any values and substitute them into the XPath
        int i = 1;
        for (String captureValuePath: annotation.getCaptureValuePaths()) {
            AutoPilot apCaptureValuePath = acquireAutoPilot(captureValuePath);
            String value = apCaptureValuePath.evalXPathToString();
            releaseAutoPilot(apCaptureValuePath);
            valuePath = valuePath.replace("$" + i, value);
            i++;
        }

        // Find matches for this annotation.
        findAnnotationMatches(annotation, null, valuePath, indexAtPositions);

        // For each configured subannotation...
        for (ConfigAnnotation subAnnot: annotation.getSubAnnotations()) {
            // Subannotation configs without a valuePath are just for
            // adding information about subannotations captured in forEach's,
            // such as extra processing steps
            if (subAnnot.getValuePath() == null || subAnnot.getValuePath().isEmpty())
                continue;

            // Capture this subannotation value
            AutoPilot apValue = acquireAutoPilot(subAnnot.getValuePath());
            if (subAnnot.isForEach()) {
                // "forEach" subannotation specification
                // (allows us to capture multiple subannotations with 3 XPath expressions)
                navpush();
                AutoPilot apForEach = acquireAutoPilot(subAnnot.getForEachPath());
                AutoPilot apName = acquireAutoPilot(subAnnot.getName());
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
                releaseAutoPilot(apForEach);
                releaseAutoPilot(apName);
                navpop();
            } else {
                // Regular metadata field; just the fieldName and an XPath expression for the value
                findAnnotationMatches(annotation, subAnnot, valuePath, indexAtPositions);
            }
            releaseAutoPilot(apValue);
        }

        if (basePath != null) {
            // We pushed when we navigated to the base element; pop now.
            navpop();
        }
    }

    protected void findAnnotationMatches(ConfigAnnotation annotation, ConfigAnnotation subAnnot, String valuePath, List<Integer> indexAtPositions)
            throws XPathParseException, XPathEvalException, NavException {
        AutoPilot apValuePath = acquireAutoPilot(valuePath);
        if (annotation.isMultipleValues()) {
            // Multiple matches will be indexed at the same position.
            AutoPilot apEvalToString = acquireAutoPilot(".");
            boolean firstValue = true;
            while (apValuePath.evalXPath() != -1) {
                apEvalToString.resetXPath();
                String annotValue = apEvalToString.evalXPathToString();
                annotValue = processString(annotValue, annotation.getProcess());
                if (subAnnot == null)
                    annotation(annotation.getName(), annotValue, firstValue ? 1 : 0, indexAtPositions);
                else
                    subAnnotation(annotation.getName(), subAnnot.getName(), annotValue, indexAtPositions);
                firstValue = false;
            }
            releaseAutoPilot(apEvalToString);
        } else {
            // Single value expected
            String annotValue = apValuePath.evalXPathToString();
            annotValue = processString(annotValue, annotation.getProcess());
            if (subAnnot == null)
                annotation(annotation.getName(), annotValue, 1, indexAtPositions);
            else
                subAnnotation(annotation.getName(), subAnnot.getName(), annotValue, indexAtPositions);
        }
        releaseAutoPilot(apValuePath);
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

            boolean docDone = false;
            if (documentXPath != null) {
                // Find our specific document
                AutoPilot documents = acquireAutoPilot(documentXPath);
                while(documents.evalXPath() != -1) {
                    if (docDone)
                        throw new RuntimeException("Document link " + documentXPath + " matched multiple documents in " + documentName);
                    indexDocument();
                    docDone = true;
                }
                releaseAutoPilot(documents);
            } else {
                // Process whole file; must be 1 document
                AutoPilot documents = acquireAutoPilot(config.getDocumentPath());
                while(documents.evalXPath() != -1) {
                    if (docDone)
                        throw new RuntimeException("Linked file contains multiple documents (and no document path given) in " + documentName);
                    indexDocument();
                    docDone = true;
                }
                releaseAutoPilot(documents);
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
        long elementFragment = nav.getElementFragment();
        int startTagOffset = (int)elementFragment;
        int endTagOffset;
		long contentFragment = nav.getContentFragment();
		if (contentFragment == -1) {
		    // Empty (self-closing) element.
		    endTagOffset = startTagOffset;
		} else {
		    // Regular element with separate open and close tags.
    		int contentOffset = (int)contentFragment;
    		int contentLength = (int)(contentFragment >> 32);
    		int contentEnd = contentOffset + contentLength;
    		endTagOffset = contentEnd;
		}

		// Find element name
		int currentIndex = nav.getCurrentIndex();
		String elementName = dedupe(nav.toString(currentIndex));

		// Add the inline tags to the list
		InlineObject openTag = new InlineObject(elementName, startTagOffset, InlineObjectType.OPEN_TAG, getAttributes());
		InlineObject closeTag = new InlineObject(elementName, endTagOffset, InlineObjectType.CLOSE_TAG, null);
		openTag.setMatchingTag(closeTag);
		closeTag.setMatchingTag(openTag);
		inlineObject.add(openTag);
		inlineObject.add(closeTag);
	}

    /**
     * Add InlineObject for a punctuation text node.
     *
     * @param inlineObjects list to add the punct object to
     * @param text
     * @throws NavException
     */
	private void collectPunct(List<InlineObject> inlineObjects, String text) throws NavException {
		int i = nav.getCurrentIndex();
		int offset = nav.getTokenOffset(i);
//		int length = nav.getTokenLength(i);

		// Make sure we only keep 1 copy of identical punct texts in memory
		text = dedupe(StringUtil.normalizeWhitespace(text));

		// Add the punct to the list
		inlineObjects.add(new InlineObject(text, offset, InlineObjectType.PUNCTUATION, null));
	}

	/**
     * Gets attribute map for current element
     * @return
     */
    private Map<String, String> getAttributes() {
		navpush();
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
		navpop();
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
            int currentByteOffset = getCurrentByteOffset();
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

    protected int getCurrentByteOffset() throws NavException {
        if (fragPos == FragmentPosition.BEFORE_OPEN_TAG || fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
            long elFrag = nav.getElementFragment();
            int  elOffset = (int)elFrag;
            if (fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
                int  elLength = (int)(elFrag >> 32);
                return elOffset + elLength;
            }
            return elOffset;
        }
        long contFrag = nav.getContentFragment();
        int  contOffset = (int)contFrag;
        if (fragPos == FragmentPosition.BEFORE_CLOSE_TAG) {
            int  contLength = (int)(contFrag >> 32);
            return contOffset + contLength;
        }
        return contOffset;
    }

    protected void setCurrentAnnotatedField(ConfigAnnotatedField annotatedField) {
        currentAnnotatedField = annotatedField;
        setCurrentComplexField(currentAnnotatedField.getName());
    }

}
