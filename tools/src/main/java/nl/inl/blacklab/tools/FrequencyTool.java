package nl.inl.blacklab.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
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
    }

    /** Configuration for making frequency lists */
    static class ConfigFreqList {

        /** A unique name that will be used as output file name */
        String name;

        /** Annotations to make frequency lists for */
        private List<String> annotations;

        /** Metadata fields to take into account (e.g. year for frequencies per year) */
        private List<String> metadataFields;

        public String getName() {
            return name;
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

    public static void main(String[] args) throws ErrorOpeningIndex {
        if (args.length < 2 || args.length > 3) {
            exit("Usage:\n\n  FrequencyTool INDEX_DIR CONFIG_FILE [OUTPUT_DIR]\n\n" +
                    "  INDEX_DIR    index to generate frequency lists for\n" +
                    "  CONFIG_FILE  YAML file specifying what frequency lists to generate\n" +
                    "  OUTPUT_DIR   where to write output files (defaults to current dir)\n");
        }

        // Open index
        File indexDir = new File(args[0]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }
        BlackLabIndex index = BlackLab.open(indexDir);

        // Read config
        File configFile = new File(args[1]);
        if (!configFile.canRead()) {
            exit("Can't read config file " + configFile);
        }
        Config config = Config.fromFile(configFile);
        AnnotatedField annotatedField = index.annotatedField(config.getAnnotatedField());

        // Output dir
        File outputDir = new File(System.getProperty("user.dir")); // current dir
        if (args.length > 2) {
            outputDir = new File(args[2]);
        }
        if (!outputDir.isDirectory() || !outputDir.canWrite()) {
            exit("Not a directory or cannot write to output dir " + outputDir);
        }

        // Generate the frequency lists
        makeFrequencyLists(index, annotatedField, config.getFrequencyLists(), outputDir);
    }

    private static void makeFrequencyLists(BlackLabIndex index, AnnotatedField annotatedField,
                                           List<ConfigFreqList> freqLists, File outputDir) {
        for (ConfigFreqList freqList: freqLists) {
            makeFrequencyList(index, annotatedField, freqList, outputDir);
        }
    }

    private static void makeFrequencyList(BlackLabIndex index, AnnotatedField annotatedField,
                                          ConfigFreqList freqList, File outputDir) {
        QueryInfo queryInfo = QueryInfo.create(index);
        SpanQueryAnyToken anyToken = new SpanQueryAnyToken(queryInfo, 1, 1, annotatedField.name());
        HitProperty groupBy = getGroupBy(index, annotatedField, freqList);
        SearchHitGroups search = index.search().find(anyToken).groupStats(groupBy, 0);
        try {
            HitGroups result = search.executeNoQueue();
            writeOutput(index, annotatedField, freqList, result, outputDir);
        } catch (InvalidQuery e) {
            throw new BlackLabRuntimeException("Error creating freqList " + freqList.getName(), e);
        }
    }

    private static HitProperty getGroupBy(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        List<HitProperty> groupProps = new ArrayList<>();
        for (String name: freqList.getAnnotations()) {
            Annotation annotation = annotatedField.annotation(name);
            groupProps.add(new HitPropertyHitText(index, annotation));
        }
        return new HitPropertyMultiple(groupProps.toArray(new HitProperty[0]));
    }

    private static String currentDateTime() {
        final String DATE_FORMAT_NOW = "yyyy-MM-dd HH:mm:ss";
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
        return sdf.format(cal.getTime());
    }

    private static void writeOutput(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
                                    HitGroups result, File outputDir) {
        File outputFile = new File(outputDir, freqList.getName() + ".json");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonFactory jfactory = new JsonFactory();
        try {
            JsonGenerator j = jfactory.createGenerator(stream, JsonEncoding.UTF8);
            j.writeStartObject();
            {
                j.writeStringField("generatedAt", currentDateTime());
                j.writeStringField("indexName", index.name());
                j.writeStringField("annotatedField", annotatedField.name());
                j.writeFieldName("config"); writeOutputConfig(j, freqList);
                j.writeFieldName("results"); writeOutputResults(j, result);
            }
            j.writeEndObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeOutputResults(JsonGenerator j, HitGroups groups) throws IOException {
        j.writeStartArray();
        for (HitGroup group: groups) {
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
                j.writeIntField("size", group.size());
            }
            j.writeEndObject();
        }
        j.writeEndArray();
    }

    private static void writeOutputConfig(JsonGenerator j, ConfigFreqList freqList) throws IOException {
        j.writeStartObject();
        {
            j.writeStringField("name", freqList.getName());
            j.writeFieldName("annotations"); writeOutputList(j, freqList.getAnnotations());
            j.writeFieldName("metadataFields"); writeOutputList(j, freqList.getMetadataFields());
        }
        j.writeEndObject();
    }

    private static void writeOutputList(JsonGenerator j, List<String> l) throws IOException {
        String[] arr = l.toArray(new String[0]);
        j.writeArray(arr, 0, arr.length);
    }

}
