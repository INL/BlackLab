package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.ximpleware.AutoPilot;
import com.ximpleware.BookMark;
import com.ximpleware.NavException;
import com.ximpleware.VTDException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.InlineObject.InlineObjectType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.XmlUtil;

/**
 * An indexer configured using full XPath 1.0 expressions.
 */
public class DocIndexerXPath extends DocIndexerConfig {

    private enum FragmentPosition {
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

    /** The config for the annotated field we're currently processing. */
    private ConfigAnnotatedField currentAnnotatedFieldConfig;

    @Override
    public void close() {
        // NOP, we already closed our input after we read it
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) throws FileNotFoundException {
        try {
            setDocument(FileUtils.readFileToByteArray(file), defaultCharset);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        if (config.shouldResolveNamedEntityReferences()) {
            // Document contains old DTD-style named entity declarations. Resolve them because VTD-XML can't deal with these.
            String doc = XmlUtil.readXmlAndResolveReferences(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contents), defaultCharset)));
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
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            setDocument(IOUtils.toByteArray(reader, Indexer.DEFAULT_INPUT_ENCODING),
                    Indexer.DEFAULT_INPUT_ENCODING);
            reader.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /** Map from XPath expression to compiled XPath. */
    private Map<String, AutoPilot> compiledXPaths = new HashMap<>();

    /**
     * AutoPilots that are currently being used. We need to keep track of this to be
     * able to re-add them to compiledXpath with the correct XPath expression later.
     */
    private Map<AutoPilot, String> autoPilotsInUse = new HashMap<>();

    /**
     * Create AutoPilot and declare namespaces on it.
     *
     * @param xpathExpr xpath expression for the AutoPilot
     * @return the AutoPilot
     * @throws XPathParseException
     */
    private AutoPilot acquireAutoPilot(String xpathExpr) throws XPathParseException {
        AutoPilot ap = compiledXPaths.remove(xpathExpr);
        if (ap == null) {
            ap = new AutoPilot(nav);
            if (config.isNamespaceAware()) {
                ap.declareXPathNameSpace("xml", "http://www.w3.org/XML/1998/namespace"); // builtin
                for (Entry<String, String> e : config.getNamespaces().entrySet()) {
                    ap.declareXPathNameSpace(e.getKey(), e.getValue());
                }
            }
            try {
                ap.selectXPath(xpathExpr);
            } catch (XPathParseException e) {
                throw new BlackLabRuntimeException("Error in XPath expression " + xpathExpr + " : " + e.getMessage(), e);
            }
        } else {
            ap.resetXPath();
        }
        autoPilotsInUse.put(ap, xpathExpr);
        return ap;
    }

