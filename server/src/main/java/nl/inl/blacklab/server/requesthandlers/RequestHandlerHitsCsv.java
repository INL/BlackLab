package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
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
     */
    // TODO share with regular RequestHandlerHits, allow configuring windows, totals, etc ?
    private Pair<Hits, HitGroups> getHits() throws BlsException {
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

        BlsCacheEntry<?> job = null;
        Hits hits = null;
        HitGroups groups = null;

        try {
            if (groupBy != null) {
                job = searchMan.searchNonBlocking(user, searchParam.hitsGrouped());
                groups = (HitGroups) job.get();
                // don't set hits yet - only return hits if we're looking within a specific group

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
                // Use a regular job for hits, so that not all hits are actually retrieved yet, we'll have to construct a pagination view on top of the hits manually
                job = searchMan.searchNonBlocking(user, searchParam.hitsSample());
                hits = (Hits) job.get();
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

            int number = searchMan.config().maxExportPageSize();
            if (searchParam.containsKey("number"))
                number = Math.min(Math.max(0, searchParam.getInteger("number")), number);

            hits = hits.window(first, number);
        }
        return Pair.of(hits, groups);
    }

    private void writeGroups(HitGroups groups, DataStreamPlain ds) throws BlsException {
        searchLogger.setResultsFound(groups.size());
        try {
            // Write the header
            List<String> row = new ArrayList<>();
            row.addAll(groups.groupCriteria().propNames());
            row.add("count");

            CSVPrinter printer = createHeader(row);

            // write the groups
            for (HitGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Integer.toString(group.storedResults().hitsStats().countedSoFar()));
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
        if (includeSearchParameters()) {
            addSummaryCommonFieldsCSV(format, printer, searchParam);
        }
        row.clear();
        return printer;
    }

    private boolean includeSearchParameters() {
        return searchParam.getBoolean("csvsummary");
    }

    private boolean declareSeparator() {
        return searchParam.getBoolean("csvsepline");
    }

    private static void writeHit(Kwic kwic, Annotation mainTokenProperty, List<Annotation> otherTokenProperties, String docPid,
            String docTitle, ArrayList<String> row) {
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
        Annotation punct = mainTokenProperty.field().annotations().punct();
        row.add(StringUtils.join(interleave(kwic.left(punct), kwic.left(mainTokenProperty)).toArray()));
        row.add(StringUtils.join(kwic.match(mainTokenProperty), " ")); // what to do about punctuation and whitespace?
        row.add(StringUtils.join(interleave(kwic.right(punct), kwic.right(mainTokenProperty)).toArray()));

        // Add all other properties in this word
        for (Annotation otherProp : otherTokenProperties)
            row.add(StringUtils.join(kwic.match(otherProp), " "));
    }

    private void writeHits(Hits hits, DataStreamPlain ds) throws BlsException {
        searchLogger.setResultsFound(hits.size());

        final Annotation mainTokenProperty = blIndex().mainAnnotatedField().mainAnnotation();
        List<Annotation> otherTokenProperties = new ArrayList<>();

        try {
            // Build the table headers
            // The first few columns are fixed, and an additional columns is appended per annotation of tokens in this corpus.
            ArrayList<String> row = new ArrayList<>();
            row.addAll(Arrays.asList("docPid", "docName", "left_context", "context", "right_context"));

            // Retrieve the additional columns
            for (AnnotatedField annotatedField: blIndex().annotatedFields()) {
                for (Annotation annotation: annotatedField.annotations()) {
                    if (annotation.equals(mainTokenProperty) || annotation.isInternal())
                        continue;

                    row.add(annotation.name());
                    otherTokenProperties.add(annotation);
                }
            }

            CSVPrinter printer = createHeader(row);

            // Write the hits
            // We cannot use hitsPerDoc unfortunately, because the hits will come out sorted by their document, and we need a global order
            // So we need to manually retrieve the documents and their data
            Map<Integer, Pair<String, String>> luceneIdToPidAndTitle = new HashMap<>();
            Kwics kwics = hits.kwics(blIndex().defaultContextSize());
            for (Hit hit : hits) {
                String pid;
                String title;

                if (!luceneIdToPidAndTitle.containsKey(hit.doc())) {
                    Document doc = blIndex().doc(hit.doc()).luceneDoc();
                    pid = getDocumentPid(blIndex(), hit.doc(), doc);
                    String titleField = blIndex().metadataFields().special(MetadataFields.TITLE).name();
                    title = doc.get(titleField);

                    if (title == null || title.isEmpty())
                        title = "unknown (pid: " + pid + ")";

                    luceneIdToPidAndTitle.put(hit.doc(), Pair.of(pid, title));
                } else {
                    Pair<String, String> p = luceneIdToPidAndTitle.get(hit.doc());
                    pid = p.getLeft();
                    title = p.getRight();
                }

                writeHit(kwics.get(hit), mainTokenProperty, otherTokenProperties, pid, title, row);
                printer.printRecord(row);
            }
            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV2");
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

        for (int i = largest.size() - 1; i >= smallest.size(); --i)
            out.add(largest.get(i));

        return out;
    }
}
