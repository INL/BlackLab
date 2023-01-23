package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Autocompletion for metadata and annotated fields. Annotations must be
 * prefixed by the annotated field in which they exist.
 */
public class RequestHandlerAutocomplete extends RequestHandler {

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
        // Get field and (optionally) annotation name from URL
        String[] pathParts = StringUtils.split(urlPathInfo, '/');
        if (pathParts.length == 0)
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Specify a field name and optionally an annotation to autocomplete.");

        String fieldNameOrAnnotation = pathParts.length > 1 ? pathParts[1] : pathParts[0];
        if (fieldNameOrAnnotation.isEmpty()) {
            throw new BadRequest("UNKNOWN_OPERATION",
                    "Bad URL. Specify a field name and optionally a annotation to autocomplete.");
        }
        String annotatedFieldName = pathParts.length > 1 ? pathParts[0] : null;
        String fieldName = StringUtils.isEmpty(annotatedFieldName) ? fieldNameOrAnnotation : annotatedFieldName;
        String annotationName = StringUtils.isEmpty(annotatedFieldName) ? "" : fieldNameOrAnnotation;
        params.setFieldName(fieldName);
        params.setAnnotationName(annotationName);

        WebserviceRequestHandler.opAutocomplete(params, ds);
        return HTTP_OK;
    }

}