    private void releaseAutoPilot(AutoPilot ap) {
        String xpathExpr = autoPilotsInUse.remove(ap);
        compiledXPaths.put(xpathExpr, ap);
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        if (inputDocument.length > 0) { // VTD doesn't like empty documents
            // Parse use VTD-XML
            vg = new VTDGen();
            vg.setDoc(inputDocument);
            // Whitespace in between elements is normally ignored,
            // but we explicitly allow whitespace in between elements to be collected here.
            // This allows punctuation xpath to match this whitespace, in case punctuation/whitespace in the document isn't contained in a dedicated element or attribute.
            // This doesn't mean that this whitespace is always used, it just enables the punctuation xpath to find this whitespace if it explicitly matches it.
            vg.enableIgnoredWhiteSpace(true);
            try {
                vg.parse(config.isNamespaceAware());

                nav = vg.getNav();

                // Find all documents
                AutoPilot documents = acquireAutoPilot(config.getDocumentPath());
                while (documents.evalXPath() != -1) {
                    indexDocument();
                }
                releaseAutoPilot(documents);
            } catch (VTDException e) {
                throw new MalformedInputFile("Error indexing file: " + documentName, e);
            }
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
        for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
            if (!annotatedField.isDummyForStoringLinkedDocuments())
                processAnnotatedField(annotatedField);
        }

        // For each configured metadata block..
        for (ConfigMetadataBlock b : config.getMetadataBlocks()) {
            processMetadataBlock(b);
        }

        // For each linked document...
        for (ConfigLinkedDocument ld : config.getLinkedDocuments().values()) {
            Function<String, String> xpathProcessor = xpath -> {
                // Resolve value using XPath
                AutoPilot apLinkPath = null;
                String result = null;
                try {
                    apLinkPath = acquireAutoPilot(xpath);
                    result = apLinkPath.evalXPathToString();
                    if (result == null || result.isEmpty()) {
                        switch (ld.getIfLinkPathMissing()) {
                            case IGNORE:
                                break;
                            case WARN:
                                docWriter.listener()
                                        .warning("Link path " + xpath + " not found in document " + documentName);
                                break;
                            case FAIL:
                                throw new BlackLabRuntimeException("Link path " + xpath + " not found in document " + documentName);
                        }
                    }
                    releaseAutoPilot(apLinkPath);
                } catch (XPathParseException e) {
                    throw new InvalidConfiguration(e.getMessage() + String.format("; when indexing file: %s", documentName), e);
                }
                return result;
            };
            processLinkedDocument(ld, xpathProcessor);
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
        for (ConfigInlineTag inlineTag : annotatedField.getInlineTags()) {
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
        AnnotatedFieldWriter annotatedFieldWriter = getAnnotatedField(annotatedField.getName());
        while (bodies.evalXPath() != -1) {

            // First we find all inline elements (stuff like s, p, b, etc.) and store
            // the locations of their start and end tags in a sorted list.
            // This way, we can keep track of between which words these tags occur.
            // For end tags, we will update the payload of the start tag when we encounter it,
            // just like we do in our SAX parsers.
            List<InlineObject> tagsAndPunct = new ArrayList<>();
            for (AutoPilot apInlineTag : apsInlineTag) {
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
                    // If punctPath matches an empty tag, replace it with a space.
                    // Deals with e.g. <lb/> (line break) tags in TEI.
                    if (punct.isEmpty())
                        punct = " ";
                    collectPunct(tagsAndPunct, punct);
                }
                navpop();
            }
            tagsAndPunct.sort(Comparator.naturalOrder());
            Iterator<InlineObject> inlineObjectsIt = tagsAndPunct.iterator();
            InlineObject nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;

            // Now, find all words, keeping track of what inline objects occur in between.
            navpush();
            words.resetXPath();

            // first find all words and sort the list -- words are returned out of order when they are at different nesting levels
            // since the xpath spec doesn't enforce any order, there's nothing we can do
            // so record their positions, sort the list, then restore the position and carry on
            List<Pair<Integer, BookMark>> wordPositions = new ArrayList<>();
            while (words.evalXPath() != -1) {
                BookMark b = new BookMark(nav);
                b.setCursorPosition();
                wordPositions.add(Pair.of(nav.getCurrentIndex(), b));
            }
            wordPositions.sort((a, b) -> a.getKey().compareTo(b.getKey()));

            for (Pair<Integer, BookMark> wordPosition : wordPositions) {
                wordPosition.getValue().setCursorPosition();

                // Capture tokenPositionId for this token position?
                if (apTokenPositionId != null) {
                    apTokenPositionId.resetXPath();
                    String tokenPositionId = apTokenPositionId.evalXPathToString();
                    tokenPositionsMap.put(tokenPositionId, getCurrentTokenPosition());
                }

                // Does an inline object occur before this word?
                long wordFragment = nav.getContentFragment();
                int wordOffset = (int) wordFragment;
                while (nextInlineObject != null && wordOffset >= nextInlineObject.getOffset()) {
                    // Yes. Handle it.
                    if (nextInlineObject.type() == InlineObjectType.PUNCTUATION)
                        punctuation(nextInlineObject.getText());
                    else
                        inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObjectType.OPEN_TAG,
                                nextInlineObject.getAttributes());
                    nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
                }

                fragPos = FragmentPosition.BEFORE_OPEN_TAG;
                beginWord();

                // For each configured annotation...
                int lastValuePosition = -1; // keep track of last value position so we can update lagging annotations
                for (ConfigAnnotation annotation : annotatedField.getAnnotations().values()) {
                    processAnnotation(annotation, null);
                    AnnotationWriter annotWriter = getAnnotation(annotation.getName());
                    int lvp = annotWriter.lastValuePosition();
                    if (lastValuePosition < lvp) {
                        lastValuePosition = lvp;
                    }
                }

                fragPos = FragmentPosition.AFTER_CLOSE_TAG;
                endWord();

                // Add empty values to all lagging annotations
                for (AnnotationWriter prop: annotatedFieldWriter.annotationWriters()) {
                    while (prop.lastValuePosition() < lastValuePosition) {
                        prop.addValue("");
                        if (prop.hasPayload())
                            prop.addPayload(null);
                    }
                }
            }
            navpop();

            // Handle any inline objects after the last word
            while (nextInlineObject != null) {
                if (nextInlineObject.type() == InlineObjectType.PUNCTUATION)
                    punctuation(nextInlineObject.getText());
                else
                    inlineTag(nextInlineObject.getText(), nextInlineObject.type() == InlineObjectType.OPEN_TAG,
                            nextInlineObject.getAttributes());
                nextInlineObject = inlineObjectsIt.hasNext() ? inlineObjectsIt.next() : null;
            }

        }
        navpop();

        // For each configured standoff annotation...
        for (ConfigStandoffAnnotations standoff : annotatedField.getStandoffAnnotations()) {
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

                for (ConfigAnnotation annotation : standoff.getAnnotations().values()) {
                    processAnnotation(annotation, tokenPositions);
                }
            }
            releaseAutoPilot(apStandoff);
            releaseAutoPilot(apTokenPos);
            navpop();
        }

