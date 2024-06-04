package nl.inl.blacklab.indexers.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.xml.sax.SAXException;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.saxon.CharPositionsTracker;
import nl.inl.blacklab.indexers.config.saxon.DocumentReference;
import nl.inl.blacklab.indexers.config.saxon.MyContentHandler;
import nl.inl.blacklab.indexers.config.saxon.SaxonHelper;
import nl.inl.blacklab.indexers.config.saxon.XPathFinder;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * An indexer capable of XPath version supported by the provided saxon library.
 */
public class DocIndexerSaxon extends DocIndexerXPath<NodeInfo> {

    public static final int INITIAL_LIST_SIZE_INLINE_TAGS = 500;

    public static final int INITIAL_CAPACITY_PER_WORD_COLLECTIONS = 3;

    /** How we collect inline tags and (optionally) their token ids (for standoff annotations) */
    private static class InlineInfo implements Comparable<InlineInfo> {

        private final NodeInfo nodeInfo;

        private final String tokenId;

        private final ConfigInlineTag config;

        public InlineInfo(NodeInfo nodeInfo, String tokenId, ConfigInlineTag config) {
            this.nodeInfo = nodeInfo;
            this.tokenId = tokenId;
            this.config = config;
        }

        @Override
        public int compareTo(InlineInfo o) {
            return nodeInfo.compareOrder(o.nodeInfo);
        }

        public NodeInfo getNodeInfo() {
            return nodeInfo;
        }

        public String getTokenId() {
            return tokenId;
        }

        public int compareOrder(NodeInfo word) {
            return nodeInfo.compareOrder(word);
        }

        public boolean indexAttribute(String name) {
            if (config.getIncludeAttributes() != null) {
                // If includeAttributes is set, attribute must be in the list and excludeAttributes is ignored.
                return config.getIncludeAttributes().contains(name);
            }
            // Otherwise, index all attributes not in excludeAttributes.
            return !config.getExcludeAttributes().contains(name);
        }
    }

    /** Our document, possibly swapped to disk. */
    private DocumentReference document;

    /** The parsed document. */
    private TreeInfo contents;

    /** Can calculate character position for a given line/column position. */
    private CharPositionsTracker charPositions;

    /** Current character position in the document */
    private int charPos = 0;

    /** XPath util functions and caching of XPathExpressions */
    private XPathFinder finder;

    /** Directory from which to resolve relative XIncludes. */
    private File currentXIncludeDir = new File(".");

