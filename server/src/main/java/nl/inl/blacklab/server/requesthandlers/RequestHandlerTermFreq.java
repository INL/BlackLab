package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.WebserviceOperations;

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
        TermFrequencyList tfl = WebserviceOperations.getTermFrequencies(params, searchMan);
        dstreamTermFreqResponse(ds, tfl);
        return HTTP_OK;
    }

    private static void dstreamTermFreqResponse(DataStream ds, TermFrequencyList tfl) {
        // Assemble all the parts
        ds.startMap();
        ds.startEntry("termFreq").startMap();
        //DataObjectMapAttribute termFreq = new DataObjectMapAttribute("term", "text");
        for (TermFrequency tf : tfl) {
            ds.attrEntry("term", "text", tf.term, tf.frequency);
        }
        ds.endMap().endEntry();
        ds.endMap();
    }

}
