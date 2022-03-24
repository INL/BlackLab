package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Configuration for making frequency lists
 */
class Config {

    /**
     * Read config from file.
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

    /**
     * Annotated field to analyze
     */
    private String annotatedField;

    /**
     * Frequency lists to make
     */
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
        for (ConfigFreqList l : frequencyLists) {
            String name = l.getReportName();
            if (reportNames.contains(name))
                throw new IllegalArgumentException("Report occurs twice: " + name);
            reportNames.add(name);

            for (String a : l.getAnnotations()) {
                if (!af.annotations().exists(a))
                    throw new IllegalArgumentException("Annotation not found: " + annotatedField + "." + a);
            }
            for (String m : l.getMetadataFields()) {
                if (!index.metadataFields().exists(m))
                    throw new IllegalArgumentException("Metadata field not found: " + m);
            }
        }
    }
}
