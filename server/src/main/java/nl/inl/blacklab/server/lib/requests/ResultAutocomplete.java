package nl.inl.blacklab.server.lib.requests;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.SearchCreator;

public class ResultAutocomplete {
    private String luceneField;

    private String term;

    private boolean sensitiveMatching;

    private IndexReader reader;

    public ResultAutocomplete(String luceneField, String term, boolean sensitiveMatching, IndexReader reader) {
        this.luceneField = luceneField;
        this.term = term;
        this.sensitiveMatching = sensitiveMatching;
        this.reader = reader;
    }

    static ResultAutocomplete get(SearchCreator params) {
        String fieldName = params.getFieldName();
        String annotationName = params.getAnnotationName();

        // Annotated field specified but no annotation?
        if (annotationName == null && params.blIndex().metadata().annotatedFields().exists(fieldName))
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Also specify a annotation to autocomplete for annotated field: " + fieldName);

        BlackLabIndex index = params.blIndex();
        IndexMetadata indexMetadata = index.metadata();

        String term = params.getAutocompleteTerm();
        if (term == null || term.isEmpty())
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
                luceneField = annotation.offsetsSensitivity().luceneField();
            }
        } else {
            luceneField = fieldName;
        }
        ResultAutocomplete result = new ResultAutocomplete(luceneField, term, sensitiveMatching, index.reader());
        return result;
    }

    public String getLuceneField() {
        return luceneField;
    }

    public String getTerm() {
        return term;
    }

    public boolean isSensitiveMatching() {
        return sensitiveMatching;
    }

    public IndexReader getReader() {
        return reader;
    }
}
