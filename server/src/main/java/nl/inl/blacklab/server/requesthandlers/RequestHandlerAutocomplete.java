package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Annotations;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.LuceneUtil;

/**
 * Autocompletion for metadata and annotated fields. Annotations must be
 * prefixed by the annotated field in which they exist.
 */
public class RequestHandlerAutocomplete extends RequestHandler {

    private static final int MAX_VALUES = 30;

    public RequestHandlerAutocomplete(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // Because reindexing might change something
    }

    @Override
    public int handle(DataStream ds) throws BlsException {

        String[] pathParts = StringUtils.split(urlPathInfo, '/');
        if (pathParts.length == 0)
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Specify a field name and optionally a annotation to autocomplete.");

        String annotatedFieldName = pathParts.length > 1 ? pathParts[0] : null;
        String fieldName = pathParts.length > 1 ? pathParts[1] : pathParts[0];
        String term = searchParam.getString("term");

        if (fieldName.isEmpty()) {
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Specify a field name and optionally a annotation to autocomplete.");
        }
        BlackLabIndex blIndex = blIndex();
        IndexMetadata indexMetadata = blIndex.metadata();
        if (annotatedFieldName == null && indexMetadata.annotatedFields().exists(fieldName))
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Also specify a annotation to autocomplete for annotated field: " + fieldName);

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
        if (annotatedFieldName != null && !annotatedFieldName.isEmpty()) {
            if (!indexMetadata.annotatedFields().exists(annotatedFieldName))
                throw new BadRequest("UNKNOWN_FIELD", "Annotated field '" + annotatedFieldName + "' does not exist.");
            AnnotatedField annotatedField = indexMetadata.annotatedField(annotatedFieldName);
            Annotations annotations = annotatedField.annotations();
            if (!annotations.exists(fieldName))
                throw new BadRequest("UNKNOWN_PROPERTY",
                        "Annotated field '" + annotatedFieldName + "' has no annotation '" + fieldName + "'.");
            Annotation annotation = annotations.get(fieldName);
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)) {
                sensitiveMatching = false;
                fieldName = annotation.sensitivity(MatchSensitivity.INSENSITIVE).luceneField();
            } else {
                sensitiveMatching = true;
                fieldName = annotation.offsetsSensitivity().luceneField();
            }
        }

        autoComplete(ds, fieldName, term, blIndex.reader(), sensitiveMatching);
        return HTTP_OK;
    }

    public static void autoComplete(DataStream ds, String fieldName, String term, IndexReader reader,
            boolean sensitive) {
        ds.startList();
        LuceneUtil.findTermsByPrefix(reader, fieldName, term, sensitive, MAX_VALUES).forEach((v) -> {
            ds.item("term", v);
        });
        ds.endList();
    }

}
