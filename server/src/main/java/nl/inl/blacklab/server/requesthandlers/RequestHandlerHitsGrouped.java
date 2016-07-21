package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitGroup;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectList;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.JobHitsGrouped;
import nl.inl.blacklab.server.search.SearchCache;
import nl.inl.blacklab.server.search.User;

/**
 * Request handler for grouped hit results.
 */
public class RequestHandlerHitsGrouped extends RequestHandler {
	public RequestHandlerHitsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {
		//logger.debug("@PERF RHHitsGrouped: START");

		// Get the window we're interested in
		JobHitsGrouped search = searchMan.searchHitsGrouped(user, searchParam);
		try {
			if (getBoolParameter("block")) {
				//logger.debug("@PERF RHHitsGrouped: block");
				search.waitUntilFinished(SearchCache.maxSearchTimeSec * 1000);
				if (!search.finished()) {
					//logger.debug("@PERF RHHitsGrouped: block, timed out");
					return Response.searchTimedOut();
				}
				//logger.debug("@PERF RHHitsGrouped: block, finished");
			}

			// If search is not done yet, indicate this to the user
			if (!search.finished()) {
				//logger.debug("@PERF RHHitsGrouped: busy");
				return Response.busy(servlet);
			}

			// Search is done; construct the results object
			//logger.debug("@PERF RHHitsGrouped: get groups");
			HitGroups groups = search.getGroups();

			//logger.debug("@PERF RHHitsGrouped: construct results");
			DataObjectList doGroups = null;
			// The list of groups found
			// TODO paging..?
			doGroups = new DataObjectList("hitgroup");
			int first = searchParam.getInteger("first");
			if (first < 0)
				first = 0;
			int number = searchParam.getInteger("number");
			if (number < 0 || number > searchMan.getMaxPageSize())
				number = searchMan.getDefaultPageSize();
			int i = 0;
			for (HitGroup group: groups) {
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
			Hits hits = search.getHits();
			summary.put("searchParam", searchParam.toDataObject());
			summary.put("searchTime", (int)(search.userWaitTime() * 1000));
			summary.put("stillCounting", false);
			summary.put("numberOfHits", hits.countSoFarHitsCounted());
			summary.put("numberOfHitsRetrieved", hits.countSoFarHitsRetrieved());
			summary.put("stoppedCountingHits", hits.maxHitsCounted());
			summary.put("stoppedRetrievingHits", hits.maxHitsRetrieved());
			summary.put("numberOfDocs", hits.countSoFarDocsCounted());
			summary.put("numberOfDocsRetrieved", hits.countSoFarDocsRetrieved());
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
			response.put("hitGroups", doGroups);

			//logger.debug("@PERF RHHitsGrouped: DONE");

			return new Response(response);
		} finally {
			search.decrRef();
		}
	}

}
