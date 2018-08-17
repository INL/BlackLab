package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for term frequencies for a set of documents.
 */
public class RequestHandlerTermFreq extends RequestHandler {

    public RequestHandlerTermFreq(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        //TODO: use background job?

        BlackLabIndex blIndex = blIndex();
        AnnotatedField cfd = blIndex.mainAnnotatedField();
        String propName = searchParam.getString("property");
        Annotation annotation = cfd.annotation(propName);
        MatchSensitivity sensitive = MatchSensitivity.caseAndDiacriticsSensitive(searchParam.getBoolean("sensitive"));
        AnnotationSensitivity sensitivity = annotation.sensitivity(sensitive);

        Query q = searchParam.getFilterQuery();
        if (q == null)
            return Response.badRequest(ds, "NO_FILTER_GIVEN",
                    "Document filter required. Please specify 'filter' parameter.");
        
        TermFrequencyList tfl = blIndex.termFrequencies(sensitivity, q);

        int first = searchParam.getInteger("first");
        if (first < 0 || first >= tfl.size())
            first = 0;
        int number = searchParam.getInteger("number");
        if (number < 0 || number > searchMan.config().maxPageSize())
            number = searchMan.config().defaultPageSize();
        int last = first + number;
        if (last > tfl.size())
            last = tfl.size();

        // Assemble all the parts
        ds.startMap();
        ds.startEntry("termFreq").startMap();
        //DataObjectMapAttribute termFreq = new DataObjectMapAttribute("term", "text");
        for (TermFrequency tf : tfl.subList(first, last)) {
            ds.attrEntry("term", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

}
