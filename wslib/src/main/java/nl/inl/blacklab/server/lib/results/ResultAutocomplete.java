package nl.inl.blacklab.server.lib.results;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.util.LuceneUtil;

public class ResultAutocomplete {

    private static final int MAX_VALUES = 30;

    private final List<String> terms;

    ResultAutocomplete(WebserviceParams params) {
        String fieldName = params.getFieldName();
        String annotationName = params.getAnnotationName();

        // Annotated field specified but no annotation?
        if (annotationName == null && params.blIndex().metadata().annotatedFields().exists(fieldName))
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Also specify a annotation to autocomplete for annotated field: " + fieldName);

        BlackLabIndex index = params.blIndex();
        IndexMetadata indexMetadata = index.metadata();

        String term = params.getAutocompleteTerm();
        if (StringUtils.isEmpty(term))
            throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Pass a parameter 'term' to autocomplete.");

        /*
         * Rather specific code:
         * We require the exact name of the annotation in the lucene index in order to find autocompletion results
         *
         * For metadata fields this is just the value as specified in the IndexMetadata,
         * but word properties have multiple internal names.
         * The annotation is part of a "annotatedField", and (usually) has multiple variants ("sensitivities") for
         * case/accent-sensitive/insensitive versions. The name needs to account for all of these things.
         *
         * By default, get the insensitive variant of the field (if present), otherwise, get whatever is the default.
         *
         * Take care to pass the sensitivity we're using
         * or we might match insensitively on a field that only contains sensitive data, or vice versa
         */
        boolean sensitiveMatching = true;
        String luceneField;
        if (!StringUtils.isEmpty(annotationName)) {
            // Annotation on annotated field
            if (!indexMetadata.annotatedFields().exists(fieldName))
                throw new BadRequest("UNKNOWN_FIELD", "Annotated field '" + fieldName + "' does not exist.");
            AnnotatedField annotatedField = indexMetadata.annotatedField(fieldName);
            Annotations annotations = annotatedField.annotations();
            if (!annotations.exists(annotationName))
                throw new BadRequest("UNKNOWN_ANNOTATION",
                        "Annotated field '" + fieldName + "' has no annotation '" + annotationName + "'.");
            Annotation annotation = annotations.get(annotationName);
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)) {
                sensitiveMatching = false;
                luceneField = annotation.sensitivity(MatchSensitivity.INSENSITIVE).luceneField();
            } else {
                sensitiveMatching = true;
                AnnotationSensitivity s = annotation.offsetsSensitivity(); // TODO: why!? get rid of this?
                if (s == null)
                    s = annotation.sensitivity(MatchSensitivity.SENSITIVE);
                luceneField = s.luceneField();
            }
        } else {
            luceneField = fieldName;
        }
        IndexReader reader = index.reader();

        terms = LuceneUtil.findTermsByPrefix(reader, luceneField, term, sensitiveMatching, MAX_VALUES);

    }

    public List<String> getTerms() {
        return terms;
    }
}
