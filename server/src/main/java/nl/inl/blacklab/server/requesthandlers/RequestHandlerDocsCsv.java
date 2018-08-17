package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
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
import nl.inl.blacklab.server.jobs.JobDocsGrouped;
import nl.inl.blacklab.server.jobs.JobWithDocs;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for hit results.
 */
public class RequestHandlerDocsCsv extends RequestHandler {
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
     */
    // TODO share with regular RequestHandlerHits
    private Pair<DocResults, DocGroups> getDocs() throws BlsException {
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

        JobWithDocs job = null;
        DocResults docs = null;
        DocGroups groups = null;

        try {
            if (groupBy != null) {
                JobDocsGrouped searchGrouped = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped(), true);
                job = searchGrouped;
                groups = searchGrouped.getGroups();
                // don't set docs yet - only return docs if we're looking within a specific group

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
                        DocProperty sortProp = DocProperty.deserialize(sortBy);
                        if (sortProp == null)
                            throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
                        docs = docs.sortedBy(sortProp);
                    }
                }
            } else {
                // Don't use JobDocsAll, as we only might not need them all.
                job = (JobWithDocs) searchMan.search(user, searchParam.docsSorted(), true);
                docs = job.getDocResults();
            }
        } finally {
            if (job != null)
                job.decrRef();
        }

        // apply window settings
        // Different from the regular results, if no window settings are provided, we export the maximum amount automatically
        // The max for CSV exports is also different from the default pagesize maximum.
        if (docs != null) {
            int first = Math.max(0, searchParam.getInteger("first")); // Defaults to 0
            if (!docs.docsProcessedAtLeast(first))
                first = 0;

            int number = searchMan.config().maxExportPageSize();
            if (searchParam.containsKey("number"))
                number = Math.min(Math.max(0, searchParam.getInteger("number")), number);

            docs = docs.window(first, number);
        }

        return Pair.of(docs, groups);
    }

    private void writeGroups(DocGroups groups, DataStreamPlain ds) throws BlsException {
        try {
            // Write the header
            List<String> row = new ArrayList<>();
            row.addAll(groups.groupCriteria().propNames());
            row.add("count");

            // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
            CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
            CSVPrinter printer = format.print(new StringBuilder("sep=,\r\n"));
            addSummaryCommonFieldsCSV(format, printer, searchParam);
            row.clear();

            // write the groups
            for (DocGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Integer.toString(group.storedResults().size()));
                printer.printRecord(row);
            }

            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), 42);
        }
    }

    private void writeDocs(DocResults docs, DataStreamPlain ds) throws BlsException {
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

            Collection<String> metadataFieldIds = indexMetadata.metadataFields().stream().map(f -> f.name()).collect(Collectors.toList());
            metadataFieldIds.remove("docPid"); // never show these values even if they exist as actual fields, they're internal/calculated
            metadataFieldIds.remove("lengthInTokens");
            metadataFieldIds.remove("mayView");

            row.addAll(metadataFieldIds); // NOTE: don't add display names, CSVPrinter can't handle duplicate names

            // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
            CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
            CSVPrinter printer = format.print(new StringBuilder("sep=,\r\n"));
            addSummaryCommonFieldsCSV(format, printer, searchParam);
            row.clear();

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
                    row.add(doc.get(fieldId));
                }

                printer.printRecord(row);
            }

            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), 42);
        }
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        Pair<DocResults, DocGroups> result = getDocs();
        if (result.getLeft() != null)
            writeDocs(result.getLeft(), (DataStreamPlain) ds);
        else
            writeGroups(result.getRight(), (DataStreamPlain) ds);

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
