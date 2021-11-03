package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamPlain;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.BlsCacheEntry;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHitsCsv extends RequestHandler {
    private static class Result {
        public final Hits hits;
        public final HitGroups groups;
        public final DocResults subcorpusResults;
        public final boolean isViewGroup;

        public Result(Hits hits, HitGroups groups, DocResults subcorpusResults, boolean isViewGroup) {
            super();
            this.hits = hits;
            this.groups = groups;
            this.subcorpusResults = subcorpusResults;
            this.isViewGroup = isViewGroup;
        }
    }

    public RequestHandlerHitsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    /**
     * Get the hits (and the groups from which they were extracted - if applicable)
     * or the groups for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the hits.
     *
     * @return Hits if looking at ungrouped hits, Hits+Groups if looking at hits
     *         within a group, Groups if looking at grouped hits.
     * @throws BlsException
     * @throws InvalidQuery
     */
    // TODO share with regular RequestHandlerHits, allow configuring windows, totals, etc ?
    private Result getHits() throws BlsException, InvalidQuery {
        // Might be null
        String groupBy = searchParam.getString("group");
        if (groupBy.isEmpty())
            groupBy = null;
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup.isEmpty())
            viewGroup = null;
        String sortBy = searchParam.getString("sort");
        if (sortBy.isEmpty())
            sortBy = null;

        BlsCacheEntry<?> cacheEntry = null;
        Hits hits = null;
        HitGroups groups = null;
        DocResults subcorpus = searchParam.subcorpus().execute();

        try {
            if (!StringUtils.isEmpty(groupBy)) {
                hits = searchParam.hits().execute();
                groups = searchParam.hitsGrouped().execute();

                if (viewGroup != null) {
                    PropertyValue groupId = PropertyValue.deserialize(blIndex(), blIndex().mainAnnotatedField(), viewGroup);
                    if (groupId == null)
                        throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
                    HitGroup group = groups.get(groupId);
                    if (group == null)
                        throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                    hits = group.storedResults();

                    // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                    // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                    // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                    // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                    if (sortBy != null) {
                        HitProperty sortProp = HitProperty.deserialize(hits, sortBy);
                        if (sortProp == null)
                            throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
                        hits = hits.sort(sortProp);
                    }
                }
            } else {
                // Use a regular search for hits, so that not all hits are actually retrieved yet, we'll have to construct a pagination view on top of the hits manually
                cacheEntry = (BlsCacheEntry<Hits>)searchParam.hitsSample().executeAsync();
                hits = (Hits) cacheEntry.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }

        // apply window settings
        // Different from the regular results, if no window settings are provided, we export the maximum amount automatically
        // The max for CSV exports is also different from the default pagesize maximum.
        if (hits != null) {
            int first = Math.max(0, searchParam.getInteger("first")); // Defaults to 0
            if (!hits.hitsStats().processedAtLeast(first))
                first = 0;


            int number = searchMan.config().getSearch().getMaxHitsToRetrieve();
            if (searchParam.containsKey("number")) {
                int requested = searchParam.getInteger("number");
                if (number >= 0 || requested >= 0) { // clamp
                    number = Math.min(requested, number);
                }
            }

            if (number >= 0)
                hits = hits.window(first, number);
        }

        return new Result(hits, groups, subcorpus, viewGroup != null);
    }

    private void writeGroups(Hits inputHitsForGroups, HitGroups groups, DocResults subcorpusResults, DataStreamPlain ds) throws BlsException {
        searchLogger.setResultsFound(groups.size());

        DocProperty metadataGroupProperties = null;
        if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
            metadataGroupProperties = groups.groupCriteria().docPropsOnly();
        }

        try {
            // Write the header
            List<String> row = new ArrayList<>();
            row.addAll(groups.groupCriteria().propNames());
            row.add("count");

            if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                row.add("numberOfDocs");
                row.add("subcorpusSize.documents");
                row.add("subcorpusSize.tokens");
            }

            CSVPrinter printer = createHeader(row);
            if (this.includeSearchParameters()) {
                addSummaryCsvHits(printer, row.size(), inputHitsForGroups, groups, subcorpusResults.subcorpusSize());
            }

            // write the groups
            for (HitGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Integer.toString(group.storedResults().hitsStats().countedSoFar()));

                if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    CorpusSize groupSubcorpusSize = RequestHandlerHitsGrouped.findSubcorpusSize(searchParam, subcorpusResults.query(), metadataGroupProperties, docPropValues, true);
                    int numberOfDocsInGroup = group.storedResults().docsStats().countedTotal();

                    row.add(Integer.toString(numberOfDocsInGroup));
                    row.add(groupSubcorpusSize.hasDocumentCount() ? Integer.toString(groupSubcorpusSize .getDocuments()) : "[unknown]");
                    row.add(groupSubcorpusSize.hasTokenCount() ? Long.toString(groupSubcorpusSize .getTokens()) : "[unknown]");
                }

                printer.printRecord(row);
            }

            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV1");
        }
    }

    private CSVPrinter createHeader(List<String> row) throws IOException {
        // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
        CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
        CSVPrinter printer = format.print(new StringBuilder(declareSeparator() ? "sep=,\r\n" : ""));
        return printer;
    }

    @SuppressWarnings("static-method")
    private boolean includeSearchParameters() {
        return true; //return searchParam.getBoolean("csvsummary");
    }

    @SuppressWarnings("static-method")
    private boolean declareSeparator() {
        return true; //return searchParam.getBoolean("csvsepline");
    }

    private static void writeHit(
        Kwic kwic,
        Document doc,
        Annotation mainTokenProperty,
        List<Annotation> otherTokenProperties,
        String docPid,
        List<MetadataField> metadataFieldsToWrite,
        ArrayList<String> row
    ) {
        row.clear();


        /*
         * Order of kwic/hitProps is always the same:
         * - punctuation (always present)
         * - other (non-internal) properties (in order of declaration in the index)
         * - word itself
         */
        // Only kwic supported, original document output not supported in csv currently.
        Annotation punct = mainTokenProperty.field().annotations().punct();
        row.add(docPid);
        row.add(StringUtils.join(interleave(kwic.left(punct), kwic.left(mainTokenProperty)).toArray()));
        row.add(StringUtils.join(interleave(kwic.match(punct), kwic.match(mainTokenProperty)).toArray()));
        row.add(StringUtils.join(interleave(kwic.right(punct), kwic.right(mainTokenProperty)).toArray()));

        // Add all other properties in this word
        for (Annotation otherProp : otherTokenProperties)
            row.add(StringUtils.join(kwic.match(otherProp), " "));

        // other fields in order of appearance
        for (MetadataField field : metadataFieldsToWrite)
            row.add(csvEscape(doc.getValues(field.name())));
    }

    private void writeHits(
        Hits hits,
        HitGroups groups,
        List<Annotation> annotationsToWrite,
        DocResults subcorpusResults,
        DataStreamPlain ds
    ) throws BlsException {
        searchLogger.setResultsFound(hits.size());

        final Annotation mainTokenProperty = blIndex().mainAnnotatedField().mainAnnotation();
        try {
            // Build the table headers
            // The first few columns are fixed, and an additional columns is appended per annotation of tokens in this corpus.
            ArrayList<String> row = new ArrayList<>();

            row.addAll(Arrays.asList("docPid", "left_context", "context", "right_context"));

            for (Annotation a : annotationsToWrite) {
                row.add(a.name());
            }
            // Only output metadata if explicitly passed, do not print all fields if the parameter was omitted like the normal hit response does
            // Since it results in a MASSIVE amount of repeated data.
            List<MetadataField> metadataFieldsToWrite = searchParam.containsKey("listmetadatavalues") ? new ArrayList<>(getMetadataToWrite()) : Collections.emptyList();
            for (MetadataField f : metadataFieldsToWrite) {
                 row.add(f.name());
            }

            CSVPrinter printer = createHeader(row);
            if (includeSearchParameters()) {
                hits.hitsStats().countedTotal(); // block for a bit
                addSummaryCsvHits(printer, row.size(), hits, groups, subcorpusResults.subcorpusSize());
            }

            Map<Integer, Document> luceneDocs = new HashMap<>();
            Kwics kwics = hits.kwics(blIndex().defaultContextSize());
            for (Hit hit : hits) {
                Document doc = luceneDocs.get(hit.doc());
                if (doc == null) {
                    doc = blIndex().doc(hit.doc()).luceneDoc();
                    luceneDocs.put(hit.doc(), doc);
                }
                writeHit(kwics.get(hit), doc, mainTokenProperty, annotationsToWrite, getDocumentPid(blIndex(), hit.doc(), doc), metadataFieldsToWrite, row);
                printer.printRecord(row);
            }
            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV2");
        } catch (BlsException e) {
            throw e;
        }
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        Result result = getHits();
        if (result.groups != null && !result.isViewGroup)
            writeGroups(result.hits, result.groups, result.subcorpusResults, (DataStreamPlain) ds);
        else
            writeHits(result.hits, result.groups, getAnnotationsToWrite(), result.subcorpusResults, (DataStreamPlain) ds);

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

        for (int i = largest.size() - 1; i >= smallest.size(); --i)
            out.add(largest.get(i));

        return out;
    }

    /*
     * We must support multiple values in a single csv cell.
     * We must also support values containing quotes/whitespace/commas.
     *
     * This mean we must delimit individual values, we do this by surrounding them by quotes and separating them with a single space
     *
     * Existing quotes will be escaped by doubling them as per the csv escaping conventions
     * Essentially transform
     *      a value containing "quotes"
     *      a "value" containing , as well as "quotes"
     * into
     *      "a value containing ""quotes"""
     *      "a ""value"" containing , as well as ""quotes"""
     *
     * Decoders must split the value on whitespace outside quotes, then strip outside quotes, then replace the doubled quotes with singular quotes
    */
    private static String csvEscape(String[] strings) {
        StringBuilder sb = new StringBuilder();
        boolean firstValue = true;
        for (String value : strings) {
            if (!firstValue) {
                sb.append(" ");
            }
            sb.append('"');
            sb.append(value.replace("\n", "").replace("\r", "").replace("\"", "\"\""));
            sb.append('"');
            firstValue = false;
        }

        return sb.toString();
    }
}
