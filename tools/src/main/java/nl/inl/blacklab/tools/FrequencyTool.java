package nl.inl.blacklab.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.searches.SearchHitGroups;

/**
 * Determine frequency lists over annotation(s) and
 * metadata field(s) for the entire index.
 */
public class FrequencyTool {

    /** Configuration for making frequency lists */
    static class Config {

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

    public static void main(String[] args) {
        if (args.length != 2) {
            exit("Usage:\n\n  FrequencyTool INDEX_DIR CONFIG_FILE\n\n" +
                    "  INDEX_DIR    index to generate frequency lists for\n" +
                    "  CONFIG_FILE  YAML file specifying what frequency lists to generate\n");
        }
        File indexDir = new File(args[0]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }
        BlackLabIndex index = BlackLabIndex.open(indexDir);
        File configFile = new File(args[1]);
        if (!configFile.canRead()) {
            exit("Can't read config file " + configFile);
        }
        Config config = Config.fromFile(configFile);

        AnnotatedField annotatedField = index.annotatedField(config.getAnnotatedField());

        makeFrequencyLists(index, annotatedField, config.getFrequencyLists());
    }

    private static void makeFrequencyLists(BlackLabIndex index, AnnotatedField annotatedField,
                                           List<ConfigFreqList> freqLists) {
        for (ConfigFreqList freqList: freqLists) {
            makeFrequencyList(index, annotatedField, freqList);
        }
    }

    private static void makeFrequencyList(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList) {
        SpanQueryAnyToken anyToken = new SpanQueryAnyToken();
        HitProperty groupBy = getGroupBy(index, annotatedField, freqList);
        SearchHitGroups search = index.search().find(anyToken).groupStats(groupBy, 0);
        try {
            HitGroups result = search.executeNoQueue();
            writeOutput(index, annotatedField, freqList, result);
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
        HitProperty groupBy = new HitPropertyMultiple(groupProps.toArray(new HitProperty[0]));
        return groupBy;
    }

    private static void writeOutput(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList, HitGroups result) {
        File outputFile = new File(freqList.getName() + ".json");

        
    }

}
