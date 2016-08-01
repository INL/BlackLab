package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.perdocument.DocGroup;
import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectList;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.JobDocsGrouped;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for grouped doc results.
 */
public class RequestHandlerDocsGrouped extends RequestHandler {
	public RequestHandlerDocsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {
		// Get the window we're interested in
		JobDocsGrouped search = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped(), getBoolParameter("block"));
		try {
			// If search is not done yet, indicate this to the user
			if (!search.finished()) {
				return Response.busy(servlet);
			}

			// Search is done; construct the results object
			DocGroups groups = search.getGroups();

			DataObjectList doGroups = null;
			// The list of groups found
			// TODO paging..?
			doGroups = new DataObjectList("docgroup");
			int first = searchParam.getInteger("first");
			if (first < 0)
				first = 0;
			int number = searchParam.getInteger("number");
			if (number < 0 || number > searchMan.getMaxPageSize())
				number = searchMan.getDefaultPageSize();
			int i = 0;
			for (DocGroup group: groups) {
				if (i >= first && i < first + number) {
					DataObjectMapElement doGroup = new DataObjectMapElement();
					doGroup.put("identity", group.getIdentity().serialize());
					doGroup.put("identityDisplay", group.getIdentity().toString());
					doGroup.put("size", group.size());
					doGroups.add(doGroup);
				}
				i++;
			}

			// The summary
			DataObjectMapElement summary = new DataObjectMapElement();
			DocResults docResults = search.getDocResults();
			Hits hits = docResults.getOriginalHits();
			summary.put("searchParam", searchParam.toDataObject());
			summary.put("searchTime", (int)(search.userWaitTime() * 1000));
			summary.put("stillCounting", false);
			if (hits != null) {
				summary.put("numberOfHits", hits.countSoFarHitsCounted());
				summary.put("numberOfHitsRetrieved", hits.countSoFarHitsRetrieved());
				summary.put("stoppedCountingHits", hits.maxHitsCounted());
				summary.put("stoppedRetrievingHits", hits.maxHitsRetrieved());
			}
			summary.put("numberOfDocs", docResults.countSoFarDocsCounted());
			summary.put("numberOfDocsRetrieved", docResults.countSoFarDocsRetrieved());
			summary.put("numberOfGroups", groups.numberOfGroups());
			summary.put("windowFirstResult", first);
			summary.put("requestedWindowSize", number);
			summary.put("actualWindowSize", doGroups.size());
			summary.put("windowHasPrevious", first > 0);
			summary.put("windowHasNext", first + number < groups.numberOfGroups());
			summary.put("largestGroupSize", groups.getLargestGroupSize());

			// Assemble all the parts
			DataObjectMapElement response = new DataObjectMapElement();
			response.put("summary", summary);
			response.put("docGroups", doGroups);

			return new Response(response);
		} finally {
			search.decrRef();
		}
	}

	@Override
	protected boolean isDocsOperation() {
		return true;
	}

}
