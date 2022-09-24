package nl.inl.blacklab.server.requesthandlers;

import java.util.Set;

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
import nl.inl.blacklab.server.config.DefaultMax;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;

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
        String annotName = searchParam.par().getAnnotation();
        Annotation annotation = cfd.annotation(annotName);
        MatchSensitivity sensitive = MatchSensitivity.caseAndDiacriticsSensitive(searchParam.par().getSensitive());
        AnnotationSensitivity sensitivity = annotation.sensitivity(sensitive);

        // May be null!
        Query q = searchParam.hasFilter() ? searchParam.filterQuery() : null;
        // May also null/empty to retrieve all terms!
        Set<String> terms = searchParam.par().getTerms();
        TermFrequencyList tfl = blIndex.termFrequencies(sensitivity, q, terms);

        if (terms == null || terms.isEmpty()) { // apply pagination only when requesting all terms
            long first = searchParam.par().getFirstResultToShow();
            if (first < 0 || first >= tfl.size())
                first = 0;
            long number = searchParam.par().getNumberOfResultsToShow();
            DefaultMax pageSize = searchMan.config().getParameters().getPageSize();
            if (number < 0 || number > pageSize.getMax())
                number = pageSize.getDefaultValue();
            long last = first + number;
            if (last > tfl.size())
                last = tfl.size();

            tfl = tfl.subList(first, last);
        }

        // Assemble all the parts
        ds.startMap();
        ds.startEntry("termFreq").startMap();
        //DataObjectMapAttribute termFreq = new DataObjectMapAttribute("term", "text");
        for (TermFrequency tf : tfl) {
            ds.attrEntry("term", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry();
        ds.endMap();

        return HTTP_OK;
    }

}
