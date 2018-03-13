package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.grouping.HitGroup;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitPropValue;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamPlain;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.jobs.JobHitsGrouped;
import nl.inl.blacklab.server.jobs.JobWithHits;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHitsCsv extends RequestHandler {
	public RequestHandlerHitsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	/**
	 * Get the hits (and the groups from which they were extracted - if applicable) or the groups for this request.
	 * Exceptions cleanly mapping to http error responses are thrown if any part of the request cannot be fulfilled.
	 * Sorting is already applied to the hits.
	 *
	 * @return Hits if looking at ungrouped hits, Hits+Groups if looking at hits within a group, Groups if looking at grouped hits.
	 * @throws BlsException
	 */
	// TODO share with regular RequestHandlerHits, allow configuring windows, totals, etc ?
	private Pair<Hits, HitGroups> getHits() throws BlsException {
		// Might be null
		String groupBy = searchParam.getString("group"); if (groupBy.isEmpty()) groupBy = null;
		String viewGroup = searchParam.getString("viewgroup"); if (viewGroup.isEmpty()) viewGroup = null;
		String sortBy = searchParam.getString("sort"); if (sortBy.isEmpty()) sortBy = null;

		Hits hits = null;
		HitGroups groups = null;

		if (groupBy != null) {
			JobHitsGrouped searchGrouped = (JobHitsGrouped) searchMan.search(user, searchParam.hitsGrouped(), true);
			groups = searchGrouped.getGroups();
			// don't set hits yet - only return hits if we're looking within a specific group

			if (viewGroup != null) {
				HitPropValue groupId = HitPropValue.deserialize(searchGrouped.getHits(), viewGroup);
				if (groupId == null)
					throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
				HitGroup group = groups.getGroup(groupId);
				if (group == null)
					throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

				hits = group.getHits();

				// TODO sortBy is automatically applied to regular hits and groups
				// but is applied to Group ordering instead of hit ordering within group with we have both group and viewGroup
				// need to fix this in SearchParameters somewhere
				if (sortBy != null) {
					HitProperty sortProp = HitProperty.deserialize(hits, sortBy);
					if (sortProp == null)
						throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
					hits = hits.sortedBy(sortProp, sortProp.isReverse());
				}
			}
		} else  {
			// Use a regular job for hits, so that not all hits are actually retrieved yet, we'll have to construct a pagination view on top of the hits manually
			JobWithHits job = (JobWithHits) searchMan.search(user, searchParam.hitsSample(), true);
			hits = job.getHits();
		}

		// apply window settings
		// Different from the regular hits, if no parameters are provided, all hits are returned.
		if (hits != null && (searchParam.containsKey("first") || searchParam.containsKey("number"))) {
			int first = Math.max(0, searchParam.getInteger("first")); // Defaults to 0
			int number = searchParam.containsKey("number") ? Math.max(1, searchParam.getInteger("number")) : Integer.MAX_VALUE;

			if (!hits.sizeAtLeast(first))
				first = 0;
			hits = hits.window(first, number);
		}

		return Pair.of(hits, groups);
	}

	private static void writeGroups(HitGroups groups, DataStreamPlain ds) throws BlsException {
		try {
			// Write the header
			List<String> row = new ArrayList<>();
			row.addAll(groups.getGroupCriteria().getPropNames());
			row.add("count");

			CSVPrinter printer = CSVFormat.EXCEL.withHeader(row.toArray(new String[0])).print(new StringBuilder("sep=,\r\n"));

			// write the groups
			for (HitGroup group : groups) {
				row.clear();
				row.addAll(group.getIdentity().getPropValues());
				row.add(Integer.toString(group.getHits().countSoFarHitsCounted()));
				printer.printRecord(row);
			}

			printer.flush();
			ds.plain(printer.getOut().toString());
		} catch (IOException e) {
			// TODO proper message
			throw new InternalServerError("Cannot write response", 1337);
		}
	}

	private static void writeHit(Kwic kwic, String mainTokenProperty, List<String> otherTokenProperties, String docPid, String docTitle, ArrayList<String> row) {
		row.clear();

		/*
		 * Order of kwic/hitProps is always the same:
		 * - punctuation (always present)
		 * - other (non-internal) properties (in order of declaration in the index)
		 * - word itself
		 */
		row.add(docPid);
		row.add(docTitle);

		// Only kwic supported, original document output not supported in csv currently.
		row.add(StringUtils.join(interleave(kwic.getLeft("punct"), kwic.getLeft(mainTokenProperty)).toArray()));
		row.add(StringUtils.join(kwic.getMatch(mainTokenProperty), " ")); // what to do about punctuation and whitespace?
		row.add(StringUtils.join(interleave(kwic.getRight("punct"), kwic.getRight(mainTokenProperty)).toArray()));

		// Add all other properties in this word
		for (String otherProp : otherTokenProperties)
			row.add(StringUtils.join(kwic.getMatch(otherProp), " "));
	}

	private void writeHits(Hits hits, DataStreamPlain ds) throws BlsException {
		final String mainTokenProperty = getSearcher().getIndexStructure().getMainContentsField().getMainProperty().getName();
		List<String> otherTokenProperties = new ArrayList<>();

		try {
			// Build the table headers
			// The first few columns are fixed, and an additional columns is appended per property of tokens in this corpus.
			List<String> columns = new ArrayList<>();
			columns.addAll(Arrays.asList("docPid", "docName", "left_context", "context", "right_context"));

			// Retrieve the additional columns
			for (String complexFieldName : getSearcher().getIndexStructure().getComplexFields()) {
				ComplexFieldDesc complexField = getSearcher().getIndexStructure().getComplexFieldDesc(complexFieldName);
				for (String tokenProperty : complexField.getProperties()) {
					PropertyDesc desc = complexField.getPropertyDesc(tokenProperty);
					if (tokenProperty.equals(mainTokenProperty) || desc.isInternal())
						continue;

					columns.add(tokenProperty); // NOTE: do not use property displayNames, the columns may not have duplicate names
					otherTokenProperties.add(tokenProperty);
				}
			}

			// Explicitly declare the separator, excel normally uses a locale-dependent CSV-separator...
			CSVPrinter printer = CSVFormat.EXCEL.withHeader(columns.toArray(new String[0])).print(new StringBuilder("sep=,\r\n"));

			// Write the hits
			// We cannot use hitsPerDoc unfortunately, because the hits will come out sorted by their document, and we need a global order
			// So we need to manually retrieve the documents and their data
			Map<Integer, Pair<String, String>> luceneIdToPidAndTitle = new HashMap<>();
			ArrayList<String> row = new ArrayList<>();
			for (Hit hit : hits) {
				String pid;
				String title;

				if (!luceneIdToPidAndTitle.containsKey(hit.doc)) {
					Document doc = getSearcher().document(hit.doc);
					pid = getDocumentPid(getSearcher(), hit.doc, doc);
					title = doc.get(getSearcher().getIndexStructure().titleField());

					if (title == null || title.isEmpty())
						title = "unknown (pid: " + pid + ")";

					luceneIdToPidAndTitle.put(hit.doc, Pair.of(pid, title));
				} else {
					Pair<String, String> p = luceneIdToPidAndTitle.get(hit.doc);
					pid = p.getLeft();
					title = p.getRight();
				}

				writeHit(hits.getKwic(hit), mainTokenProperty, otherTokenProperties, pid, title, row);
				printer.printRecord(row);
			}
			printer.flush();
			ds.plain(printer.getOut().toString());
		} catch (IOException e) {
			// TODO proper message
			throw new InternalServerError("Cannot write response", 1337);
		}
	}


	@Override
	public int handle(DataStream ds) throws BlsException {
		Pair<Hits, HitGroups> result = getHits();
		if (result.getLeft() != null)
			writeHits(result.getLeft(), (DataStreamPlain) ds);
		else
			writeGroups(result.getRight(), (DataStreamPlain) ds);

		return HTTP_OK;
	}

	@Override
	public DataFormat getOverrideType() {
		return DataFormat.CSV;
	}


	private static List<String> interleave(List<String> a, List<String> b) {
		List<String> out = new ArrayList<>();

		List<String> smallest = a.size() < b.size() ? a : b;
		List<String> largest = a.size() > b.size() ? a : b;
		for (int i = 0; i < smallest.size(); ++i) {
			out.add(a.get(i));
			out.add(b.get(i));
		}

		for (int i = largest.size()-1; i >= smallest.size(); --i)
			out.add(largest.get(i));

		return out;
	}
}



