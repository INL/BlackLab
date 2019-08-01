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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.FileUtil;

/**
 * An indexer for tabular file formats, such as tab-separated or comma-separated
 * values.
 */
public class DocIndexerTabular extends DocIndexerConfig {

    /** Tabular types we support */
    enum Type {
        CSV,
        TSV;

        public static Type fromStringValue(String str) {
            switch (str.toUpperCase()) {
            case "TDF":
                return TSV;
            case "EXCEL":
                return CSV;
            }
            return valueOf(str.toUpperCase());
        }

        public String stringValue() {
            return toString().toLowerCase();
        }
    }

    /**
     * Regex for recognizing open or close tag and capturing tag name and attributes
     * part
     */
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

    private boolean hasInlineTags;

    private boolean hasGlueTags;

    /**
     * After an inline tag such as {@code <s>}, may there be separator character(s)
     * like on the non-tag lines? By default, this is not allowed, but this option
     * can be turned on in the configuration file.
     */
    private boolean allowSeparatorsAfterInlineTags;

    private String multipleValuesSeparator = ";";

    private BufferedReader inputReader;

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() > 1)
            throw new InvalidInputFormatConfig("Tabular type can only have 1 annotated field");
        super.setConfigInputFormat(config);
        Map<String, String> opt = config.getFileTypeOptions();
        Type type = opt.containsKey("type") ? Type.fromStringValue(opt.get("type")) : Type.CSV;
        //ConfigTabularOptions tab = config.getTabularOptions();
        switch (type) {
        case TSV:
            tabularFormat = CSVFormat.TDF;
            break;
        case CSV:
            tabularFormat = CSVFormat.EXCEL;
            break;
        default:
            throw new InvalidInputFormatConfig("Unknown tabular type " + opt.get("type") + " (use csv or tsv)");
        }
        if (opt.containsKey("columnNames") && opt.get("columnNames").equalsIgnoreCase("true"))
            tabularFormat = tabularFormat.withFirstRecordAsHeader();
        if (opt.containsKey("delimiter") && opt.get("delimiter").length() > 0)
            tabularFormat = tabularFormat.withDelimiter(opt.get("delimiter").charAt(0));
        if (opt.containsKey("quote") && opt.get("quote").length() > 0)
            tabularFormat = tabularFormat.withQuote(opt.get("quote").charAt(0));
        else
            tabularFormat = tabularFormat.withQuote(null); // disable quotes altogether
        allowSeparatorsAfterInlineTags = false;
        if (opt.containsKey("allowSeparatorsAfterInlineTags")
                && opt.get("allowSeparatorsAfterInlineTags").equalsIgnoreCase("true"))
            allowSeparatorsAfterInlineTags = true;
        hasInlineTags = opt.containsKey("inlineTags") && opt.get("inlineTags").equalsIgnoreCase("true");
        hasGlueTags = opt.containsKey("glueTags") && opt.get("glueTags").equalsIgnoreCase("true");
        if (opt.containsKey("multipleValuesSeparator"))
            multipleValuesSeparator = opt.get("multipleValuesSeparator");
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) throws FileNotFoundException {
        setDocument(FileUtil.openForReading(file, defaultCharset));
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
            inputReader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
            records = tabularFormat.parse(inputReader);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void close() throws BlackLabRuntimeException {
        try {
            if (inputReader != null)
                inputReader.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        // If a documentPath was specified, look for that as the document tag
        boolean lookForDocumentTags = !config.getDocumentPath().equals("/");

        if (!lookForDocumentTags)
            startDocument();

        // Do we need to look for tags at all?
        boolean lookForTags = !lookForDocumentTags || hasInlineTags || hasGlueTags;

        // Are we inside a document now?
        boolean inDocument = !lookForDocumentTags;

        csvData = new StringBuilder();
        try (CSVPrinter p = new CSVPrinter(csvData, tabularFormat)) {
            // For the configured annotated field...
            for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
                setCurrentAnnotatedFieldName(annotatedField.getName());

                // For each token position
                for (CSVRecord record : records) {
                    if (record.size() == 0)
                        continue; // skip empty lines

                    // If this format contains tags...
                    if (lookForTags && (record.size() == 1 || allowSeparatorsAfterInlineTags)) {
                        // Is this a tag line instead of a token line?
                        String possibleTag = record.get(0);
                        Matcher m = REGEX_TAG.matcher(possibleTag);
                        if (m.find()) {
                            // It's a document tag, an inline tag or a glue tag
                            boolean isOpenTag = m.group(1) == null;
                            String tagName = m.group(2);
                            String rest = m.group(3).trim();
                            boolean selfClosing = rest.endsWith("/");
                            if (!isOpenTag && selfClosing)
                                throw new MalformedInputFile("Close tag must not also end with /: " + tagName);
                            if (selfClosing)
                                rest = rest.substring(0, rest.length() - 1);
                            Map<String, String> attributes = getAttr(rest);

                            if (lookForDocumentTags && tagName.equals(config.getDocumentPath())) {
                                // Document tag.
                                if (inDocument && isOpenTag)
                                    throw new MalformedInputFile("Found document open tag inside document");
                                if (!inDocument && !isOpenTag)
                                    throw new MalformedInputFile(
                                            "Found document close tag outside of document");
                                if (isOpenTag) {
                                    // Start a new document and add attributes as metadata fields
                                    inDocument = true;
                                    startDocument();
                                    for (Entry<String, String> e : attributes.entrySet()) {
                                        String value = processMetadataValue(e.getKey(), e.getValue());
                                        addMetadataField(e.getKey(), value);
                                    }
                                } else {
                                    endDocument();
                                    inDocument = false;
                                }
                            } else if (hasGlueTags && tagName.equals(GLUE_TAG_NAME)) {
                                // Glue tag. Don't add default punctuation when adding next word.
                                if (!attributes.isEmpty())
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
                    if (isStoreDocuments()) {
                        p.printRecord(record);
                    }

                    // For each annotation
                    for (ConfigAnnotation annotation : annotatedField.getAnnotationsFlattened().values()) {
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
                        value = processString(value, annotation.getProcess(), null);
                        if (annotation.isMultipleValues()) {
                            // Multiple values possible. Split on multipleValuesSeparator.
                            boolean first = true;
                            List<String> values = Arrays.asList(value.split(multipleValuesSeparator, -1));
                            if (!annotation.isAllowDuplicateValues()) {
                                // Discard any duplicate values from the list
                                values = values.stream().distinct().collect(Collectors.toList());
                            }
                            for (String v : values) {
                                annotation(annotation.getName(), v, first ? 1 : 0, null);
                                first = false;
                            }
                        } else {
                            // Single value.
                            annotation(annotation.getName(), value, 1, null);
                        }
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
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(csvData.toString());
    }

    @Override
    protected int getCharacterPosition() {
        return csvData.length();
    }

}
