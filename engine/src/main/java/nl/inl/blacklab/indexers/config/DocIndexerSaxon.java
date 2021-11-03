package nl.inl.blacklab.indexers.config;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.xml.sax.SAXException;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * An indexer capable of XPath version supported by the provided saxon library.
 */
public class DocIndexerSaxon extends DocIndexerConfig {

    private SaxonHelper saxonHelper;
    private TreeInfo contents;

    @Override
    public void setDocument(Reader reader) {
        try {
            saxonHelper = new SaxonHelper(reader, config);
            contents = saxonHelper.getContents();
        } catch (IOException | XPathException | SAXException | ParserConfigurationException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        try {
            for (NodeInfo doc : saxonHelper.findNodes(config.getDocumentPath(),contents)) {
                indexDocument(doc);
            }
        } catch (XPathExpressionException e) {
            throw new InvalidConfiguration(e.getMessage() + String.format("; when indexing file: %s", documentName), e);
        }
    }

    /**
     * Index document from the current node.
     */
    protected void indexDocument(NodeInfo doc) throws XPathExpressionException {

        startDocument();

        // For each configured annotated field...
        for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
            if (!annotatedField.isDummyForStoringLinkedDocuments())
                processAnnotatedField(doc,annotatedField);
        }

        // For each configured metadata block..
        for (ConfigMetadataBlock b : config.getMetadataBlocks()) {
            processMetadataBlock(doc, b);
        }

        // For each linked document...
        for (ConfigLinkedDocument ld : config.getLinkedDocuments().values()) {
            Function<String, String> xpathProcessor = xpath -> {
                try {
                    return saxonHelper.getValue(xpath,doc);
                } catch (XPathExpressionException e) {
                    throw new InvalidConfiguration(e.getMessage() + String.format("; when indexing file: %s", documentName), e);
                }
            };
            processLinkedDocument(ld, xpathProcessor);
        }

        endDocument();
    }


    protected void processAnnotatedField(NodeInfo doc, ConfigAnnotatedField annotatedField)
            throws XPathExpressionException {
        setCurrentAnnotatedFieldName(annotatedField.getName());

        /*
        Woorden kunnen in "inline tags zitten"

        Tussen woorden kunnen leestekens zitten

        Verzamelen en plaatsen m.b.v. NodeInfo#compareOrder

         */

        List<NodeInfo> puncts = saxonHelper.findNodes(annotatedField.getPunctPath(), doc);

        List<NodeInfo> inlines = new ArrayList<>(500);

        for (ConfigInlineTag inl : annotatedField.getInlineTags()) {
            inlines.addAll(saxonHelper.findNodes(inl.getPath(),doc));
        }
        SaxonHelper.documentOrder(puncts);
        SaxonHelper.documentOrder(inlines);

        for (NodeInfo container : saxonHelper.findNodes(annotatedField.getContainerPath(),doc)) {
            int wNum=0;
            Map<Integer,List<NodeInfo>> inlinesToEnd = new HashMap<>();
            for (NodeInfo word : saxonHelper.findNodes(annotatedField.getWordsPath(),container)) {
                wNum++;
                /*
                hier gaan we kijken of er inline tags of leestekens voor dit woord zitten
                - indexeren opentag
                - onthouden na welk word gesloten moet worden
                - verwijderen uit lijst met inlines en leestekens
                - indexeren word(s)
                - indexeren sluittag(s) na het juiste woord

                 */
                List<NodeInfo> precedingPuncts = new ArrayList<>(3); // hierin verzamelen we puncts zodat we SNEL weten of een NodeInfo een punct is
                List<NodeInfo> preceding = getPreceding(puncts, inlines, word, precedingPuncts);

                for (NodeInfo punctOrInline : preceding) {
                    if (precedingPuncts.contains(punctOrInline)) {
                        String punct = punctOrInline.getStringValue();
                        punctuation(punct==null||punct.isEmpty()?" ":punct);
                        if (!puncts.remove(punctOrInline)) {
                            throw new BlackLabRuntimeException(String.format("punct not deleted %s",punctOrInline.toShortString()));
                        }
                    } else {
                        // check if this word is within the inline, if so this word will always be the first word in the inline
                        // because we remove the inline after it has been processed
                        AxisIterator descendants = punctOrInline.iterateAxis(Axis.DESCENDANT.getAxisNumber());
                        boolean isDescendant = false;
                        NodeInfo next;
                        while ((next = descendants.next()) != null) {
                            if (next.equals(word)) {
                                isDescendant = true;
                                break;
                            }
                        }
                        if (isDescendant) {
                            Map<String,String> atts = new HashMap<>(3);
                            AxisIterator attributes = punctOrInline.iterateAxis(Axis.ATTRIBUTE.getAxisNumber());
                            while ((next = attributes.next()) != null) {
                                atts.put(next.getDisplayName(),next.getStringValue());
                            }
                            inlineTag(punctOrInline.getDisplayName(), true, atts);
                            int increment =
                                    Integer.parseInt(saxonHelper
                                            .getValue("count("+annotatedField.getWordsPath()+")",punctOrInline))
                                            -1;
                            if (inlinesToEnd.containsKey(wNum+increment)) {
                                inlinesToEnd.get(wNum+increment).add(0,punctOrInline);
                            } else {
                                List<NodeInfo> toEnd =  new ArrayList<>(3);
                                toEnd.add(punctOrInline);
                                inlinesToEnd.put(wNum+increment, toEnd);
                            }
//                            System.out.println("inline "+punctOrInline.toShortString()+" for " +wNum + " should close after " + (wNum+increment));
                        }
                        if (!inlines.remove(punctOrInline)) {
                            throw new BlackLabRuntimeException(String.format("not deleted %s",punctOrInline.toShortString()));
                        }
                    }

                }
                charPos = saxonHelper.getStartPos(word);
                beginWord();
                for (Map.Entry<String, ConfigAnnotation> an : annotatedField.getAnnotations().entrySet()) {
                    ConfigAnnotation annotation = an.getValue();
                    // TODO we may need to support multiple values here
                    String value = saxonHelper.getValue(annotation.getValuePath(),word);
                    annotation(annotation.getName(),value,1,null);
                }
                charPos = saxonHelper.getEndPos(word);
                endWord();
//                System.out.println("looking for inlines to close after " +wNum);
                if (inlinesToEnd.containsKey(wNum)) {
                    for (NodeInfo toEnd : inlinesToEnd.get(wNum)) {
//                        System.out.println("closing inline "+toEnd.toShortString()+" after " +wNum);
                        inlineTag(toEnd.getDisplayName(),false,null);
                    }
                    inlinesToEnd.remove(wNum);
                }
            }
            if (!inlinesToEnd.isEmpty()) {
                throw new BlackLabRuntimeException(String.format("unclosed inlines left: %s ", inlinesToEnd.values()));
            }

        }
    }