//private void formatDocuments(Map<Integer, Document> docsByLuceneId) {
//
////	ds.entry("hits", printer.getOut().toString());
//
//	// Explicitly declare the separator, excel normally uses a locale-dependent CSV-separator...
//	CSVPrinter printer = CSVFormat.EXCEL.withHeader("docPid", "key", "value").print(new StringBuilder("sep=,\r\n"));
//	List<String> row = new ArrayList<>();
//	for (Entry<Integer, Document> e : docs.entrySet()) {
//		Integer docId = e.getKey();
//		Document doc = e.getValue();
//
//		for (String metadataFieldName: struct.getMetadataFields()) {
//			String value = doc.get(metadataFieldName);
//			if (value != null && !value.equals("lengthInTokens") && !value.equals("mayView")) {
//				row.add(docPids.get(docId));
//				row.add(metadataFieldName);
//				row.add(value);
//				printer.printRecord(row);
//				row.clear();
//			}
//		}
//
//		int subtractFromLength = struct.alwaysHasClosingToken() ? 1 : 0;
//		String tokenLengthField = struct.getMainContentsField().getTokenLengthField();
//		if (tokenLengthField != null) {
//			row.add(docPids.get(docId));
//			row.add("lengthInTokens");
//			row.add(Integer.toString(Integer.parseInt(doc.get(tokenLengthField)) - subtractFromLength));
//			printer.printRecord(row);
//			row.clear();
//		}
//
//		row.add(docPids.get(docId));
//		row.add("mayView");
//		row.add(Boolean.toString(struct.contentViewable()));
//		printer.printRecord(row);
//		row.clear();
//	}
//	printer.flush();
//}
