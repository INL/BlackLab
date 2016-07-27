package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.perdocument.DocGroup;
import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyComplexFieldLength;
import nl.inl.blacklab.perdocument.DocResult;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.perdocument.DocResultsWindow;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.grouping.HitPropValue;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectContextList;
import nl.inl.blacklab.server.dataobject.DataObjectList;
import nl.inl.blacklab.server.dataobject.DataObjectMapAttribute;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.dataobject.DataObjectPlain;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.Job;
import nl.inl.blacklab.server.search.JobDocsGrouped;
import nl.inl.blacklab.server.search.JobDocsTotal;
import nl.inl.blacklab.server.search.JobDocsWindow;
import nl.inl.blacklab.server.search.SearchCache;
import nl.inl.blacklab.server.search.User;

import org.apache.lucene.document.Document;

/**
 * Request handler for the doc results.
 */
public class RequestHandlerDocs extends RequestHandler {
	public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {
		// Do we want to view a single group after grouping?
		String groupBy = searchParam.getString("group");
		if (groupBy == null)
			groupBy = "";
		String viewGroup = searchParam.getString("viewgroup");
		if (viewGroup == null)
			viewGroup = "";
		Job search = null;
		JobDocsGrouped searchGrouped = null;
		JobDocsWindow searchWindow = null;
		JobDocsTotal total = null;
		try {
			DocResultsWindow window;
			DocGroup group = null;
			boolean block = getBoolParameter("block");
			if (groupBy.length() > 0 && viewGroup.length() > 0) {

				// TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

				// Yes. Group, then show hits from the specified group
				searchGrouped = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped());
				search = searchGrouped;
				search.incrRef();
				if (block) {
					search.waitUntilFinished(SearchCache.maxSearchTimeSec * 1000);
					if (!search.finished())
						return Response.searchTimedOut();
				}

				// If search is not done yet, indicate this to the user
				if (!search.finished()) {
					return Response.busy(servlet);
				}

				// Search is done; construct the results object
				DocGroups groups = searchGrouped.getGroups();

				HitPropValue viewGroupVal = null;
				viewGroupVal = HitPropValue.deserialize(groups.getOriginalDocResults().getOriginalHits(), viewGroup);
				if (viewGroupVal == null)
					return Response.badRequest("ERROR_IN_GROUP_VALUE", "Parameter 'viewgroup' has an illegal value: " + viewGroup);

				group = groups.getGroup(viewGroupVal);
				if (group == null)
					return Response.badRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

				String sortBy = searchParam.getString("sort");
				DocProperty sortProp = sortBy != null && sortBy.length() > 0 ? DocProperty.deserialize(sortBy) : null;
				DocResults docsSorted;
				if (sortProp != null) {
					docsSorted = group.getResults();
					docsSorted.sort(sortProp, false);
				} else
					docsSorted = group.getResults();

				int first = searchParam.getInteger("first");
				if (first < 0)
					first = 0;
				int number = searchParam.getInteger("number");
				if (number < 0 || number > searchMan.getMaxPageSize())
					number = searchMan.getDefaultPageSize();
				window = docsSorted.window(first, number);

			} else {
				// Regular set of docs (no grouping first)

				searchWindow = (JobDocsWindow) searchMan.search(user, searchParam.docsWindow());
				search = searchWindow;
				search.incrRef();
				if (block) {
					search.waitUntilFinished(SearchCache.maxSearchTimeSec * 1000);
					if (!search.finished())
						return Response.searchTimedOut();
				}

				// Also determine the total number of hits
				// (usually nonblocking, unless "waitfortotal=yes" was passed)
				total = (JobDocsTotal) searchMan.search(user, searchParam.docsTotal());
				if (searchParam.getBoolean("waitfortotal")) {
					total.waitUntilFinished(SearchCache.maxSearchTimeSec * 1000);
					if (!total.finished())
						return Response.searchTimedOut();
				}

				// If search is not done yet, indicate this to the user
				if (!search.finished()) {
					return Response.busy(servlet);
				}

				window = searchWindow.getWindow();
			}

			String parFacets = searchParam.getString("facets");
			DataObjectMapAttribute doFacets = null;
			if (parFacets != null && parFacets.length() > 0) {
				// Now, group the docs according to the requested facets.
				//TODO: use background job?
				DocResults docsToFacet = window.getOriginalDocs();
				doFacets = getFacets(docsToFacet, parFacets);
			}

			Searcher searcher = search.getSearcher();

			boolean includeTokenCount = searchParam.getBoolean("includetokencount");
			int totalTokens = -1;
			if (includeTokenCount) {
				// Determine total number of tokens in result set
				//TODO: use background job?
				String fieldName = searcher.getIndexStructure().getMainContentsField().getName();
				DocProperty propTokens = new DocPropertyComplexFieldLength(fieldName);
				totalTokens = window.getOriginalDocs().intSum(propTokens);
			}

			// Search is done; construct the results object

			// The hits and document info
			DataObjectList docList = new DataObjectList("doc");
			for (DocResult result: window) {
				// Doc info (metadata, etc.)
				Document document = result.getDocument();
				DataObjectMapElement docInfo = getDocumentInfo(searcher, document);

				// Snippets
				Hits hits = result.getHits(5); // TODO: make num. snippets configurable
				DataObjectList doSnippetList = null;
				if (hits.sizeAtLeast(1)) {
					doSnippetList = new DataObjectList("snippet");
					for (Hit hit: hits) {

						// TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()

						DataObjectMapElement hitMap = new DataObjectMapElement();
						if (searchParam.getString("usecontent").equals("orig")) {
							// Add concordance from original XML
							Concordance c = hits.getConcordance(hit);
							hitMap.put("left", new DataObjectPlain(c.left()));
							hitMap.put("match", new DataObjectPlain(c.match()));
							hitMap.put("right", new DataObjectPlain(c.right()));
							doSnippetList.add(hitMap);
						} else {
							// Add KWIC info
							Kwic c = hits.getKwic(hit);
							hitMap.put("left", new DataObjectContextList(c.getProperties(), c.getLeft()));
							hitMap.put("match", new DataObjectContextList(c.getProperties(), c.getMatch()));
							hitMap.put("right", new DataObjectContextList(c.getProperties(), c.getRight()));
							doSnippetList.add(hitMap);
						}
					}
				}

				// Find pid
				String pid = getDocumentPid(searcher, result.getDocId(), document);

				// Combine all
				DataObjectMapElement docMap = new DataObjectMapElement();
				docMap.put("docPid", pid);
				int numHits = result.getNumberOfHits();
				if (numHits > 0)
					docMap.put("numberOfHits", numHits);
				docMap.put("docInfo", docInfo);
				if (doSnippetList != null)
					docMap.put("snippets", doSnippetList);

				docList.add(docMap);
			}

			// The summary (done last because the count might be done by this time)
			DataObjectMapElement summary = new DataObjectMapElement();
			DocResults docs = searchWindow != null ? total.getDocResults() : group.getResults();
			Hits hits = docs.getOriginalHits();
			boolean done = hits == null ? true : hits.doneFetchingHits();
			summary.put("searchParam", searchParam.toDataObject());
			summary.put("searchTime", (int)(search.userWaitTime() * 1000));
			if (total != null)
				summary.put("countTime", (int)(total.userWaitTime() * 1000));
			summary.put("stillCounting", !done);
			if (searchGrouped == null && hits != null) {
				int numberOfHitsCounted = hits.countSoFarHitsCounted();
				if (total != null && total.threwException())
					numberOfHitsCounted = -1;
				summary.put("numberOfHits", numberOfHitsCounted);
				summary.put("numberOfHitsRetrieved", hits.countSoFarHitsRetrieved());
				summary.put("stoppedCountingHits", hits.maxHitsCounted());
				summary.put("stoppedRetrievingHits", hits.maxHitsRetrieved());
			}
			if (hits != null || group != null) {
				int numberOfDocsCounted = hits == null ? group.getResults().size() : hits.countSoFarDocsCounted();
				if (total != null && total.threwException())
					numberOfDocsCounted = -1;
				summary.put("numberOfDocs", numberOfDocsCounted);
				summary.put("numberOfDocsRetrieved", hits == null ? group.getResults().size() : hits.countSoFarDocsRetrieved());
			} else {
				summary.put("numberOfDocs", docs.countSoFarDocsCounted());
				summary.put("numberOfDocsRetrieved", docs.countSoFarDocsRetrieved());
			}
			summary.put("windowFirstResult", window.first());
			summary.put("requestedWindowSize", searchParam.getInteger("number"));
			summary.put("actualWindowSize", window.size());
			summary.put("windowHasPrevious", window.hasPrevious());
			summary.put("windowHasNext", window.hasNext());
			if (includeTokenCount)
				summary.put("tokensInMatchingDocuments", totalTokens);
			summary.put("docFields", RequestHandler.getDocFields(searcher.getIndexStructure()));

			// Assemble all the parts
			DataObjectMapElement response = new DataObjectMapElement();
			response.put("summary", summary);
			response.put("docs", docList);
			if (doFacets != null)
				response.put("facets", doFacets);

			return new Response(response);
		} finally {
			if (search != null)
				search.decrRef();
			if (searchWindow != null)
				searchWindow.decrRef();
			if (searchGrouped != null)
				searchGrouped.decrRef();
			if (total != null)
				total.decrRef();
		}
	}


}
