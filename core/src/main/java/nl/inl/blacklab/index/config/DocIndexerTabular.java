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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.index.InputFormatException;
import nl.inl.util.ExUtil;
import nl.inl.util.FileUtil;

/**
 * An indexer for tabular file formats, such as tab-separated
 * or comma-separated values.
 */
public class DocIndexerTabular extends DocIndexerConfig {

    /** Regex for recognizing open or close tag and capturing tag name and attributes part */
    final static Pattern REGEX_TAG = Pattern.compile("^\\s*<\\s*(/\\s*)?(\\w+)((\\b[^>]+)?)>\\s*$");

    /** Single- or double-quoted attribute in a tag */
    static final Pattern REGEX_ATTR = Pattern.compile("\\b(\\w+)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    private static Map<String, String> getAttr(String group) {
        if (group == null)
            return Collections.emptyMap();
        String strAttrDef = group.trim();
        Matcher m = REGEX_ATTR.matcher(strAttrDef);
        Map<String, String> attributes = new LinkedHashMap<>();
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            value = value.substring(1, value.length() - 1); // chop quotes
            attributes.put(key, value);
        }
        return attributes;
    }

    private static final Object GLUE_TAG_NAME = "g";

    Iterable<CSVRecord> records;

    CSVFormat tabularFormat = CSVFormat.EXCEL;

    StringBuilder csvData;

    public DocIndexerTabular() {
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() > 1)
            throw new InputFormatConfigException("Tabular type can only have 1 annotated field");
        super.setConfigInputFormat(config);
        ConfigTabularOptions tab = config.getTabularOptions();
        switch (tab.getType()) {
        case TSV:
            tabularFormat = CSVFormat.TDF;
            break;
        case CSV:
            tabularFormat = CSVFormat.EXCEL;
            break;
        default:
            throw new InputFormatConfigException("Unknown tabular type " + tab.getType() + " (use csv or tsv)");
        }
        if (tab.hasColumnNames())
            tabularFormat = tabularFormat.withFirstRecordAsHeader();
        if (tab.getDelimiter() != null)
            tabularFormat = tabularFormat.withDelimiter(tab.getDelimiter());
        if (tab.getQuote() != null)
            tabularFormat = tabularFormat.withQuote(tab.getQuote());
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) throws FileNotFoundException {
        Reader r = FileUtil.openForReading(file, defaultCharset);
        setDocument(r);
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        setDocument(new ByteArrayInputStream(contents), defaultCharset);
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        setDocument(new InputStreamReader(new BOMInputStream(is), defaultCharset));
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            BufferedReader br = reader instanceof BufferedReader ? (BufferedReader)reader : new BufferedReader(reader);
            records = tabularFormat.parse(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void index() throws Exception {
        super.index();

        // If a documentPath was specified, look for that as the document tag
        boolean lookForDocumentTags = !config.getDocumentPath().equals("/");

        if (!lookForDocumentTags)
            startDocument();

        // Do we need to look for tags at all?
        boolean lookForTags = !lookForDocumentTags || config.getTabularOptions().hasInlineTags() || config.getTabularOptions().hasGlueTags();

        // Are we inside a document now?
        boolean inDocument = !lookForDocumentTags;

        csvData = new StringBuilder();
        try (CSVPrinter p = new CSVPrinter(csvData, tabularFormat)) {
            // For the configured annotated field...
            for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields().values()) {
                setCurrentComplexField(annotatedField.getName());

                // For each token position
                for (CSVRecord record: records) {
                    if (record.size() == 0)
                        continue; // skip empty lines

                    // If this format contains tags...
                    if (lookForTags && record.size() == 1) {
                        // Is this a tag line instead of a token line?
                        Matcher m = REGEX_TAG.matcher(record.get(0));
                        if (m.find()) {
                            // It's a document tag, an inline tag or a glue tag
                            boolean isOpenTag = m.group(1) == null;
                            String tagName = m.group(2);
                            String rest = m.group(3).trim();
                            boolean selfClosing = rest.endsWith("/");
                            if (!isOpenTag && selfClosing)
                                throw new InputFormatException("Close tag must not also end with /: " + tagName);
                            if (selfClosing)
                                rest = rest.substring(0, rest.length() - 1);
                            Map<String, String> attributes = getAttr(rest);

                            if (lookForDocumentTags && tagName.equals(config.getDocumentPath())) {
                                // Document tag.
                                if (inDocument && isOpenTag)
                                    throw new InputFormatException("Found document open tag inside document");
                                if (!inDocument && !isOpenTag)
                                    throw new InputFormatException("Found document close tag outside of document");
                                if (isOpenTag) {
                                    // Start a new document and add attributes as metadata fields
                                    inDocument = true;
                                    startDocument();
                                    for (Entry<String, String> e: attributes.entrySet()) {
                                    	// TODO: execute processing step(s) for metadata fields
                                        addMetadataField(e.getKey(), e.getValue());
                                    }
                                } else {
                                    endDocument();
                                    inDocument = false;
                                }
                            } else if (config.getTabularOptions().hasGlueTags() && tagName.equals(GLUE_TAG_NAME)) {
                                // Glue tag. Don't add default punctuation when adding next word.
                                if (attributes.size() > 0)
                                    warn("Glue tag has attributes: " + attributes.toString());
                                setPreventNextDefaultPunctuation();
                            } else {
                                inlineTag(tagName, isOpenTag, attributes);
                                if (selfClosing)
                                    inlineTag(tagName, !isOpenTag, null);
                            }
                            continue;
                        }
                    }

                    // It's a regular token
                    beginWord();
                    if (getStoreDocuments()) {
                        p.printRecord(record);
                    }

                    // For each annotation
                    for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                        // Either column number of name
                        String value;
                        if (annotation.isValuePathInteger()) {
                            int i = annotation.getValuePathInt() - 1;
                            if (i < record.size())
                                value = record.get(i);
                            else
                                value = "";
                        } else {
                            if (record.isMapped(annotation.getValuePath()))
                                value = record.get(annotation.getValuePath());
                            else
                                value = "";
                        }
                        value = processString(value, annotation.getProcess());
                        annotation(annotation.getName(), value, 1, null);
                    }
                    endWord();
                }
            }
        }

        if (!lookForDocumentTags)
            endDocument();
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // documentExpr is ignored because tabular format files always contain 1 document
        try {
            index();
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(csvData.toString());
    }

    @Override
	public int getCharacterPosition() {
        return csvData.length();
	}

}