    @Override
    public void setDocumentDirectory(File dir) {
        this.currentXIncludeDir = dir.getAbsoluteFile();
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) {
        setDocument(file, defaultCharset, null);
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        try {
            char[] charContents = IOUtils.toCharArray(new ByteArrayInputStream(contents), defaultCharset);
            setDocument(null, StandardCharsets.UTF_8, charContents);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        try {
            setDocument(null, StandardCharsets.UTF_8, IOUtils.toCharArray(is, defaultCharset));
            is.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            setDocument(null, StandardCharsets.UTF_8, IOUtils.toCharArray(reader));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private void setDocument(File file, Charset charset, char[] documentContent) {
        assert file == null || charset != null; // if file is set, we also need a charset
        document = new DocumentReference(documentContent, charset, file, false);
    }

    private void readDocument() {
        try {
            document = document.withXIncludesResolved(currentXIncludeDir);
            charPositions = document.getCharPositionsTracker();
            try (Reader reader = document.getDocumentReader()) {
                contents = SaxonHelper.parseDocument(reader, new MyContentHandler(charPositions));
            }
            XPath xPath = SaxonHelper.getXPathFactory().newXPath();
            finder = new XPathFinder(xPath,
                    config.isNamespaceAware() ? config.getNamespaces() : null);
        } catch (IOException | XPathException | SAXException | ParserConfigurationException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    protected void xpathForEach(String xPath, NodeInfo context, NodeHandler<NodeInfo> handler) {
        finder.xpathForEach(xPath, context, handler);
    }

    @Override
    protected void xpathForEachStringValue(String xPath, NodeInfo context, StringValueHandler handler) {
        finder.xpathForEachStringValue(xPath, context, handler);
    }

    @Override
    protected String xpathValue(String xpath, NodeInfo context) {
        return finder.xpathValue(xpath, context);
    }

    @Override
    protected String xpathXml(String xpath, NodeInfo context) {
        return finder.xpathXml(xpath, context);
    }

    @Override
    protected String currentNodeXml(NodeInfo node) {
        return finder.currentNodeXml(node);
    }

    @Override
    protected NodeInfo contextNodeWholeDocument() {
        return contents.getRootNode();
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();
        readDocument();
        indexParsedFile(config.getDocumentPath(), false);
    }

    // process annotated field

    Map<ConfigAnnotatedField, Pair<Integer, Integer>> docStartEndOffsetsPerField = new HashMap<>();

    @Override
    protected void startDocument() {
        super.startDocument();
        docStartEndOffsetsPerField.clear();
    }

    protected void processAnnotatedFieldContainer(NodeInfo container, ConfigAnnotatedField annotatedField,
            Map<String, Span> tokenPositionsMap) {

        // Is this a parallel corpus annotated field?
        if (AnnotatedFieldNameUtil.isParallelField(annotatedField.getName())) {
            // Yes; determine boundaries of this annotated field container so we can later store
            // this version of the document in the field's content store.
            // (so we can retrieve only the desired version of the document later, e.g. only the Dutch version)
            int start = charPositions.getNodeStartPos(container);
            int end = charPositions.getNodeEndPos(container);
            docStartEndOffsetsPerField.put(annotatedField, Pair.of(start, end));
        }

        // Collect information outside word tags:

        // - Punctuation may occur between word tags, which we want to capture
        Iterator<NodeInfo> punctIt = collectPunctuation(container, annotatedField).iterator();
        NodeInfo currentPunct = punctIt.hasNext() ? punctIt.next() : null;

        // - "inline tags" (e.g. b, i, named-entity) can occur between words
        Iterator<InlineInfo> inlineIt = collectInlineTags(container, annotatedField).iterator();
        InlineInfo currentInline = inlineIt.hasNext() ? inlineIt.next() : null;

        // Keep track of where we need to close inline tags we've opened.
        Map<Span, List<NodeInfo>> inlinesToClose = new HashMap<>();

        // For each word...
        Span tokenPosition = Span.token(0);
        List<NodeInfo> words = finder.findNodes(annotatedField.getWordsPath(), container);
        words.sort(NodeInfo::compareOrder); // (or does Saxon guarantee that matching nodes are already in order? maybe check)
        for (NodeInfo word: words) {
            // Index any punctuation occurring before this word
            while (currentPunct != null) {
                if (currentPunct.compareOrder(word) != -1)
                    break; // follows word, we'll index it later
                handlePunct(currentPunct);
                currentPunct = punctIt.hasNext() ? punctIt.next() : null;
            }

            // Index any inline open tags occurring before this word
            while (currentInline != null) {
                if (currentInline.compareOrder(word) != -1)
                    break; // follows word, we'll index it later
                handleInlineOpenTag(annotatedField, inlinesToClose, currentInline, tokenPosition, word, tokenPositionsMap);
                currentInline = inlineIt.hasNext() ? inlineIt.next() : null;
            }

            // Index our word
            charPos = charPositions.getNodeStartPos(word);
            beginWord();

            // For each configured annotation...
            for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                processAnnotation(annotation, word, tokenPosition);
            }

            charPos = charPositions.getNodeEndPos(word);
            endWord();

            // Make sure we close inline tags at the correct position
            List<NodeInfo> closeHere = inlinesToClose.getOrDefault(tokenPosition, Collections.emptyList());
            for (int i = closeHere.size() - 1; i >= 0; i--) {
                NodeInfo inlineTag = closeHere.get(i);
                inlineTag(inlineTag.getDisplayName(), false, null);
            }
            inlinesToClose.remove(tokenPosition);

            // Capture token id if needed (for standoff annotations)
            if (annotatedField.getTokenIdPath() != null) {
                String tokenId = xpathValue(annotatedField.getTokenIdPath(), word);
                if (tokenId != null)
                    tokenPositionsMap.put(tokenId, tokenPosition.copy());
            }

            tokenPosition.increment();
        }
        if (!inlinesToClose.isEmpty()) {
            throw new BlackLabRuntimeException(String.format("unclosed inlines left: %s ", inlinesToClose.values()));
        }
        // Index any punctuation occurring after last word
        while (currentPunct != null) {
            handlePunct(currentPunct);
            currentPunct = punctIt.hasNext() ? punctIt.next() : null;
        }
    }

    private List<NodeInfo> collectPunctuation(NodeInfo container, ConfigAnnotatedField annotatedField) {
        setAddDefaultPunctuation(true);
        if (annotatedField.getPunctPath() != null) {
            // We have punctuation occurring between word tags (as opposed to
            // punctuation that is tagged as a word itself). Collect this punctuation.
            setAddDefaultPunctuation(false);
            List<NodeInfo> puncts = finder.findNodes(annotatedField.getPunctPath(), container);
            puncts.sort(NodeInfo::compareOrder);
            return puncts;
        }
        return Collections.emptyList();
    }

    private List<InlineInfo> collectInlineTags(NodeInfo container, ConfigAnnotatedField annotatedField) {
        List<InlineInfo> inlines = new ArrayList<>(INITIAL_LIST_SIZE_INLINE_TAGS);
        for (ConfigInlineTag inlineTag: annotatedField.getInlineTags()) {
            String tokenIdXPath = inlineTag.getTokenIdPath();
            xpathForEach(inlineTag.getPath(), container, (tag) -> {
                String tokenId = tokenIdXPath == null ? null : xpathValue(tokenIdXPath, tag);
                inlines.add(new InlineInfo(tag, tokenId, inlineTag));
            });
        }
        Collections.sort(inlines);
        return inlines;
    }

    private void handlePunct(NodeInfo currentPunct) {
        // Punct precedes word
        String punct = currentPunct.getStringValue();
        punctuation(punct == null ? " " : punct);
    }

    private void handleInlineOpenTag(ConfigAnnotatedField annotatedField, Map<Span, List<NodeInfo>> inlinesToClose,
            InlineInfo currentInline, Span position, NodeInfo word, Map<String, Span> tokenPositionsMap) {
        /*
        - index open tag
        - remember after which word the close tag occurs
        - index word(s)
        - index closing tags(s) at the right position
         */

        // Check if this word is within the inline, if so this word will always be the first word in
        // the inline because we only process each inline once.
        NodeInfo nodeInfo = currentInline.getNodeInfo();
        boolean isDescendant = false;
        NodeInfo next;
        try (AxisIterator descendants = nodeInfo.iterateAxis(Axis.DESCENDANT.getAxisNumber())) {
            while ((next = descendants.next()) != null) {
                if (next.equals(word)) {
                    isDescendant = true;
                    break;
                }
            }
        }
        int firstWordOutsideInline;
        if (isDescendant) {
            // Yes, word is a descendant.   (i.e. not a self-closing inline tag?)
            // Find the attributes and index the tag.
            Map<String, String> atts = new HashMap<>(INITIAL_CAPACITY_PER_WORD_COLLECTIONS);
            try (AxisIterator attributes = nodeInfo.iterateAxis(Axis.ATTRIBUTE.getAxisNumber())) {
                while ((next = attributes.next()) != null) {
                    if (currentInline.indexAttribute(next.getDisplayName())) {
                        atts.put(next.getLocalPart(), next.getStringValue());
                    }
                }
            }
            // Index any extra attributes using the provided XPath expressions.
            for (ConfigInlineTag.ConfigExtraAttribute extraAttribute: currentInline.config.getExtraAttributes()) {
                String value = xpathValue(extraAttribute.getValuePath(), nodeInfo);
                if (value != null)
                    atts.put(extraAttribute.getName(), value);
            }
            inlineTag(nodeInfo.getDisplayName(), true, atts);

            // Add tag to the list of tags to close at the correct position.
            // (calculate word position by determining the number of word tags inside this element)
            String xpNumberOfWordsInsideTag = "count(" + annotatedField.getWordsPath() + ")";
            int numberOfWordsInsideTag = Integer.parseInt(xpathValue(xpNumberOfWordsInsideTag, nodeInfo));
            // close inline after the last word that's contained in it (position + numberOfWordsInsideTag - 1)
            inlinesToClose.computeIfAbsent(position.plus(numberOfWordsInsideTag - 1),
                            k -> new ArrayList<>(INITIAL_CAPACITY_PER_WORD_COLLECTIONS))
                    .add(nodeInfo);
            firstWordOutsideInline = position.start() + numberOfWordsInsideTag;
        } else {
            // Word is not a descendant, so this inline must be self-closing.
            // In other words, the length of the inline is 0.
            firstWordOutsideInline = position.start();
        }

        if (currentInline.getTokenId() != null)
            tokenPositionsMap.put(currentInline.getTokenId(), Span.between(position.start(), firstWordOutsideInline));
    }


    //---- PROCESS ANNOTATION

    /**
     * Process an annotation at the current position.
     * <p>
     * If this is a span annotation (spanEndPos >= 0), and the span looks like this:
     * <code>&lt;named-entity type="person"&gt;Santa Claus&lt;/named-entity&gt;</code>,
     * then spanName should be "named-entity" and annotation name should be "type" (and
     * its XPath expression should evaluate to "person", obviously).
     *
     * @param annotation   annotation to process.
     * @param positionSpanEndOrSource     position to index at
     * @param spanEndOrRelTarget   if >= 0, index as a span annotation with this end position (exclusive)
     * @param handler      call handler for each value found, including that of subannotations
     */
    protected void processAnnotation(ConfigAnnotation annotation, NodeInfo word,
            Span positionSpanEndOrSource, Span spanEndOrRelTarget,
            AnnotationHandler handler) {
        if (StringUtils.isEmpty(annotation.getValuePath()))
            return; // assume this will be captured using forEach

        if (annotation.getBasePath() != null) {
            for (NodeInfo baseNode: finder.findNodes(annotation.getBasePath(), word)) {
                processAnnotationWithinBasePath(annotation, baseNode, positionSpanEndOrSource, spanEndOrRelTarget, handler);
            }
        } else {
            processAnnotationWithinBasePath(annotation, word, positionSpanEndOrSource, spanEndOrRelTarget, handler);
        }
    }

    @Override
    protected void storeDocument() {
        if (docStartEndOffsetsPerField.isEmpty()) {
            storeWholeDocument(document.getTextContent());
        } else {
            for (Map.Entry<ConfigAnnotatedField, Pair<Integer, Integer>> entry: docStartEndOffsetsPerField.entrySet()) {
                Integer startOffset = entry.getValue().getLeft();
                Integer endOffset = entry.getValue().getRight();
                storeContent(entry.getKey(), document.getTextContent(startOffset, endOffset));
            }
        }
        document.clean();
        document = null;
        // make sure we don't hold on to memory needlessly
        charPositions = null;
        contents = null;
    }

    @Override
    protected int getCharacterPosition() {
        return charPos;
    }

}
