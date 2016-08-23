package nl.inl.blacklab.server.requesthandlers;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.util.LuceneUtil;

/**
 * Request handler for term frequencies for a set of documents.
 */
public class RequestHandlerTermFreq extends RequestHandler {

	public RequestHandlerTermFreq(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		//TODO: use background job?

		Searcher searcher = getSearcher();
		IndexStructure struct = searcher.getIndexStructure();
		ComplexFieldDesc cfd = struct.getMainContentsField();
		String propName = searchParam.getString("property");
		boolean sensitive = searchParam.getBoolean("sensitive");

		Query q = searchParam.getFilterQuery();
		if (q == null)
			return Response.badRequest(ds, "NO_FILTER_GIVEN", "Document filter required. Please specify 'filter' parameter.");
		Map<String, Integer> freq = LuceneUtil.termFrequencies(searcher.getIndexSearcher(), q, cfd.getName(), propName, sensitive ? "s" : "i");

		TermFrequencyList tfl = new TermFrequencyList(freq.size());
		for (Map.Entry<String, Integer> e: freq.entrySet()) {
			tfl.add(new TermFrequency(e.getKey(), e.getValue()));
		}
		tfl.sort();

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
		for (TermFrequency tf: tfl.subList(first, last)) {
			ds.attrEntry("term", "text", tf.term, tf.frequency);
		}
		ds.endMap().endEntry();
		ds.endMap();

		return HTTP_OK;
	}


}
