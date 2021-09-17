package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamPlain;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for hit results.
 */
public class RequestHandlerDocsCsv extends RequestHandler {
    private static class Result {
        public final DocResults docs;
        public final DocGroups groups;
        public final DocResults subcorpusResults;
        public final boolean isViewGroup;

        public Result(DocResults docs, DocGroups groups, DocResults subcorpusResults, boolean isViewGroup) {
            super();
            this.docs = docs;
            this.groups = groups;
            this.subcorpusResults = subcorpusResults;
            this.isViewGroup = isViewGroup;
        }
    }

    public RequestHandlerDocsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    /**
     * Get the docs (and the groups from which they were extracted - if applicable)
     * or the groups for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the results.
     *
     * @return Docs if looking at ungrouped results, Docs+Groups if looking at
     *         results within a group, Groups if looking at groups but not within a
     *         specific group.
     * @throws BlsException
     * @throws InvalidQuery
     */
    // TODO share with regular RequestHandlerHits
    private Result getDocs() throws BlsException, InvalidQuery {
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

        DocResults docs = null;
        DocGroups groups = null;
        DocResults subcorpusResults = searchParam.subcorpus().execute();

        if (groupBy != null) {
            groups = searchParam.docsGrouped().execute();
            docs = searchParam.docs().execute();

            if (viewGroup != null) {
                PropertyValue groupId = PropertyValue.deserialize(groups.index(), groups.field(), viewGroup);
                if (groupId == null)
                    throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
                DocGroup group = groups.get(groupId);
                if (group == null)
                    throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                docs = group.storedResults();

                // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                if (sortBy != null) {
                    DocProperty sortProp = DocProperty.deserialize(blIndex(), sortBy);
                    if (sortProp == null)
                        throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
                    docs = docs.sort(sortProp);
                }
            }
        } else {
            // Don't use JobDocsAll, as we only might not need them all.
            docs = searchParam.docsSorted().execute();
        }

        // apply window settings
        // Different from the regular results, if no window settings are provided, we export the maximum amount automatically
        // The max for CSV exports is also different from the default pagesize maximum.
        if (docs != null) {
            int first = Math.max(0, searchParam.getInteger("first")); // Defaults to 0
            if (!docs.docsProcessedAtLeast(first))
                first = 0;

            int number = searchMan.config().getSearch().getMaxHitsToRetrieve();
            if (searchParam.containsKey("number")) {
                int requested = searchParam.getInteger("number");
                if (number >= 0 || requested >= 0) { // clamp
                    number = Math.min(requested, number);
                }
            }

            if (number >= 0)
                docs = docs.window(first, number);
        }

