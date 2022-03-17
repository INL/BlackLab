package nl.inl.blacklab.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.searches.SearchHitGroups;

/**
 * Determine frequency lists over annotation(s) and
 * metadata field(s) for the entire index.
 */
public class FrequencyTool {

    /** Configuration for making frequency lists */
    static class Config {

        /** Read config from file.
         *
         * @param f config file
         * @return config object
         */
        static Config fromFile(File f) {
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                return mapper.readValue(f, Config.class);
            } catch (IOException e) {
                throw new BlackLabRuntimeException("Error reading config file " + f, e);
            }
        }

        /** Annotated field to analyze */
        private String annotatedField;

        /** Frequency lists to make */
        private List<ConfigFreqList> frequencyLists;

        public String getAnnotatedField() {
            return annotatedField;
        }

        public void setAnnotatedField(String annotatedField) {
            this.annotatedField = annotatedField;
        }

        public List<ConfigFreqList> getFrequencyLists() {
            return frequencyLists;
        }

        @SuppressWarnings("unused")
        public void setFrequencyLists(List<ConfigFreqList> frequencyLists) {
            this.frequencyLists = frequencyLists;
        }

        @Override
        public String toString() {
            return "Config{" +
                    "annotatedField='" + annotatedField + '\'' +
                    ", frequencyLists=" + frequencyLists +
                    '}';
        }

