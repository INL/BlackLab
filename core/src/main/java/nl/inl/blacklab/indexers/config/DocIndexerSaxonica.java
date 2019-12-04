package nl.inl.blacklab.indexers.config;

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
import org.apache.commons.lang3.NotImplementedException;
import org.xml.sax.SAXException;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * An indexer capable of XPath version supported by the provided saxonica library.
 */
public class DocIndexerSaxonica extends DocIndexerConfig {

    private SaxonicaHelper saxonicaHelper;
    private TreeInfo contents;

    @Override
    public void setDocument(Reader reader) {
        try {
            saxonicaHelper = new SaxonicaHelper(reader, config);
            contents = saxonicaHelper.getContents();
        } catch (IOException | XPathException | SAXException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        try {
            for (NodeInfo doc : saxonicaHelper.findNodes(config.getDocumentPath(),contents)) {
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
            processLinkedDocument(doc, ld);
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

        List<NodeInfo> puncts = saxonicaHelper.findNodes(annotatedField.getPunctPath(), doc);

        List<NodeInfo> inlines = new ArrayList<>(500);
        for (ConfigInlineTag inl : annotatedField.getInlineTags()) {
            inlines.addAll(saxonicaHelper.findNodes(inl.getPath(),doc));
        }

        for (NodeInfo container : saxonicaHelper.findNodes(annotatedField.getContainerPath(),doc)) {
            int wNum=0;
            Map<Integer,List<NodeInfo>> inlinesToEnd = new HashMap<>();
            for (NodeInfo word : saxonicaHelper.findNodes(annotatedField.getWordsPath(),container)) {
                wNum++;
                /*
                hier gaan we kijken of er inline tags of leestekens voor dit woord zitten
                - indexeren opentag
                - onthouden na welk word gesloten moet worden
                - verwijderen uit lijst met inlines en leestekens
                - indexeren word(s)
                - inderen sluittag na het juiste woord

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
                            int count = Integer.parseInt(saxonicaHelper.getValue("count("+annotatedField.getWordsPath()+")",punctOrInline));
                            Map<String,String> atts = new HashMap<>(3);
                            AxisIterator attributes = punctOrInline.iterateAxis(Axis.ATTRIBUTE.getAxisNumber());
                            while ((next = attributes.next()) != null) {
                                atts.put(next.getDisplayName(),next.getStringValue());
                            }
                            inlineTag(punctOrInline.getDisplayName(), true, atts);
                            if (inlinesToEnd.containsKey(wNum+count)) {
                                inlinesToEnd.get(wNum+count).add(punctOrInline);
                            } else {
                                List<NodeInfo> toEnd =  new ArrayList<>(3);
                                toEnd.add(punctOrInline);
                                inlinesToEnd.put(wNum+count, toEnd);
                            }
                        }
                        if (!inlines.remove(punctOrInline)) {
                            throw new BlackLabRuntimeException(String.format("not deleted %s",punctOrInline.toShortString()));
                        }
                    }

                }
                charPos = saxonicaHelper.getStartPos(word);
                beginWord();
                for (Map.Entry<String, ConfigAnnotation> an : annotatedField.getAnnotations().entrySet()) {
                    ConfigAnnotation annotation = an.getValue();
                    // TODO we may need to support multiple values here
                    String value = saxonicaHelper.getValue(annotation.getValuePath(),word);
                    annotation(annotation.getName(),value,1,null);
                }
                charPos = saxonicaHelper.getEndPos(word);
                endWord();
                if (inlinesToEnd.containsKey(wNum)) {
                    for (NodeInfo toEnd : inlinesToEnd.get(wNum)) {
                        inlineTag(toEnd.getDisplayName(),false,null);
                    }
                }
            }

        }
    }
    
    private static class PunctOrInline {
        private final NodeInfo punctOrInline;
        private final boolean isPunct;

        public PunctOrInline(NodeInfo punctOrInline, boolean isPunct) {
            this.punctOrInline = punctOrInline;
            this.isPunct = isPunct;
        }
        
    }

    /**
     * return punctuations and inlines occurring before a word
     * @param puncts
     * @param inlines
     * @param word
     * @return
     */
    private List<NodeInfo> getPreceding(List<NodeInfo> puncts, List<NodeInfo> inlines, NodeInfo word, List<NodeInfo> precedingPuncts) {
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
        SaxonicaHelper.documentOrder(preceding);
        return preceding;
    }


    protected void processMetadataBlock(NodeInfo doc, ConfigMetadataBlock b) throws XPathExpressionException {

        for (NodeInfo header : saxonicaHelper.findNodes(b.getContainerPath(),doc)) {
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
                    for (NodeInfo forEach : saxonicaHelper.findNodes(f.getForEachPath(),header)) {
                        // Find the fieldName and value for this forEach match
                        String origFieldName = saxonicaHelper.getValue(f.getName(),forEach);
                        String fieldName = AnnotatedFieldNameUtil.sanitizeXmlElementName(origFieldName);
                        if (!origFieldName.equals(fieldName)) {
                            DocIndexerXPath.warnSanitized(origFieldName, fieldName);
                        }
                        ConfigMetadataField metadataField = b.getOrCreateField(fieldName);

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
        for (Object val : saxonicaHelper.find(f.getValuePath(),header)) {
            if (val instanceof NodeInfo) {
                String unprocessedValue = saxonicaHelper.getValue(".", val);
                for (String value : processStringMultipleValues(unprocessedValue, f.getProcess(), f.getMapValues())) {
                    addMetadataField(f.getName(), value);
                }
            } else {
                String metadataValue = processString(String.valueOf(val), f.getProcess(), f.getMapValues());
                addMetadataField(f.getName(), metadataValue);

            }
        }
    }


    protected void processLinkedDocument(NodeInfo doc, ConfigLinkedDocument ld) {
        throw new NotImplementedException("not implemented yet");
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(new String(saxonicaHelper.getChars()));
    }

    @Override
    public void close() {
    }

    private int charPos = 0;

    @Override
    protected int getCharacterPosition() {
        return charPos;
    }
}
