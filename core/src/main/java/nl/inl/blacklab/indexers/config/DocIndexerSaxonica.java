package nl.inl.blacklab.indexers.config;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import org.xml.sax.SAXException;

import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
//            processLinkedDocument(doc, ld);
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
            for (NodeInfo word : saxonicaHelper.findNodes(annotatedField.getWordsPath(),container)) {
                wNum++;
                charPos = saxonicaHelper.getStartPos(word);
                beginWord();
                for (Map.Entry<String, ConfigAnnotation> an : annotatedField.getAnnotations().entrySet()) {
                    ConfigAnnotation annotation = an.getValue();
                    String value = saxonicaHelper.getValue(annotation.getValuePath(),word);
                    annotation(annotation.getName(),value,1,null);
                }
                /*
                hier gaan we kijken of er inline tags of leestekens voor dit woord zitten
                - indexeren met begin en eindpositie
                - verwijderen uit lijst met inlines en leestekens

                m.b.v. List#contains / equals in NodeInfo kunnen we zien of we een leesteken of inline hebben
                 */
                List<NodeInfo> preceding = new ArrayList<>(3);
                for (NodeInfo pi : puncts) {
                    if (word.compareOrder(pi)!=1) {
                        break;
                    }
                    preceding.add(pi);
                }
                for (NodeInfo pi : inlines) {
                    if (word.compareOrder(pi)!=1) {
                        break;
                    }
                    preceding.add(pi);
                }
                SaxonicaHelper.documentOrder(preceding);
                for (NodeInfo punctOrInline : preceding) {
                    if (puncts.contains(punctOrInline)) {
                        String punct = punctOrInline.getStringValue();
                        punctuation(punct==null||punct.isEmpty()?" ":punct);
                        if (!puncts.remove(punctOrInline)) {
                            throw new BlackLabRuntimeException(String.format("punct not deleted %s",punctOrInline.toShortString()));
                        }
                    } else {
                        AxisIterator descendants = punctOrInline.iterateAxis(Axis.DESCENDANT.getAxisNumber());
                        boolean wraps = false;
                        NodeInfo next;
                        while ((next = descendants.next()) != null) {
                            if (next.equals(word)) {
                                wraps = true;
                                break;
                            }
                        }
                        if (wraps) {
                            // Now I have all values but no method to index them
                            int count = saxonicaHelper.findNodes(annotatedField.getWordsPath(),punctOrInline).size();
                            Map<String,String> atts = new HashMap<>(3);
                            AxisIterator attributes = punctOrInline.iterateAxis(Axis.ATTRIBUTE.getAxisNumber());
                            while ((next = attributes.next()) != null) {
                                atts.put(next.getDisplayName(),next.getStringValue());
                            }
                            addInlineTag(punctOrInline.getDisplayName(),atts,wNum,wNum+count);
                            System.out.println(punctOrInline.toShortString());
                        }
                        if (!inlines.remove(punctOrInline)) {
                            throw new BlackLabRuntimeException(String.format("not deleted %s",punctOrInline.toShortString()));
                        }
                    }

                }
                charPos = saxonicaHelper.getEndPos(word);
                endWord();
            }
        }
    }


    protected void processMetadataBlock(NodeInfo meta, ConfigMetadataBlock b) throws XPathExpressionException {

        for (NodeInfo header : saxonicaHelper.findNodes(b.getContainerPath(),contents)) {
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
//        // Resolve linkPaths to get the information needed to fetch the document
//        List<String> results = new ArrayList<>();
//        for (ConfigLinkValue linkValue : ld.getLinkValues()) {
//            String result = "";
//            String valuePath = linkValue.getValuePath();
//            String valueField = linkValue.getValueField();
//            if (valuePath != null) {
//                // Resolve value using XPath
//                AutoPilot apLinkPath = acquireAutoPilot(valuePath);
//                result = apLinkPath.evalXPathToString();
//                if (result == null || result.isEmpty()) {
//                    switch (ld.getIfLinkPathMissing()) {
//                        case IGNORE:
//                            break;
//                        case WARN:
//                            docWriter.listener()
//                                    .warning("Link path " + valuePath + " not found in document " + documentName);
//                            break;
//                        case FAIL:
//                            throw new BlackLabRuntimeException("Link path " + valuePath + " not found in document " + documentName);
//                    }
//                }
//                releaseAutoPilot(apLinkPath);
//            } else if (valueField != null) {
//                // Fetch value from Lucene doc
//                result = getMetadataField(valueField).get(0);
//            }
//            result = processString(result, linkValue.getProcess(), null);
//            results.add(result);
//        }
//
//        // Substitute link path results in inputFile, pathInsideArchive and documentPath
//        String inputFile = replaceDollarRefs(ld.getInputFile(), results);
//        String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
//        String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);
//
//        try {
//            // Fetch and index the linked document
//            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormatIdentifier(),
//                    ld.shouldStore() ? ld.getName() : null);
//        } catch (Exception e) {
//            String moreInfo = "(inputFile = " + inputFile;
//            if (pathInsideArchive != null)
//                moreInfo += ", pathInsideArchive = " + pathInsideArchive;
//            if (documentPath != null)
//                moreInfo += ", documentPath = " + documentPath;
//            moreInfo += ")";
//            switch (ld.getIfLinkPathMissing()) {
//                case IGNORE:
//                case WARN:
//                    docWriter.listener().warning("Could not find or parse linked document for " + documentName + moreInfo
//                            + ": " + e.getMessage());
//                    break;
//                case FAIL:
//                    throw new BlackLabRuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
//            }
//        }
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