        /**
         * Check if this is a valid config.
         *
         * @param index our index
         */
        public void check(BlackLabIndex index) {
            if (!index.annotatedFields().exists(annotatedField))
                throw new IllegalArgumentException("Annotated field not found: " + annotatedField);
            AnnotatedField af = index.annotatedField(annotatedField);
            Set<String> reportNames = new HashSet<>();
            for (ConfigFreqList l: frequencyLists) {
                String name = l.getReportName();
                if (reportNames.contains(name))
                    throw new IllegalArgumentException("Report occurs twice: " + name);
                reportNames.add(name);

                for (String a: l.getAnnotations()) {
                    if (!af.annotations().exists(a))
                        throw new IllegalArgumentException("Annotation not found: " + annotatedField + "." + a);
                }
                for (String m: l.getMetadataFields()) {
                    if (!index.metadataFields().exists(m))
                        throw new IllegalArgumentException("Metadata field not found: " + m);
                }
            }
        }
    }

    /** Configuration for making frequency lists */
    static class ConfigFreqList {

        /** A unique name that will be used as output file name */
        String name = "";

        /** Annotations to make frequency lists for */
        private List<String> annotations;

        /** Metadata fields to take into account (e.g. year for frequencies per year) */
        private List<String> metadataFields = Collections.emptyList();

        public String getName() {
            return name;
        }

        public String getReportName() {
            return name.isEmpty() ? generateName() : name;
        }

        private String generateName() {
            List<String> parts = new ArrayList<>();
            parts.addAll(annotations);
            parts.addAll(metadataFields);
            return StringUtils.join(parts, "-");
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<String> annotations) {
            this.annotations = annotations;
        }

        public List<String> getMetadataFields() {
            return metadataFields;
        }

        public void setMetadataFields(List<String> metadataFields) {
            this.metadataFields = metadataFields;
        }

        @Override
        public String toString() {
            return "ConfigFreqList{" +
                    "name='" + name + '\'' +
                    ", annotations=" + annotations +
                    ", metadataFields=" + metadataFields +
                    '}';
        }
    }

    static void exit(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    static void exitUsage(String msg) {
        if (!StringUtils.isEmpty(msg)) {
            System.out.println(msg + "\n");
        }
        exit("Usage:\n\n  FrequencyTool [--json] [--gzip] INDEX_DIR CONFIG_FILE [OUTPUT_DIR]\n\n" +
                "  INDEX_DIR    index to generate frequency lists for\n" +
                "  CONFIG_FILE  YAML file specifying what frequency lists to generate\n" +
                "  OUTPUT_DIR   where to write output files (defaults to current dir)\n");
    }

    public static void main(String[] args) throws ErrorOpeningIndex {
        // Check for options
        int numOpts = 0;
        boolean gzip = false;
        FreqListOutput.Format format = FreqListOutput.Format.TSV;
        for (String arg: args) {
            if (arg.startsWith("--")) {
                numOpts++;
                switch (arg) {
                case "--json":
                    format = FreqListOutput.Format.JSON;
                    break;
                case "--gzip":
                    gzip = true;
                    break;
                case "--help":
                    exitUsage("");
                    break;
                }
            } else
                break;
        }

        // Process arguments
        int numArgs = args.length - numOpts;
        if (numArgs < 2 || numArgs > 3) {
            exitUsage("Incorrect number of arguments.");
        }

        // Open index
        File indexDir = new File(args[numOpts]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            // Read config
            File configFile = new File(args[numOpts + 1]);
            if (!configFile.canRead()) {
                exit("Can't read config file " + configFile);
            }
            Config config = Config.fromFile(configFile);
            AnnotatedField annotatedField = index.annotatedField(config.getAnnotatedField());
            config.check(index);

            // Output dir
            File outputDir = new File(System.getProperty("user.dir")); // current dir
            if (numArgs > 2) {
                outputDir = new File(args[numOpts + 2]);
            }
            if (!outputDir.isDirectory() || !outputDir.canWrite()) {
                exit("Not a directory or cannot write to output dir " + outputDir);
            }

            // Generate the frequency lists
            makeFrequencyLists(index, annotatedField, config.getFrequencyLists(), outputDir, format, gzip);
        }
    }

    private static void makeFrequencyLists(BlackLabIndex index, AnnotatedField annotatedField, List<ConfigFreqList> freqLists, File outputDir, FreqListOutput.Format format, boolean gzip) {
        for (ConfigFreqList freqList: freqLists) {
            makeFrequencyList(index, annotatedField, freqList, outputDir, format, gzip);
        }
    }

    private static void makeFrequencyList(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList, File outputDir, FreqListOutput.Format format, boolean gzip) {

        // Create our search
        QueryInfo queryInfo = QueryInfo.create(index);
        BLSpanQuery anyToken = new SpanQueryAnyToken(queryInfo, 1, 1, annotatedField.name());
        HitProperty groupBy = getGroupBy(index, annotatedField, freqList);
        SearchHitGroups search = index.search()
                .find(anyToken)
                .groupStats(groupBy, 0)
                .sort(new HitGroupPropertyIdentity());
        try {
            // Execute search and write output file
            HitGroups result = search.execute();
            FreqListOutput.write(index, annotatedField, freqList, result, outputDir, format, gzip);
        } catch (InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating freqList " + freqList.getReportName(), e);
        }
    }

    private static HitProperty getGroupBy(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        List<HitProperty> groupProps = new ArrayList<>();
        // Add annotations to group by
        for (String name: freqList.getAnnotations()) {
            Annotation annotation = annotatedField.annotation(name);
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        // Add metadata fields to group by
        for (String name: freqList.getMetadataFields()) {
            groupProps.add(new HitPropertyDocumentStoredField(index, name));
        }
        return new HitPropertyMultiple(groupProps.toArray(new HitProperty[0]));
    }

    private static String currentDateTime() {
        final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        return sdf.format(cal.getTime());
    }

    interface FreqListOutput {

        enum Format {
            JSON,
            TSV
        }

        /**
         * Write a frequency list file.
         *  @param index our index
         * @param annotatedField annotated field
         * @param freqList freq list configuration, including name to use for file
         * @param result resulting frequencies
         * @param outputDir directory to write output file
         */
        public static void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList, HitGroups result, File outputDir, Format format, boolean gzip) {
            FreqListOutput f = format == Format.JSON ? new FreqListOutputJson() : new FreqListOutputTsv();
            f.write(index, annotatedField, freqList, result, outputDir, gzip);
        }

        void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList, HitGroups result, File outputDir, boolean gzip);

    }

    /**
     * Writes frequency results to a TSV file.
     */
    static class FreqListOutputTsv implements FreqListOutput {

        private BlackLabIndex index;
        private AnnotatedField annotatedField;
        private ConfigFreqList freqList;
        private HitGroups result;

        public void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
                           HitGroups result, File outputDir, boolean gzip) {
            this.index = index;
            this.annotatedField = annotatedField;
            this.freqList = freqList;
            this.result = result;
            File outputFile = new File(outputDir, freqList.getReportName() + ".tsv" + (gzip ? ".gz" : ""));
            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                OutputStream stream = outputStream;
                if (gzip)
                    stream = new GZIPOutputStream(stream);
                try (Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
                     CSVPrinter printer = new CSVPrinter(out, CSVFormat.TDF)) {
                    for (HitGroup group : result) {
                        List<String> record = new ArrayList<>();
                        PropertyValue identity = group.identity();
                        if (identity instanceof PropertyValueMultiple) {
                            // Grouped by multiple properties. Serialize each value separately
                            PropertyValueMultiple values = (PropertyValueMultiple) identity;
                            for (PropertyValue value : values.values()) {
                                record.add(value.toString());
                            }
                        } else {
                            // Grouped by single property. Serialize it.
                            record.add(identity.toString());
                        }
                        record.add(Long.toString(group.size()));
                        printer.printRecord(record);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error writing output for " + freqList.getReportName(), e);
            }
        }
    }

    /**
     * Writes frequency results to a JSON file.
     */
    static class FreqListOutputJson implements FreqListOutput {

        private BlackLabIndex index;
        private AnnotatedField annotatedField;
        private ConfigFreqList freqList;
        private HitGroups result;
        private JsonGenerator j;

        private FreqListOutputJson() {
        }

        public void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
                          HitGroups result, File outputDir, boolean gzip) {
            this.index = index;
            this.annotatedField = annotatedField;
            this.freqList = freqList;
            this.result = result;
            File outputFile = new File(outputDir, freqList.getReportName() + ".json" + (gzip ? ".gz" : ""));
            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                OutputStream stream = outputStream;
                if (gzip)
                    stream = new GZIPOutputStream(stream);
                JsonFactory jfactory = new JsonFactory();
                try (JsonGenerator j = jfactory.createGenerator(stream, JsonEncoding.UTF8)) {
                    this.j = j;
                    j.writeStartObject();
                    {
                        j.writeStringField("generatedAt", currentDateTime());
                        j.writeStringField("indexName", index.name());
                        j.writeStringField("annotatedField", annotatedField.name());
                        j.writeFieldName("config");
                        writeConfig();
                        j.writeFieldName("results");
                        writeResults();
                    }
                    j.writeEndObject();
                }
            } catch (IOException e) {
                throw new RuntimeException("Error writing output for " + freqList.getReportName(), e);
            }
        }

        /**
         * Write results object as a JSON array.
         *
         * @throws IOException
         */
        private void writeResults() throws IOException {
            j.writeStartArray();
            for (HitGroup group: result) {
                j.writeStartObject();
                {
                    PropertyValue identity = group.identity();
                    j.writeFieldName("identity");
                    j.writeStartArray();
                    {
                        if (identity instanceof PropertyValueMultiple) {
                            // Grouped by multiple properties. Serialize each value separately
                            PropertyValueMultiple values = (PropertyValueMultiple) identity;
                            for (PropertyValue value: values.values()) {
                                j.writeString(value.toString());
                            }
                        } else {
                            // Grouped by single property. Serialize it.
                            j.writeString(identity.toString());
                        }
                    }
                    j.writeEndArray();
                    j.writeNumberField("size", group.size());
                }
                j.writeEndObject();
            }
            j.writeEndArray();
        }

        /**
         * Write frequency list config as a JSON object.
         *
         * @throws IOException
         */
        private void writeConfig() throws IOException {
            j.writeStartObject();
            {
                j.writeStringField("name", freqList.getReportName());
                j.writeFieldName("annotations");
                writeList(freqList.getAnnotations());
                j.writeFieldName("metadataFields");
                writeList(freqList.getMetadataFields());
            }
            j.writeEndObject();
        }

        /**
         * Write a List of Strings as a JSON array.
         *
         * @param l list to write
         * @throws IOException
         */
        private void writeList(List<String> l) throws IOException {
            String[] arr = l.toArray(new String[0]);
            j.writeArray(arr, 0, arr.length);
        }
    }


}
