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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.FileUtil;

/**
 * Indexer for the CoNLL-U format.
 *
 * See <a href="https://universaldependencies.org/format.html">https://universaldependencies.org/format.html</a>
 *
 * We don't support all features (yet).
 */
public class DocIndexerCoNLLU extends DocIndexerTabularBase {

    public static final int COL_HEAD = 6;

    public static final int COL_DEP = 7;

    private StringBuilder csvData;

    private BufferedReader inputReader;

    public DocIndexerCoNLLU() {
        super("\\|");
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() != 1)
            throw new InvalidInputFormatConfig("Tabular type must have only 1 annotated field");
        super.setConfigInputFormat(config);
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
        inputReader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
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

    @Override protected void endDocument() {
        super.endDocument();

        // Clear csvData (we use this to store the document)
        csvData.delete(0, csvData.length());
    }

    /** Single- or double-quoted attribute in a tag */
    private static final Pattern SENTENCE_ATTRIBUTE_LINE = Pattern.compile(
            "^#\\s*(\\w+)\\s*=\\s*(.+)$");

    private static class Span {
        int start;
        int end;

        public Span(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public Span(int start) {
            this.start = start;
            this.end = start + 1;
        }
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        csvData = new StringBuilder();
        startDocument();

        // For the configured annotated field...
        ConfigAnnotatedField annotatedField = config.getAnnotatedFields().values().iterator().next();
        setCurrentAnnotatedFieldName(annotatedField.getName());

        boolean inSentence = false;

        // For each token position
        Map<String, String> sentenceAttr = new LinkedHashMap<>();
        int sentenceStartPosition = -1;
        while (true) {

            // Read and trim next line
            String origLine = inputReader.readLine();
            if (origLine == null)
                break; // end of file
            String line = origLine.trim();

            // Is it empty?
            if (line.length() == 0) {
                if (inSentence) {
                    // Empty line ends sentence
                    inlineTag("s", false, null);
                    inSentence = false;
                }
                continue; // skip empty lines
            }

            // Sentence attribute line? ("comment", but not really used as such)
            if (line.startsWith("#")) {
                if (!inSentence) {
                    // Comments before sentence contain attributes for the sentence tag.
                    // Collect them until we actually start the sentence.
                    // (Mid-sentence comment lines are ignored)
                    Matcher m = SENTENCE_ATTRIBUTE_LINE.matcher(line);
                    if (m.matches()) {
                        String attributeName = m.group(1);
                        String attributeValue = m.group(2);
                        sentenceAttr.put(attributeName, attributeValue);
                    }
                }
                continue;
            }

            // Index each annotation
            beginWord();

            if (!inSentence) {
                // We're not in a sentence yet and encountered a value line; start the sentence now.
                inlineTag("s", true, sentenceAttr);
                sentenceAttr.clear();
                inSentence = true;
                sentenceStartPosition = getCurrentTokenPosition();
            }

            // Split the line into columns
            List<String> record = List.of(line.split("\t", -1));

            String id = record.get(0);
            if (id.contains(".")) {
                // skip decimal ids; these are "empty tokens" (i.e. implied words that weren't actually in the sentence)
                // ex. Sue likes coffee and Bill [likes] tea.
                // the bracketed word 'likes' would get a decimal id.
                continue;
            }
            Span span = idSpan(id, sentenceStartPosition);

            // Store this line now, so the start/end offsets are correct
            if (isStoreDocuments())
                csvData.append(origLine);

            // Index dependency relation
            String strHead = record.size() > COL_HEAD ? record.get(COL_HEAD) : "_";
            if (!strHead.isEmpty() && !strHead.equals("_")) {
                boolean isRoot = strHead.equals("0");
                String relationType = record.size() > COL_DEP ? record.get(COL_DEP) : "_";
                if (!isRoot) {
                    // Regular relation with source and target.
                    Span headSpan = idSpan(strHead, sentenceStartPosition);
                    tagsAnnotation().indexRelation(relationType, false, headSpan.start, headSpan.end,
                            span.start, span.end, null, getIndexType());
                } else {
                    // Root relation has no source. We just use the target positions for the source, so
                    // the relation is stored in a sane position.
                    tagsAnnotation().indexRelation(relationType, true, span.start, span.end,
                            span.start, span.end, null, getIndexType());
                }
            }

            // Index all annotations defined in the config file
            for (ConfigAnnotation annotation : annotatedField.getAnnotationsFlattened().values()) {
                String value;
                if (annotation.isValuePathInteger()) {
                    int i = annotation.getValuePathInt() - 1;
                    if (i < record.size())
                        value = record.get(i);
                    else
                        value = "";
                } else {
                    throw new RuntimeException("valuePath must be a column number");
                }
                indexValue(annotation, value);
            }
            endWord();
        }

        endDocument();
    }

    private Span idSpan(String id, int currentSentenceStart) {
        if (id.matches("\\d+-\\d+")) {
            // Span ID; calculate length
            String[] parts = id.split("-");
            int first = Integer.parseInt(parts[0]) - 1;
            int second = Integer.parseInt(parts[1]) - 1;
            return new Span(currentSentenceStart + first, currentSentenceStart + second);
        } else if (id.matches("\\d+"))
            return new Span(currentSentenceStart + Integer.parseInt(id) - 1);
        else
            throw new IllegalArgumentException("Invalid ID: " + id);
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