        return new Result(docs, groups, subcorpusResults, viewGroup != null);
    }

    private boolean includeSearchParameters() {
        return searchParam.getBoolean("csvsummary");
    }

    private boolean declareSeparator() {
        return searchParam.getBoolean("csvsepline");
    }

    private CSVPrinter createHeader(List<String> row) throws IOException {
        // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
        CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
        CSVPrinter printer = format.print(new StringBuilder(declareSeparator() ? "sep=,\r\n" : ""));

        return printer;
    }

    private void writeGroups(DocResults inputDocsForGroups, DocGroups groups, DocResults subcorpusResults, DataStreamPlain ds) throws BlsException {
        searchLogger.setResultsFound(groups.size());

        try {
            // Write the header
            List<String> row = new ArrayList<>();
            row.addAll(groups.groupCriteria().propNames());
            row.add("size"); // size of the group in documents
            if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
                row.add("numberOfTokens"); // tokens across all documents with hits in group
                // tokens across all document in group including docs without hits
                // might be equal to size+numberOfTokens, if the query didn't include a cql query
                // but don't bother omitting this data.
                row.add("subcorpusSize.tokens");
                row.add("subcorpusSize.documents");
            }

            CSVPrinter printer = createHeader(row);
            if (includeSearchParameters()) {
                addSummaryCsvDocs(printer, row.size(), inputDocsForGroups, groups, subcorpusResults.subcorpusSize());
            }

            // write the groups
            for (DocGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Integer.toString(group.size()));
                if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
                    row.add(Long.toString(group.totalTokens()));

                    if (searchParam.hasPattern()) {
                        PropertyValue docPropValues = group.identity();
                        CorpusSize groupSubcorpusSize = RequestHandlerHitsGrouped.findSubcorpusSize(searchParam, subcorpusResults.query(), groups.groupCriteria(), docPropValues, true);
                        row.add(groupSubcorpusSize.hasTokenCount() ? Long.toString(groupSubcorpusSize.getTokens()) : "[unknown]");
                        row.add(groupSubcorpusSize.hasDocumentCount() ? Integer.toString(groupSubcorpusSize.getDocuments()) : "[unknown]");
                    } else {
                        row.add(Long.toString(group.storedResults().subcorpusSize().getTokens()));
                        row.add(Integer.toString(group.storedResults().subcorpusSize().getDocuments()));
                    }
                }

                printer.printRecord(row);
            }

            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_DOCS_CSV1");
        }
    }

    private void writeDocs(DocResults docs, DocGroups fromGroups, DocResults globalSubcorpusSize, DataStreamPlain ds) throws BlsException {

        searchLogger.setResultsFound(docs.size());

        try {
            IndexMetadata indexMetadata = this.blIndex().metadata();
            MetadataField pidField = indexMetadata.metadataFields().special(MetadataFields.PID);
            String tokenLengthField = this.blIndex().mainAnnotatedField().tokenLengthField();

            // Build the header; 2 columns for pid and length, then 1 for each metadata field
            List<String> row = new ArrayList<>();
            row.add("docPid");
            row.add("numberOfHits");
            if (tokenLengthField != null)
                row.add("lengthInTokens");

            Collection<String> metadataFieldIds = this.getMetadataToWrite().stream().map(f -> f.name())
                    .collect(Collectors.toList());
            metadataFieldIds.remove("docPid"); // never show these values even if they exist as actual fields, they're internal/calculated
            metadataFieldIds.remove("lengthInTokens");
            metadataFieldIds.remove("mayView");

            row.addAll(metadataFieldIds); // NOTE: use the raw field IDs for headers, not the display names, CSVPrinter can't handle duplicate names

            CSVPrinter printer = createHeader(row);
            addSummaryCsvDocs(printer, row.size(), docs, fromGroups, globalSubcorpusSize.subcorpusSize());

            StringBuilder sb = new StringBuilder();

            int subtractClosingToken = 1;
            for (DocResult docResult : docs) {
                Document doc = docResult.identity().luceneDoc();
                row.clear();

                // Pid field, use lucene doc id if not provided
                if (pidField != null && doc.get(pidField.name()) != null)
                    row.add(doc.get(pidField.name()));
                else
                    row.add(Integer.toString(docResult.identity().id()));

                row.add(Integer.toString(docResult.size()));

                // Length field, if applicable
                if (tokenLengthField != null)
                    row.add(Integer.toString(Integer.parseInt(doc.get(tokenLengthField)) - subtractClosingToken)); // lengthInTokens

                // other fields in order of appearance
                for (String fieldId : metadataFieldIds) {
                    // we must support multiple values in a single csv cell
                    // we must also support values containing quotes/whitespace/commas
                    // this mean we must delimit individual values, we do this by surrounding them by quotes and separating them with a single space
                    // existing quotes will be escaped by doubling them as per the csv escaping conventions

                    // essentially transform
                    // a value containing "quotes"
                    // a "value" containing , as well as "quotes"

                    // into
                    // "a value containing ""quotes""" "a ""value"" containing , as well as ""quotes"""

                    // decoders must split the value on whitespace outside quotes, then strip outside quotes, then replace the doubled quotes with singular quotes

                    boolean firstValue = true;
                    for (String value : doc.getValues(fieldId)) {
                        if (!firstValue) {
                            sb.append(" ");
                        }
                        sb.append('"');
                        sb.append(value.replace("\n", "").replace("\r", "").replace("\"", "\"\""));
                        sb.append('"');
                        firstValue = false;
                    }

                    row.add(sb.toString());
                    sb.setLength(0);
                }

                Appendable app = printer.getOut();
                for (String cell : row) {
                    app.append(cell).append(',');
                }
                printer.println();
            }

            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_DOCS_CSV2");
        }
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        Result result = getDocs();
        if (result.groups == null || result.isViewGroup)
            writeDocs(result.docs, result.groups, result.subcorpusResults, (DataStreamPlain) ds);
        else
            writeGroups(result.docs, result.groups, result.subcorpusResults, (DataStreamPlain) ds);

        return HTTP_OK;
    }

    @Override
    public DataFormat getOverrideType() {
        return DataFormat.CSV;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
