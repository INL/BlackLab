package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.perdocument.DocGroup;
import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
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
	public int handle(DataStream ds) throws BlsException {
		// Get the window we're interested in
		JobDocsGrouped search = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped(), isBlockingOperation());
		try {
			// If search is not done yet, indicate this to the user
			if (!search.finished()) {
				return Response.busy(ds, servlet);
			}

			// Search is done; construct the results object

			DocResults docResults = search.getDocResults();
			Hits hits = docResults.getOriginalHits();
			DocGroups groups = search.getGroups();
			int first = searchParam.getInteger("first");
			if (first < 0)
				first = 0;
			int number = searchParam.getInteger("number");
			if (number < 0 || number > searchMan.config().maxPageSize())
				number = searchMan.config().defaultPageSize();
			int numberOfGroupsInWindow = 0;
			numberOfGroupsInWindow = number;
			if (first + number > groups.numberOfGroups())
				numberOfGroupsInWindow = groups.numberOfGroups() - first;

			ds.startMap();

			// The summary
			ds.startEntry("summary").startMap();
			ds.startEntry("searchParam");
			searchParam.dataStream(ds);
			ds.endEntry();
			ds	.entry("searchTime", (int)(search.userWaitTime() * 1000))
				.entry("stillCounting", false);
			if (hits != null) {
				ds	.entry("numberOfHits", hits.countSoFarHitsCounted())
					.entry("numberOfHitsRetrieved", hits.countSoFarHitsRetrieved())
					.entry("stoppedCountingHits", hits.maxHitsCounted())
					.entry("stoppedRetrievingHits", hits.maxHitsRetrieved());
			}
			if (hits instanceof HitsSample) {
				HitsSample sample = ((HitsSample)hits);
				ds.entry("sampleSeed", sample.seed());
				if (sample.exactNumberGiven())
					ds.entry("sampleSize", sample.numberOfHitsToSelect());
				else
					ds.entry("samplePercentage", Math.round(sample.ratio() * 100 * 100) / 100.0);
			}
			ds	.entry("numberOfDocs", docResults.countSoFarDocsCounted())
				.entry("numberOfDocsRetrieved", docResults.countSoFarDocsRetrieved())
				.entry("numberOfGroups", groups.numberOfGroups())
				.entry("windowFirstResult", first)
				.entry("requestedWindowSize", number)
				.entry("actualWindowSize", numberOfGroupsInWindow)
				.entry("windowHasPrevious", first > 0)
				.entry("windowHasNext", first + number < groups.numberOfGroups())
				.entry("largestGroupSize", groups.getLargestGroupSize());
			ds.endMap().endEntry();

			// The list of groups found
			ds.startEntry("docGroups").startList();
			int i = 0;
			for (DocGroup group: groups) {
				if (i >= first && i < first + number) {
					ds.startItem("docgroup").startMap()
						.entry("identity", group.getIdentity().serialize())
						.entry("identityDisplay", group.getIdentity().toString())
						.entry("size", group.size())
					.endMap().endItem();
				}
				i++;
			}
			ds.endList().endEntry();

			ds.endMap();

			return HTTP_OK;
		} finally {
			search.decrRef();
		}
	}

	@Override
	protected boolean isDocsOperation() {
		return true;
	}

}
