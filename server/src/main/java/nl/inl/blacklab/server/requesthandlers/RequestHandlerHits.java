package nl.inl.blacklab.server.requesthandlers;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyComplexFieldLength;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.grouping.HitGroup;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitPropValue;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.blacklab.server.jobs.JobHitsGrouped;
import nl.inl.blacklab.server.jobs.JobHitsTotal;
import nl.inl.blacklab.server.jobs.JobHitsWindow;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {
	public RequestHandlerHits(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		Job search = null;
		JobHitsGrouped searchGrouped = null;
		JobHitsWindow searchWindow = null;
		JobHitsTotal total = null;

		// Do we want to view a single group after grouping?
		String groupBy = searchParam.getString("group");
		if (groupBy == null)
			groupBy = "";
		String viewGroup = searchParam.getString("viewgroup");
		if (viewGroup == null)
			viewGroup = "";
		try {
			HitsWindow window;
			HitGroup group = null;
			boolean block = isBlockingOperation();
			if (groupBy.length() > 0 && viewGroup.length() > 0) {

				// TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

				// Yes. Group, then show hits from the specified group
				searchGrouped = (JobHitsGrouped) searchMan.search(user, searchParam.hitsGrouped(), block);
				search = searchGrouped;
				search.incrRef();

				// If search is not done yet, indicate this to the user
				if (!search.finished()) {
					return Response.busy(ds, servlet);
				}

				// Search is done; construct the results object
				HitGroups groups = searchGrouped.getGroups();

				HitPropValue viewGroupVal = null;
				viewGroupVal = HitPropValue.deserialize(searchGrouped.getHits(), viewGroup);
				if (viewGroupVal == null)
					return Response.badRequest(ds, "ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);

				group = groups.getGroup(viewGroupVal);
				if (group == null)
					return Response.badRequest(ds, "GROUP_NOT_FOUND", "Group not found: " + viewGroup);

				String sortBy = searchParam.getString("sort");
				HitProperty sortProp = sortBy != null && sortBy.length() > 0 ? HitProperty.deserialize(group.getHits(), sortBy) : null;
				Hits hitsSorted;
				if (sortProp != null)
					hitsSorted = group.getHits().sortedBy(sortProp);
				else
					hitsSorted = group.getHits();

				int first = searchParam.getInteger("first");
				if (first < 0)
					first = 0;
				int number = searchParam.getInteger("number");
				if (number < 0 || number > searchMan.config().maxPageSize())
					number = searchMan.config().defaultPageSize();
				if (!hitsSorted.sizeAtLeast(first))
					return Response.badRequest(ds, "HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");
				window = hitsSorted.window(first, number);

			} else {
				// Regular set of hits (no grouping first)

				searchWindow = (JobHitsWindow) searchMan.search(user, searchParam.hitsWindow(), block);
				search = searchWindow;
				search.incrRef();

				// Also determine the total number of hits
				// (usually nonblocking, unless "waitfortotal=yes" was passed)
				total = (JobHitsTotal) searchMan.search(user, searchParam.hitsTotal(), searchParam.getBoolean("waitfortotal"));

				// If search is not done yet, indicate this to the user
				if (!search.finished()) {
					return Response.busy(ds, servlet);
				}

				window = searchWindow.getWindow();
			}

			if (searchParam.getString("calc").equals("colloc")) {
				dataStreamCollocations(ds, window.getOriginalHits());
				return HTTP_OK;
			}

			DocResults perDocResults = null;

			Searcher searcher = search.getSearcher();

			boolean includeTokenCount = searchParam.getBoolean("includetokencount");
			int totalTokens = -1;
			IndexStructure struct = searcher.getIndexStructure();
			if (includeTokenCount) {
				perDocResults = window.getOriginalHits().perDocResults();
				// Determine total number of tokens in result set
				String fieldName = struct.getMainContentsField().getName();
				DocProperty propTokens = new DocPropertyComplexFieldLength(fieldName);
				totalTokens = perDocResults.intSum(propTokens);
			}

			// Search is done; construct the results object

			ds.startMap();

			// The summary
			ds.startEntry("summary").startMap();
			Hits hits = searchWindow != null ? hits = searchWindow.getWindow().getOriginalHits() : group.getHits();
			boolean done = hits.doneFetchingHits();
			ds.startEntry("searchParam");
			searchParam.dataStream(ds);
			ds.endEntry();
			ds.entry("searchTime", (int)(search.userWaitTime() * 1000));
			if (total != null)
				ds.entry("countTime", (int)(total.userWaitTime() * 1000));
			ds.entry("stillCounting", !done);
			int totalHitsCounted = hits.countSoFarHitsCounted();
			if (total != null && total.threwException()) {
				// indicate that something went wrong while counting;
				// i.e. timeout
				totalHitsCounted = -1;
			}
			ds	.entry("numberOfHits", totalHitsCounted)
				.entry("numberOfHitsRetrieved", hits.countSoFarHitsRetrieved())
				.entry("stoppedCountingHits", hits.maxHitsCounted())
				.entry("stoppedRetrievingHits", hits.maxHitsRetrieved())
				.entry("numberOfDocs", hits.countSoFarDocsCounted())
				.entry("numberOfDocsRetrieved", hits.countSoFarDocsRetrieved());
			if (hits instanceof HitsSample) {
				HitsSample sample = ((HitsSample)hits);
				ds.entry("sampleSeed", sample.seed());
				if (sample.exactNumberGiven())
					ds.entry("sampleSize", sample.numberOfHitsToSelect());
				else
					ds.entry("samplePercentage", Math.round(sample.ratio() * 100 * 100) / 100.0);
			}
			ds.entry("windowFirstResult", window.first())
				.entry("requestedWindowSize", searchParam.getInteger("number"))
				.entry("actualWindowSize", window.size())
				.entry("windowHasPrevious", window.hasPrevious())
				.entry("windowHasNext", window.hasNext());
			if (includeTokenCount)
				ds.entry("tokensInMatchingDocuments", totalTokens);
			ds.startEntry("docFields");
			RequestHandler.dataStreamDocFields(ds, searcher.getIndexStructure());
			ds.endEntry();
			ds.endMap().endEntry();

			ds.startEntry("hits").startList();
			Map<Integer, String> pids = new HashMap<>();
			for (Hit hit: window) {
				ds.startItem("hit").startMap();

				// Find pid
				String pid = pids.get(hit.doc);
				if (pid == null) {
					Document document = searcher.document(hit.doc);
					pid = getDocumentPid(searcher, hit.doc, document);
					pids.put(hit.doc, pid);
				}

				boolean useOrigContent = searchParam.getString("usecontent").equals("orig");

				// TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()

				// Add basic hit info
				ds.entry("docPid", pid);
				ds.entry("start", hit.start);
				ds.entry("end", hit.end);

				if (useOrigContent) {
					// Add concordance from original XML
					Concordance c = window.getConcordance(hit);
					ds	.startEntry("left").plain(c.left()).endEntry()
						.startEntry("match").plain(c.match()).endEntry()
						.startEntry("right").plain(c.right()).endEntry();
				} else {
					// Add KWIC info
					Kwic c = window.getKwic(hit);
					ds	.startEntry("left").contextList(c.getProperties(), c.getLeft()).endEntry()
						.startEntry("match").contextList(c.getProperties(), c.getMatch()).endEntry()
						.startEntry("right").contextList(c.getProperties(), c.getRight()).endEntry();
				}
				ds.endMap().endItem();
			}
			ds.endList().endEntry();

			ds.startEntry("docInfos").startMap();
			//DataObjectMapAttribute docInfos = new DataObjectMapAttribute("docInfo", "pid");
			MutableIntSet docsDone = new IntHashSet();
			Document doc = null;
			String lastPid = "";
			for (Hit hit: window) {
				String pid = pids.get(hit.doc);

				// Add document info if we didn't already
				if (!docsDone.contains(hit.doc)) {
					docsDone.add(hit.doc);
					ds.startAttrEntry("docInfo", "pid", pid);
					if (!pid.equals(lastPid)) {
						doc = searcher.document(hit.doc);
						lastPid = pid;
					}
					dataStreamDocumentInfo(ds, searcher, doc);
					ds.endAttrEntry();
				}
			}
			ds.endMap().endEntry();

			if (searchParam.hasFacets()) {
				// Now, group the docs according to the requested facets.
				if (perDocResults == null)
					perDocResults = window.getOriginalHits().perDocResults();
				ds.startEntry("facets");
				dataStreamFacets(ds, perDocResults, searchParam.facets());
				ds.endEntry();
			}

			ds.endMap();

			return HTTP_OK;
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

	private void dataStreamCollocations(DataStream ds, Hits originalHits) {
		originalHits.settings().setContextSize(searchParam.getInteger("wordsaroundhit"));
		ds.startMap().startEntry("tokenFrequencies").startMap();
		TermFrequencyList tfl = originalHits.getCollocations();
		tfl.sort();
		for (TermFrequency tf: tfl) {
			ds.attrEntry("token", "text", tf.term, tf.frequency);
		}
		ds.endMap().endEntry().endMap();
	}

}
