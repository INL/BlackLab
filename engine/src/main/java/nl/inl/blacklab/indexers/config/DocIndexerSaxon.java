package nl.inl.blacklab.indexers.config;

import java.io.ByteArrayInputStream;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
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

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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

/**
 * An indexer capable of XPath version supported by the provided saxon library.
 */
public class DocIndexerSaxon extends DocIndexerXPath<NodeInfo> {

    public static final int INITIAL_LIST_SIZE_INLINE_TAGS = 500;

    public static final int INITIAL_CAPACITY_PER_WORD_COLLECTIONS = 3;

    /** How we collect inline tags and (optionally) their token ids (for standoff annotations) */
    private static class InlineInfo implements Comparable<InlineInfo> {

        private NodeInfo nodeInfo;

        private String tokenId;

        public InlineInfo(NodeInfo nodeInfo, String tokenId) {
            this.nodeInfo = nodeInfo;
            this.tokenId = tokenId;
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

    @Override
    public void setDocument(File file, Charset defaultCharset) {
        setDocument(file, defaultCharset, null);
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        try {
            char[] charContents = IOUtils.toCharArray(new ByteArrayInputStream(contents), defaultCharset);
            setDocument(null, null, charContents);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        try {
            setDocument(null, null, IOUtils.toCharArray(is, defaultCharset));
            is.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            setDocument(null, null, IOUtils.toCharArray(reader));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private void setDocument(File file, Charset defaultCharset, char[] documentContent) {
        try {
            if (documentContent == null)
                documentContent = IOUtils.toCharArray(new FileReader(file, defaultCharset));
            charPositions = new CharPositionsTracker(documentContent);
            contents = SaxonHelper.parseDocument(
                    new CharArrayReader(documentContent), new MyContentHandler(charPositions));
            finder = new XPathFinder(SaxonHelper.getXPathFactory(),
                    config.isNamespaceAware() ? config.getNamespaces() : null);
            document = new DocumentReference(documentContent, file);
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
        indexParsedFile(config.getDocumentPath(), false);
    }

    // process annotated field

    protected void processAnnotatedFieldContainer(NodeInfo container, ConfigAnnotatedField annotatedField,
            Map<String, Integer> tokenPositionsMap) {

        // Collect information outside word tags:

        // - Punctuation may occur between word tags, which we want to capture
        Iterator<NodeInfo> punctIt = collectPunctuation(container, annotatedField).iterator();
        NodeInfo currentPunct = punctIt.hasNext() ? punctIt.next() : null;

        // - "inline tags" (e.g. b, i, named-entity) can occur between words
        Iterator<InlineInfo> inlineIt = collectInlineTags(container, annotatedField).iterator();
        InlineInfo currentInline = inlineIt.hasNext() ? inlineIt.next() : null;

        // Keep track of where we need to close inline tags we've opened.
        Map<Integer, List<NodeInfo>> inlinesToClose = new HashMap<>();

        // For each word...
        int wordNumber = 0;
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
                handleInlineOpenTag(annotatedField, inlinesToClose, currentInline, wordNumber, word, tokenPositionsMap);
                currentInline = inlineIt.hasNext() ? inlineIt.next() : null;
            }

            // Index our word
            charPos = charPositions.getNodeStartPos(word);
            beginWord();

            // For each configured annotation...
            for (ConfigAnnotation annotation: annotatedField.getAnnotationsFlattened().values()) {
                processAnnotation(annotation, word, wordNumber, -1);
            }

            charPos = charPositions.getNodeEndPos(word);
            endWord();

            // Make sure we close inline tags at the correct position
            List<NodeInfo> closeHere = inlinesToClose.getOrDefault(wordNumber, Collections.emptyList());
            for (int i = closeHere.size() - 1; i >= 0; i--) {
                NodeInfo inlineTag = closeHere.get(i);
                inlineTag(inlineTag.getDisplayName(),false,null);
            }
            inlinesToClose.remove(wordNumber);

            // Capture token id if needed (for standoff annotations)
            if (annotatedField.getTokenIdPath() != null) {
                String tokenId = xpathValue(annotatedField.getTokenIdPath(), word);
                if (tokenId != null)
                    tokenPositionsMap.put(tokenId, wordNumber);
            }

            wordNumber++;
        }
        if (!inlinesToClose.isEmpty()) {
            throw new BlackLabRuntimeException(String.format("unclosed inlines left: %s ", inlinesToClose.values()));
        }
        // Index any punctuation occurring after last word
        while (currentPunct != null) {
            handlePunct(currentPunct);
            currentPunct = punctIt.hasNext() ? punctIt.next() : null;
        }

        // Process standoff annotations
        for (ConfigStandoffAnnotations standoff: annotatedField.getStandoffAnnotations()) {
            processStandoffAnnotation(standoff, container, tokenPositionsMap);
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
                inlines.add(new InlineInfo(tag, tokenId));
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

    private void handleInlineOpenTag(ConfigAnnotatedField annotatedField, Map<Integer, List<NodeInfo>> inlinesToClose,
            InlineInfo currentInline, int wordNumber, NodeInfo word, Map<String, Integer> tokenPositionsMap) {
    /*
    - index open tag
    - remember after which word the close tag occurs
    - index word(s)
    - index closing tags(s) at the right position
     */

        // Check if this word is within the inline, if so this word will always be the first word in
        // the inline because we only process each inline once.
        NodeInfo nodeInfo = currentInline.getNodeInfo();
        AxisIterator descendants = nodeInfo.iterateAxis(Axis.DESCENDANT.getAxisNumber());
        boolean isDescendant = false;
        NodeInfo next;
        while ((next = descendants.next()) != null) {
            if (next.equals(word)) {
                isDescendant = true;
                break;
            }
        }
        if (isDescendant) {
            // Yes, word is a descendant.   (i.e. not a self-closing inline tag?)
            // Find the attributes and index the tag.
            Map<String, String> atts = new HashMap<>(INITIAL_CAPACITY_PER_WORD_COLLECTIONS);
            AxisIterator attributes = nodeInfo.iterateAxis(Axis.ATTRIBUTE.getAxisNumber());
            while ((next = attributes.next()) != null) {
                atts.put(next.getDisplayName(),next.getStringValue());
            }
            inlineTag(nodeInfo.getDisplayName(), true, atts);

            // Add tag to the list of tags to close at the correct position.
            // (calculate word position by determining the number of word tags inside this element)
            String xpNumberOfWordsInsideTag = "count(" + annotatedField.getWordsPath() + ")";
            int increment = Integer.parseInt(xpathValue(xpNumberOfWordsInsideTag, nodeInfo)) - 1;
            inlinesToClose.computeIfAbsent(wordNumber + increment,
                            k -> new ArrayList<>(INITIAL_CAPACITY_PER_WORD_COLLECTIONS))
                    .add(nodeInfo);
        }

        if (currentInline.getTokenId() != null)
            tokenPositionsMap.put(currentInline.getTokenId(), wordNumber);
    }


    //---- PROCESS ANNOTATION

    /**
     * Process an annotation at the current position.
     *
     * If this is a span annotation (spanEndPos >= 0), and the span looks like this:
     * <code>&lt;named-entity type="person"&gt;Santa Claus&lt;/named-entity&gt;</code>,
     * then spanName should be "named-entity" and annotation name should be "type" (and
     * its XPath expression should evaluate to "person", obviously).
     *
     * @param annotation   annotation to process.
     * @param position     position to index at
     * @param spanEndPos   if >= 0, index as a span annotation with this end position (exclusive)
     * @param handler      call handler for each value found, including that of subannotations
     */
    protected void processAnnotation(ConfigAnnotation annotation, NodeInfo word, int position, int spanEndPos,
            AnnotationHandler handler) {
        if (StringUtils.isEmpty(annotation.getValuePath()))
            return; // assume this will be captured using forEach

        if (annotation.getBasePath() != null) {
            for (NodeInfo baseNode: finder.findNodes(annotation.getBasePath(), word)) {
                processAnnotationWithinBasePath(annotation, baseNode, position, spanEndPos, handler);
            }
        } else {
            processAnnotationWithinBasePath(annotation, word, position, spanEndPos, handler);
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(document.get());
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
