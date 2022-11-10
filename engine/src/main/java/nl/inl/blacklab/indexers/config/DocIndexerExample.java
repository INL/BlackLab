package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;

/**
 * A toy DocIndexer that demonstrates advanced custom format support.
 *
 * Note that for most XML or TSV formats, you should just use a .blf.yaml
 * configuration file, but if your format doesn't fit this setup, implementing
 * your own DocIndexer will give the most flexibility.
 *
 * The example format is designed to show the most basic building blocks
 * of indexing, not to be practical or robust. It covers only some of the possibilities.
 * It contains one instruction per line. Each instruction is
 * a uppercase word followed by 0 or more parameter(s), whitespace-separated.
 * Anything after a hash symbol is ignored.
 *
 * Also see example.txt in the resources dir.
 *
 * Example:
 * <code>
 * # Add document
 * DOC_START                     # begin a new document
 *   # Add metadata
 *   MVALUE author Pete Puck
 *   MVALUE title This is a test.
 *
 *   # Add annotation values
 *   AVAL_START contents
 *     VAL word The
 *     VAL lemma the
 *     POS_ADD 1                 # Go to next token position
 *     VAL word quick
 *     VAL lemma quick
 *     POS_ADD 1                 # Go to next token position
 *     VAL word brown
 *     VAL lemma brown
 *     POS_ADD 1                 # Go to next token position
 *     VAL word fox
 *     VAL lemma fox
 *     SPAN named-entity 0 4     # Add span according to token positions
 *                               # (note that start is inclusive, end exclusive)
 *     VAL word jumps
 *     VAL lemma jump
 *   AVAL_END
 *
 * DOC_END                       # end document and add to index
 * </code>
 *
 */
public class DocIndexerExample extends DocIndexerBase {

    private BufferedReader reader;

    /** Are we inside a document? */
    private boolean inDoc = false;

    /** Name of annotated field we're processing or null if not in annotated field part */
    private String currentAnnotatedField = null;

    /** What position increment should the next annotation values get? */
    private int posIncr;

    /** Character position within the input file. */
    private int characterPosition = 0;

    public DocIndexerExample() {

        // Create our index structure: annotated and metadata fields.
        createSimpleAnnotatedField("contents", List.of("word", "lemma"));
        createMetadataField("pid", FieldType.UNTOKENIZED);
        createMetadataField("author", FieldType.UNTOKENIZED);
        createMetadataField("title", FieldType.TOKENIZED);
    }

    /**
     * Create a simple annotated field.
     *
     * All of the given annotations get a forward index and will be indexed
     * accent/case insensitively only. A special annotation for spans ("tags")
     * will also be created.
     *
     * @param name annotated field name
     * @param annotations annotations on this field
     */
    private void createSimpleAnnotatedField(String name, List<String> annotations) {
        BlackLabIndexWriter indexWriter = getDocWriter().indexWriter();
        // Configure an annotated field "contents".
        // Add two annotations, "word" and "lemma". First one added will be the main annotation.
        ConfigAnnotatedField field = new ConfigAnnotatedField();
        field.setName(name);
        for (String annotationName: annotations) {
            addAnnotationToFieldConfig(field, annotationName,
                    AnnotationSensitivities.ONLY_INSENSITIVE,
                    true);
        }
        // Add a special annotation where we can index arbitrary spans.
        addAnnotationToFieldConfig(field, AnnotatedFieldNameUtil.TAGS_ANNOT_NAME,
                AnnotationSensitivities.ONLY_SENSITIVE, false);

        // Add the field to the index metadata
        getDocWriter().indexWriter().annotatedFields().addFromConfig(field);

        // Create and add AnnotatedFieldWriter so we can index this field
        addAnnotatedField(createAnnotatedFieldWriter(field));
    }

    private static ConfigAnnotation addAnnotationToFieldConfig(ConfigAnnotatedField config, String name,
            AnnotationSensitivities sensitivities, boolean forwardIndex) {
        ConfigAnnotation annot = new ConfigAnnotation();
        annot.setName(name);
        annot.setSensitivity(sensitivities);
        annot.setForwardIndex(forwardIndex);
        config.addAnnotation(annot);
        return annot;
    }

    private AnnotatedFieldWriter createAnnotatedFieldWriter(ConfigAnnotatedField fieldContents) {
        BlackLabIndexWriter indexWriter = getDocWriter().indexWriter();
        // Add the configured field to our index metadata
        indexWriter.annotatedFields().addFromConfig(fieldContents);

        // Create a AnnotatedFieldWriter for this field so we can index it
        Collection<ConfigAnnotation> annots = fieldContents.getAnnotations().values();
        Iterator<ConfigAnnotation> annotIt = annots.iterator();
        ConfigAnnotation mainAnnotation = annotIt.next();
        AnnotatedFieldWriter contents = new AnnotatedFieldWriter(fieldContents.getName(),
                mainAnnotation.getName(), mainAnnotation.getSensitivitySetting(),
                false,
                indexWriter.needsPrimaryValuePayloads());
        while (annotIt.hasNext()) {
            ConfigAnnotation annot = annotIt.next();
            boolean includePayloads = annot.getName() == AnnotatedFieldNameUtil.TAGS_ANNOT_NAME;
            contents.addAnnotation(annot.getName(), annot.getSensitivitySetting(), includePayloads, annot.createForwardIndex());
        }
        return contents;
    }