        releaseAutoPilot(words);
        releaseAutoPilot(apEvalToString);
        for (AutoPilot ap : apsInlineTag) {
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

    protected void processMetadataBlock(ConfigMetadataBlock b)
            throws XPathParseException, XPathEvalException, NavException {
        // For each instance of this metadata block...
        navpush();
        AutoPilot apMetadataBlock = acquireAutoPilot(b.getContainerPath());
        while (apMetadataBlock.evalXPath() != -1) {

            // For each configured metadata field...
            List<ConfigMetadataField> fields = b.getFields();
            for (int i = 0; i < fields.size(); i++) { // NOTE: fields may be added during loop, so can't iterate
                ConfigMetadataField f = fields.get(i);

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
                        String origFieldName = apFieldName.evalXPathToString();
                        String fieldName = AnnotatedFieldNameUtil.sanitizeXmlElementName(origFieldName);
                        if (!origFieldName.equals(fieldName)) {
                            warnSanitized(origFieldName, fieldName);
                        }
                        ConfigMetadataField metadataField = b.getOrCreateField(fieldName);

                        // This metadata field is matched by a for-each, but if it specifies its own xpath ignore it in the for-each section
                        // It will capture values on its own at another point in the outer loop.
                        // Note that we check whether there is any path at all: otherwise an identical path to the for-each would capture values twice.
                        if (metadataField.getValuePath() != null && !metadataField.getValuePath().isEmpty())
                            continue;

                        apMetadata.resetXPath();

                        // Multiple matches will be indexed at the same position.
                        AutoPilot apEvalToString = acquireAutoPilot(".");
                        try {
                            while (apMetadata.evalXPath() != -1) {
                                apEvalToString.resetXPath();
                                String unprocessedValue = apEvalToString.evalXPathToString();
                                for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                                    // Also execute process defined for named metadata field, if any
                                    for (String processedValue : processStringMultipleValues(value, metadataField.getProcess(), metadataField.getMapValues())) {
                                        addMetadataField(fieldName, processedValue);
                                    }
                                }
                            }
                        } catch (XPathEvalException e) {
                            // An xpath like string(@value) will make evalXPath() fail.
                            // There is no good way to check whether this exception will occur
                            // When the exception occurs we try to evaluate the xpath as string
                            // NOTE: an xpath with dot like: string(.//tei:availability[1]/@status='free') may fail silently!!
                            if (logger.isDebugEnabled()) {
                                logger.debug(String.format("An xpath with a dot like %s may fail silently and may have to be replaced by one like %s",
                                        "string(.//tei:availability[1]/@status='free')",
                                        "string(//tei:availability[1]/@status='free')"));
                            }
                            String unprocessedValue = apMetadata.evalXPathToString();
                            for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                                for (String processedValue : processStringMultipleValues(value, metadataField.getProcess(), metadataField.getMapValues())) {
                                    addMetadataField(fieldName, processedValue);
                                }
                            }
                        }
                        releaseAutoPilot(apEvalToString);
                    }
                    releaseAutoPilot(apMetaForEach);
                    releaseAutoPilot(apFieldName);
                    navpop();
                } else {
                    // Regular metadata field; just the fieldName and an XPath expression for the value
                    // Multiple matches will be indexed at the same position.
                    AutoPilot apEvalToString = acquireAutoPilot(".");
                    try {
                        while (apMetadata.evalXPath() != -1) {
                            apEvalToString.resetXPath();
                            String unprocessedValue = apEvalToString.evalXPathToString();
                            for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                                addMetadataField(f.getName(), value);
                            }
                        }
                    } catch(XPathEvalException e) {
                        // An xpath like string(@value) will make evalXPath() fail.
                        // There is no good way to check whether this exception will occur
                        // When the exception occurs we try to evaluate the xpath as string
                        // NOTE: an xpath with dot like: string(.//tei:availability[1]/@status='free') may fail silently!!
                        if (logger.isDebugEnabled()) {
                            logger.debug(String.format("An xpath with a dot like %s may fail silently and may have to be replaced by one like %s",
                                    "string(.//tei:availability[1]/@status='free')",
                                    "string(//tei:availability[1]/@status='free')"));
                        }
                        String unprocessedValue = apMetadata.evalXPathToString();
                        for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                            addMetadataField(f.getName(), value);
                        }
                    }
                    releaseAutoPilot(apEvalToString);
                }
                releaseAutoPilot(apMetadata);
            }

        }
        releaseAutoPilot(apMetadataBlock);
        navpop();
    }

    private static Set<String> reportedSanitizedNames = new HashSet<>();

    synchronized static void warnSanitized(String origFieldName, String fieldName) {
        if (!reportedSanitizedNames.contains(origFieldName)) {
            logger.warn("Name '" + origFieldName + "' is not a valid XML element name; sanitized to '" + fieldName + "'");
            reportedSanitizedNames.add(origFieldName);
        }
    }

    /**
     * Process an annotation at the current position.
     *
     * @param annotation annotation to process
     * @param indexAtPositions if null: index at the current position; otherwise,
     *            index at all these positions
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
        try {
            String valuePath = annotation.getValuePath();
            if (valuePath == null) {
                // No valuePath given. Assume this will be captures using forEach.
                return;
            }

            // See if we want to capture any values and substitute them into the XPath
            int i = 1;
            for (String captureValuePath : annotation.getCaptureValuePaths()) {
                AutoPilot apCaptureValuePath = acquireAutoPilot(captureValuePath);
                String value = apCaptureValuePath.evalXPathToString();
                releaseAutoPilot(apCaptureValuePath);
                valuePath = valuePath.replace("$" + i, value);
                i++;
            }

            // Find matches for this annotation.
            Collection<String> annotValue = findAnnotationMatches(annotation, valuePath, indexAtPositions, null);

            // For each configured subannotation...
            Set<String> alreadySeen = new HashSet<>(); // keep track of which annotation have multiple values so we can use the correct position increment
            for (ConfigAnnotation subAnnot : annotation.getSubAnnotations()) {
                // Subannotation configs without a valuePath are just for
                // adding information about subannotations captured in forEach's,
                // such as extra processing steps
                if (subAnnot.getValuePath() == null || subAnnot.getValuePath().isEmpty())
                    continue;

                // Capture this subannotation value
                if (subAnnot.isForEach()) {
                    // "forEach" subannotation specification
                    // (allows us to capture multiple subannotations with 3 XPath expressions)
                    navpush();
                    AutoPilot apForEach = acquireAutoPilot(subAnnot.getForEachPath());
                    AutoPilot apName = acquireAutoPilot(subAnnot.getName());
                    alreadySeen.clear();
                    while (apForEach.evalXPath() != -1) {
                        // Find the name and value for this forEach match
                        apName.resetXPath();

                        String name = apName.evalXPathToString();
                        String subannotationName = annotation.getName() + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + name;
                        ConfigAnnotation actualSubAnnot = annotation.getSubAnnotation(subannotationName);
                        
                        // It's not possible to create annotation on the fly at the moment. 
                        // So since this was not declared in the config file, emit a warning and skip.
                        if (actualSubAnnot == null) {
                            if (!skippedAnnotations.contains(subannotationName)) {
                                skippedAnnotations.add(subannotationName);
                                logger.error(documentName + ": skipping undeclared annotation " + name + " (" + "as subannotation of forEachPath " + subAnnot.getName() + ")");
                            }
                            continue;
                        }

                        // The forEach xpath matched an annotation that specifies its own valuepath
                        // Skip it as part of the forEach, because it will be processed by itself later.
                        if (actualSubAnnot.getValuePath() != null && !actualSubAnnot.getValuePath().isEmpty()) {
                            continue;
                        }

                        boolean reuseAnnotationValue = subAnnot.getValuePath().equals(annotation.getValuePath()) &&
                            actualSubAnnot.isMultipleValues() == annotation.isMultipleValues() &&
                            actualSubAnnot.isAllowDuplicateValues() == annotation.isAllowDuplicateValues() &&
                            actualSubAnnot.isCaptureXml() == annotation.isCaptureXml();

                        findAnnotationMatches(actualSubAnnot, subAnnot.getValuePath(), indexAtPositions, reuseAnnotationValue ? annotValue : null);
                    }
                    releaseAutoPilot(apForEach);
                    releaseAutoPilot(apName);
                    navpop();
                } else {
                    // Regular subannotation; just the fieldName and an XPath expression for the value

                    boolean reuseParentAnnotationValue = subAnnot.getValuePath().equals(annotation.getValuePath()) &&
                        subAnnot.isMultipleValues() == annotation.isMultipleValues() &&
                        subAnnot.isAllowDuplicateValues() == annotation.isAllowDuplicateValues() &&
                        subAnnot.isCaptureXml() == annotation.isCaptureXml();

                    findAnnotationMatches(subAnnot, subAnnot.getValuePath(), indexAtPositions, reuseParentAnnotationValue ? annotValue : null);
                }
            }

        } finally {
            if (basePath != null) {
                // We pushed when we navigated to the base element; pop now.
                navpop();
            }
        }
    }

    protected AutoPilot apDot = null;
    protected Collection<String> findAnnotationMatches(ConfigAnnotation annotation, String valuePath,
            List<Integer> indexAtPositions, Collection<String> reuseValueFromParentAnnot)
            throws XPathParseException, XPathEvalException, NavException {
        boolean evalXml = annotation.isCaptureXml();

        if (reuseValueFromParentAnnot == null) {
            reuseValueFromParentAnnot = new ArrayList<String>();
            navpush();

            AutoPilot apValuePath = acquireAutoPilot(valuePath);
            if (annotation.isMultipleValues()) {
                // Multiple matches will be indexed at the same position.
                AutoPilot apValue = apDot == null ? apDot = acquireAutoPilot(".") : apDot;

                while (apValuePath.evalXPath() != -1) {
                    String unprocessedValue = evalXml ? apValue.evalXPath() != -1 ? getXml(apValue) : "" : apValue.evalXPathToString();
                    reuseValueFromParentAnnot.add(unprocessedValue);
                }

                // No annotations have been added, the result of the xPath query must have been empty.
                if (reuseValueFromParentAnnot.isEmpty()) {
                    reuseValueFromParentAnnot.add("");
                }
            } else {
                // Single value expected
                String unprocessedValue = evalXml ? apValuePath.evalXPath() != -1 ? getXml(apValuePath) : "" : apValuePath.evalXPathToString();
                reuseValueFromParentAnnot.add(unprocessedValue);
            }
            releaseAutoPilot(apValuePath);
            navpop();
        }

        // Now apply process and add to index
        if (annotation.isMultipleValues()) {

            // If duplicates are not allowed, keep track of values we've already added
            boolean duplicatesOkay = annotation.isAllowDuplicateValues();
            Set<String> valuesAlreadyAdded = duplicatesOkay ? null : new HashSet<>();

            boolean firstValue = true; // first value gets position increment 1, subsequent values 0
            for (String raw : reuseValueFromParentAnnot) {
                for (String processed : processStringMultipleValues(raw, annotation.getProcess(), null)) {
                    if (duplicatesOkay || !valuesAlreadyAdded.contains(processed)) {
                        // Not a duplicate, or we don't care about duplicates. Add it.
                        int increment = firstValue ? 1 : 0;
                        annotation(annotation.getName(), processed, increment, indexAtPositions);
                        firstValue = false;
                        if (valuesAlreadyAdded != null)
                            valuesAlreadyAdded.add(processed);
                    }
                }
            }
        } else {
            // single value (the collection should only contain one entry)
            for (String raw : reuseValueFromParentAnnot) {
                String processed = processString(raw, annotation.getProcess(), null);
                annotation(annotation.getName(), processed, 1, indexAtPositions);
                break;
            }
        }
        return reuseValueFromParentAnnot; // so subannotations can reuse it if they use the same valuePath
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
                while (documents.evalXPath() != -1) {
                    if (docDone)
                        throw new BlackLabRuntimeException(
                                "Document link " + documentXPath + " matched multiple documents in " + documentName);
                    indexDocument();
                    docDone = true;
                }
                releaseAutoPilot(documents);
            } else {
                // Process whole file; must be 1 document
                AutoPilot documents = acquireAutoPilot(config.getDocumentPath());
                while (documents.evalXPath() != -1) {
                    if (docDone)
                        throw new BlackLabRuntimeException(
                                "Linked file contains multiple documents (and no document path given) in "
                                        + documentName);
                    indexDocument();
                    docDone = true;
                }
                releaseAutoPilot(documents);
            }
        } catch (Exception e1) {
            throw BlackLabRuntimeException.wrap(e1);
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
        int startTagOffset = (int) elementFragment;
        int endTagOffset;
        long contentFragment = nav.getContentFragment();
        if (contentFragment == -1) {
            // Empty (self-closing) element.
            endTagOffset = startTagOffset;
        } else {
            // Regular element with separate open and close tags.
            int contentOffset = (int) contentFragment;
            int contentLength = (int) (contentFragment >> 32);
            int contentEnd = contentOffset + contentLength;
            endTagOffset = contentEnd;
        }

        // Find element name
        int currentIndex = nav.getCurrentIndex();
        String elementName = dedupe(nav.toString(currentIndex));

        // Add the inline tags to the list
        InlineObject openTag = new InlineObject(elementName, startTagOffset, InlineObjectType.OPEN_TAG,
                getAttributes());
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
            throw BlackLabRuntimeException.wrap(e);
        }
        navpop();
        return attr;
    }

    @Override
    protected void startDocument() {
        super.startDocument();

        try {
            long fragment = nav.getElementFragment();
            documentByteOffset = (int) fragment;
            documentLengthBytes = (int) (fragment >> 32);
        } catch (NavException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        lastCharPosition = 0;
        lastCharPositionByteOffset = documentByteOffset;
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(inputDocument, documentByteOffset, documentLengthBytes, StandardCharsets.UTF_8);
    }

    @Override
    protected int getCharacterPosition() {
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
                String str = new String(inputDocument, lastCharPositionByteOffset, length, StandardCharsets.UTF_8);
                lastCharPosition += str.length();
                lastCharPositionByteOffset = currentByteOffset;
            }
            return lastCharPosition;
        } catch (NavException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    protected int getCurrentByteOffset() throws NavException {
        if (fragPos == FragmentPosition.BEFORE_OPEN_TAG || fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
            long elFrag = nav.getElementFragment();
            int elOffset = (int) elFrag;
            if (fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
                int elLength = (int) (elFrag >> 32);
                return elOffset + elLength;
            }
            return elOffset;
        }
        long contFrag = nav.getContentFragment();
        int contOffset = (int) contFrag;
        if (fragPos == FragmentPosition.BEFORE_CLOSE_TAG) {
            int contLength = (int) (contFrag >> 32);
            return contOffset + contLength;
        }
        return contOffset;
    }

    protected void setCurrentAnnotatedField(ConfigAnnotatedField annotatedField) {
        currentAnnotatedFieldConfig = annotatedField;
        setCurrentAnnotatedFieldName(currentAnnotatedFieldConfig.getName());
    }

    /** Get the raw xml from the document at the current position
     * @throws NavException */
    private static String getXml(AutoPilot ap) throws NavException {
        long frag = ap.getNav().getContentFragment();
        if (frag == -1) {
            return "";
        }

        int offset = (int) frag;
        int length = (int) (frag >> 32);

        return ap.getNav().toRawString(offset, length);
    }
}
