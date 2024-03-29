package nl.inl.blacklab.indexers.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nl.inl.util.StringUtil;

public abstract class DocIndexerTabularBase extends DocIndexerConfig {

    protected String multipleValuesSeparatorRegex;

    public DocIndexerTabularBase(String multipleValuesSeparatorRegex) {
        super();
        this.multipleValuesSeparatorRegex = multipleValuesSeparatorRegex;
    }

    protected void indexValue(ConfigAnnotation annotation, String value) {
        // Remove unwanted unprintable characters and normalize to canonical unicode composition
        value = StringUtil.sanitizeAndNormalizeUnicode(value);

        if (annotation.isMultipleValues()) {
            // Multiple values possible. Split on multipleValuesSeparator.
            List<String> values = processStringMultipleValues(value, annotation.getProcess(), null);
            Stream<String> valueStream = values.stream();
            if (annotation.getProcess().isEmpty()) {
                // No explicit processing steps defined.
                // Perform the split processing step that is implicit for tabular formats.
                valueStream = valueStream
                        .map(v -> Arrays.asList(v.split(multipleValuesSeparatorRegex, -1)))
                        .flatMap(List::stream);
            }
            if (!annotation.isAllowDuplicateValues()) {
                // Discard any duplicate values from the list
                valueStream = valueStream.distinct();
            }
            values = valueStream.collect(Collectors.toList());
            boolean first = true;
            for (String v: values) {
                annotationValueAppend(annotation.getName(), v, first ? 1 : 0);
                first = false;
            }
        } else {
            // Single value.
            value = processString(value, annotation.getProcess(), null);
            annotationValueAppend(annotation.getName(), value, 1);
        }
    }
}
