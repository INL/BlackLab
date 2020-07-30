package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.HashSet;
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
        String propName = searchParam.getString("annotation");
        if (propName.length() == 0)
            propName = searchParam.getString("property");
        Annotation annotation = cfd.annotation(propName);
        MatchSensitivity sensitive = MatchSensitivity.caseAndDiacriticsSensitive(searchParam.getBoolean("sensitive"));
        AnnotationSensitivity sensitivity = annotation.sensitivity(sensitive);

        // May be null!
        Query q = searchParam.hasFilter() ? searchParam.getFilterQuery() : null;
        // May also null/empty to retrieve all terms!
        Set<String> terms = searchParam.getString("terms") != null ? new HashSet<>(Arrays.asList(searchParam.getString("terms").trim().split("\\s*,\\s*"))) : null;
         
        TermFrequencyList tfl = blIndex.termFrequencies(sensitivity, q, terms);
        searchLogger.setResultsFound(tfl.size());

        if (terms == null || terms.isEmpty()) { // apply pagination only when requesting all terms
            int first = searchParam.getInteger("first");
            if (first < 0 || first >= tfl.size())
                first = 0;
            int number = searchParam.getInteger("number");
            DefaultMax pageSize = searchMan.config().getParameters().getPageSize();
            if (number < 0 || number > pageSize.getMax())
                number = pageSize.getDefaultValue();
            int last = first + number;
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
