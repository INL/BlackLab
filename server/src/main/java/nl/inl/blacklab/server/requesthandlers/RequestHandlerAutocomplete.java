package nl.inl.blacklab.server.requesthandlers;


import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.LuceneUtil;

/**
 * Autocompletion for metadata and property fields.
 * Property fields must be prefixed by the complexField in which they exist.
 */
public class RequestHandlerAutocomplete extends RequestHandler {

    private static final int MAX_VALUES = 30;

    public RequestHandlerAutocomplete(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
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
        	throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Specify a field name and optionally a property to autocomplete.");

        String complexFieldName = pathParts.length > 1 ? pathParts[0] : null;
        String fieldName        = pathParts.length > 1 ? pathParts[1] : pathParts[0];
        String term = searchParam.getString("term");

        if (fieldName.isEmpty()) {
            throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Specify a field name and optionally a property to autocomplete.");
        }
        Searcher searcher = getSearcher();
        IndexStructure struct = searcher.getIndexStructure();
        if (complexFieldName == null && struct.getComplexFields().contains(fieldName))
            throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Also specify a property to autocomplete for complexfield: " + fieldName);

        if (term == null || term.isEmpty())
            throw new BadRequest("UNKNOWN_OPERATION", "Bad URL. Pass a parameter 'term' to autocomplete.");
        
        /*
         * Rather specific code:
         * We require the exact name of the property in the lucene index in order to find autocompletion results
         *
         * For metadata fields this is just the value as specified in the IndexStructure,
         * but word properties have multiple internal names.
         * the property is part of a "complexField", and (usually) has multiple variants for case/accent-sensitive/insensitive versions.
         * The name needs to account for all of these things.
         *
         * By default, get the insensitive variant of the field (if present), otherwise, get whatever is the default.
         *
         * Take care to pass the sensitivity we're using
         * or we might match insensitively on a field that only contains sensitive data, or vice versa
         */
        boolean sensitiveMatching = true;
        if (complexFieldName != null && !complexFieldName.isEmpty()) {
            if (!struct.hasComplexField(complexFieldName))
                throw new BadRequest("UNKNOWN_FIELD", "Complex field '" + complexFieldName + "' does not exist.");
        	ComplexFieldDesc complexFieldDesc = struct.getComplexFieldDesc(complexFieldName);
        	if (!complexFieldDesc.hasProperty(fieldName))
                throw new BadRequest("UNKNOWN_PROPERTY", "Complex field '" + complexFieldName + "' has no property '" + fieldName + "'.");
            PropertyDesc prop = complexFieldDesc.getPropertyDesc(fieldName);
        	if (prop.hasAlternative(ComplexFieldUtil.INSENSITIVE_ALT_NAME)) {
        		sensitiveMatching = false;
        		fieldName = ComplexFieldUtil.propertyField(complexFieldName, fieldName, ComplexFieldUtil.INSENSITIVE_ALT_NAME);
        	} else {
        		sensitiveMatching = true;
        		fieldName = ComplexFieldUtil.propertyField(complexFieldName, fieldName, prop.offsetsAlternative());
        	}
        }

    	autoComplete(ds, fieldName, term, searcher.getIndexReader(), sensitiveMatching);
        return HTTP_OK;
    }

    public static void autoComplete(DataStream ds, String fieldName, String term, IndexReader reader, boolean sensitive) {
        ds.startList();
        LuceneUtil.findTermsByPrefix(reader, fieldName, term, sensitive, MAX_VALUES).forEach((v) -> {
            ds.item("term", v);
        });
        ds.endList();
    }

}