    private ConfigMetadataField createMetadataField(String name, FieldType type) {
        ConfigMetadataField metaPidConfig = new ConfigMetadataField();
        metaPidConfig.setName(name);
        metaPidConfig.setType(type);
        getDocWriter().indexWriter().metadata().metadataFields().addFromConfig(metaPidConfig);
    }

    @Override
    public void close() {

    }

    @Override
    public void index() throws IOException, MalformedInputFile, PluginException {
        // Execute the commands one line at a time.
        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;

            // Try to keep track of character position, for highlighting.
            // Assumes lines end with only a newline. May not be accurate.
            characterPosition += line.length() + 1;

            // Execute the command on this line, if any.
            processLine(line);
        }
    }

    private void processLine(String line) {
        // Remove comments and leading/trailing whitespace
        line = line.replaceAll("^\\s*(\\S.*\\S)\\s*(#.*)?$", "$1");
        if (line.isEmpty())
            return;

        String[] parts = line.split("\\s+", -1);
        String command = parts[0];
        String[] parameters = Arrays.copyOfRange(parts, 1, Integer.MAX_VALUE);
        executeCommand(command, parameters);
    }

    private void executeCommand(String command, String[] parameters) {
        if (currentAnnotatedField != null) {
            executeValueCommand(command, parameters);
        } else if (inDoc) {
            execDocCommand(command, parameters);
        } else {
            // Top-level. Look for documents.
            switch (command) {
            case "DOC_START":
                // Start a document.
                inDoc = true;
            default:
                throw new RuntimeException("Command " + command + " cannot appear at top level");
            }
        }
    }

    /**
     * Handle command inside a AVAL (annotated field values) block.
     *
     * @param command command to handle
     * @param parameters parameters
     */
    private void executeValueCommand(String command, String[] parameters) {
        switch (command) {
        case "VAL":
            // Add annotation value at current position.
            String annotationName = parameters[0];
            String value = parameters[1];
            annotation(annotationName, value, posIncr, null);
            break;
        case "POS_ADD":
            // Increase current position.
            posIncr = Integer.parseInt(parameters[0]);
            break;
        case "SPAN":
            // Add a named span, e.g. <p>, <s>, <named-entity>, etc.
            // at the specified token positions.
            String spanType = parameters[0];
            int spanStart = Integer.parseInt(parameters[1]);
            int spanEnd = Integer.parseInt(parameters[2]);
            //@@@@
            //annotation(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME, )
            // PAYLOAD
        case "AVAL_END":
            // End the annotated field value block.
            currentAnnotatedField = null;
            break;
        default:
            throw new RuntimeException("Command " + command + " cannot appear inside annotated field block");
        }
    }

    /**
     * Handle command inside a DOC (document) block.
     *
     * @param command command to handle
     * @param parameters parameters
     */
    private void execDocCommand(String command, String[] parameters) {
        switch (command) {
        case "MVALUE":
            // Gives a document metadata value.
            String name = parameters[0];
            String value = parameters[1];
            addMetadataField(name, value);
            break;
        case "AVAL_START":
            // Starts an annotated field values block.
            currentAnnotatedField = parameters[0];
            setCurrentAnnotatedFieldName(currentAnnotatedField);
            break;
        case "DOC_END":
            // Ends document.
            inDoc = false;
            break;
        default:
            throw new RuntimeException("Command " + command + " cannot appear inside document");
        }
    }

    @Override
    protected int getCharacterPosition() {
        // This should ideally keep track of character position within the input document, to be used
        // for highlighting in BlackLab later. If you don't use BlackLab's highlighting, you don't need this.
        return characterPosition;
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // Used with linked documents to index part of a linked document.
        // For example, if you're indexing a document A that links to a document B that contains metadata
        // for many documents, you only want to index the metadata for document A in document B. The
        // document expression indicates how to find that metadata inside document B. For XML, this would probably
        // be an XPath expression.
        // If this doesn't fit your needs, it is of course always fine to ignore this and write your own code
        // to handle your linked resources.
    }

    @Override
    protected void storeDocument() {
        // If you want to store your input documents in the BlackLab index for easy retrieval and highlighting
        // later, you should uncomment the next line.
        //storeWholeDocument(document);
    }

    /**
     * Sets document to be indexed.
     *
     * NOTE: there are other setDocument methods that you can override. In some
     * cases, depending on how you process your data, you can optimize indexing by
     * implementing another method. For example, DocIndexerXpath uses the byte array
     * because that's what VTD-XML expects.
     *
     * @param reader document to index
     */
    @Override
    public void setDocument(Reader reader) {
        this.reader = IOUtils.toBufferedReader(reader);
    }

}