    /**
     * return punctuations and inlines occurring before a word
     * @param puncts
     * @param inlines
     * @param word
     * @return
     */
    private static List<NodeInfo> getPreceding(List<NodeInfo> puncts, List<NodeInfo> inlines, NodeInfo word, List<NodeInfo> precedingPuncts) {
        List<NodeInfo> preceding = new ArrayList<>();
        for (NodeInfo pi : puncts) {
            if (word!=null&&word.compareOrder(pi)!=1) {
                break;
            }
            preceding.add(pi);
            precedingPuncts.add(pi);
        }
        for (NodeInfo pi : inlines) {
            if (word!=null&&word.compareOrder(pi)!=1) {
                break;
            }
            preceding.add(pi);
        }
        SaxonHelper.documentOrder(preceding);
        return preceding;
    }


    protected void processMetadataBlock(NodeInfo doc, ConfigMetadataBlock b) throws XPathExpressionException {

        for (NodeInfo header : saxonHelper.findNodes(b.getContainerPath(),doc)) {
            List<ConfigMetadataField> fields = b.getFields();
            for (int i = 0; i < fields.size(); i++) { // NOTE: fields may be added during loop, so can't iterate
                ConfigMetadataField f = fields.get(i);

                // Metadata field configs without a valuePath are just for
                // adding information about fields captured in forEach's,
                // such as extra processing steps
                if (f.getValuePath() == null || f.getValuePath().isEmpty())
                    continue;

                // Capture whatever this configured metadata field points to
                if (f.isForEach()) {
                    // "forEach" metadata specification
                    // (allows us to capture many metadata fields with 3 XPath expressions)
                    for (NodeInfo forEach : saxonHelper.findNodes(f.getForEachPath(),header)) {
                        // Find the fieldName and value for this forEach match
                        String origFieldName = saxonHelper.getValue(f.getName(),forEach);
                        String fieldName = AnnotatedFieldNameUtil.sanitizeXmlElementName(origFieldName);
                        if (!origFieldName.equals(fieldName)) {
                            DocIndexerXPath.warnSanitized(origFieldName, fieldName);
                        }
                        b.getOrCreateField(fieldName);

                        // Multiple matches will be indexed at the same position.
                        processValue(forEach, f);
                    }
                } else {
                    // Regular metadata field; just the fieldName and an XPath expression for the value
                    // Multiple matches will be indexed at the same position.
                    processValue(header, f);
                }
            }
        }

    }

    private void processValue(NodeInfo header, ConfigMetadataField f) throws XPathExpressionException {
        for (Object val : saxonHelper.find(f.getValuePath(),header)) {
            if (val instanceof NodeInfo) {
                String unprocessedValue = saxonHelper.getValue(".", val);
                for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                    addMetadataField(f.getName(), value);
                }
            } else {
                String metadataValue = processString(String.valueOf(val), f.getProcess(), f.getMapValues());
                addMetadataField(f.getName(), metadataValue);

            }
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(saxonHelper.getDocument(true));
    }

    @Override
    public void close() {
        // NOP
    }

    private int charPos = 0;

    @Override
    protected int getCharacterPosition() {
        return charPos;
    }
}
