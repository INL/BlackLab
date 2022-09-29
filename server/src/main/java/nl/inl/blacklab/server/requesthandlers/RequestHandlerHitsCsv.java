package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
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
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperations;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHitsCsv extends RequestHandlerCsvAbstract {

    public RequestHandlerHitsCsv(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    private static void hitsCsvWriteGroups(SearchCreator params, Hits inputHitsForGroups, HitGroups groups, DocResults subcorpusResults, DataStreamPlain ds) throws BlsException {
        DocProperty metadataGroupProperties = null;
        if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
            metadataGroupProperties = groups.groupCriteria().docPropsOnly();
        }

        try {
            // Write the header
            List<String> row = new ArrayList<>(groups.groupCriteria().propNames());
            row.add("count");

            if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                row.add("numberOfDocs");
                row.add("subcorpusSize.documents");
                row.add("subcorpusSize.tokens");
            }

            CSVPrinter printer = hitsCsvCreateHeader(row, params.getCsvDeclareSeparator());
            if (params.getCsvIncludeSummary()) {
                addSummaryCsvHits(params, printer, row.size(), inputHitsForGroups, groups, subcorpusResults.subcorpusSize());
            }

            // write the groups
            for (HitGroup group : groups) {
                row.clear();
                row.addAll(group.identity().propValues());
                row.add(Long.toString(group.storedResults().hitsStats().countedSoFar()));

                if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = groups.groupCriteria().docPropValues(group.identity());
                    CorpusSize groupSubcorpusSize = RequestHandlerHitsGrouped.findSubcorpusSize(params, subcorpusResults.query(), metadataGroupProperties, docPropValues);
                    long numberOfDocsInGroup = group.storedResults().docsStats().countedTotal();

                    row.add(Long.toString(numberOfDocsInGroup));
                    row.add(groupSubcorpusSize.hasDocumentCount() ? Long.toString(groupSubcorpusSize .getDocuments()) : "[unknown]");
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

    private static CSVPrinter hitsCsvCreateHeader(List<String> row, boolean declareSeparator) throws IOException {
        // Create the header, then explicitly declare the separator, as excel normally uses a locale-dependent CSV-separator...
        CSVFormat format = CSVFormat.EXCEL.withHeader(row.toArray(new String[0]));
        return format.print(new StringBuilder(declareSeparator ? "sep=,\r\n" : ""));
    }

    private static void hitsCsvWriteHit(
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

    private static void hitsCsvWriteHits(
        SearchCreator params,
        Hits hits,
        HitGroups groups,
        List<Annotation> annotationsToWrite,
        DocResults subcorpusResults,
        DataStreamPlain ds
    ) throws BlsException {
        BlackLabIndex index = params.blIndex();
        final Annotation mainTokenProperty = index.mainAnnotatedField().mainAnnotation();
        try {
            // Build the table headers
            // The first few columns are fixed, and an additional columns is appended per annotation of tokens in this corpus.

            ArrayList<String> row = new ArrayList<>(Arrays.asList("docPid", "left_context", "context", "right_context"));
            for (Annotation a : annotationsToWrite) {
                row.add(a.name());
            }
            // Only output metadata if explicitly passed, do not print all fields if the parameter was omitted like the
            // normal hit response does
            // Since it results in a MASSIVE amount of repeated data.
            List<MetadataField> metadataFieldsToWrite = !params.getListMetadataValuesFor().isEmpty() ?
                    new ArrayList<>(WebserviceOperations.getMetadataToWrite(index, params)) :
                    Collections.emptyList();
            for (MetadataField f : metadataFieldsToWrite) {
                 row.add(f.name());
            }

            CSVPrinter printer = hitsCsvCreateHeader(row, params.getCsvDeclareSeparator());
            if (params.getCsvIncludeSummary()) {
                hits.hitsStats().countedTotal(); // block for a bit
                addSummaryCsvHits(params, printer, row.size(), hits, groups, subcorpusResults.subcorpusSize());
            }

            Map<Integer, Document> luceneDocs = new HashMap<>();
            Kwics kwics = hits.kwics(params.contextSettings().size());
            for (Hit hit : hits) {
                Document doc = luceneDocs.get(hit.doc());
                if (doc == null) {
                    doc = index.luceneDoc(hit.doc());
                    luceneDocs.put(hit.doc(), doc);
                }
                hitsCsvWriteHit(kwics.get(hit), doc, mainTokenProperty, annotationsToWrite, WebserviceOperations.getDocumentPid(
                        index, hit.doc(), doc), metadataFieldsToWrite, row);
                printer.printRecord(row);
            }
            printer.flush();
            ds.plain(printer.getOut().toString());
        } catch (IOException e) {
            throw new InternalServerError("Cannot write response: " + e.getMessage(), "INTERR_WRITING_HITS_CSV2");
        }
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        WebserviceOperations.ResultHitsCsv result = WebserviceOperations.getHitsCsv(params, searchMan);
        if (result.groups != null && !result.isViewGroup) {
            hitsCsvWriteGroups(result.hits, result.groups, result.subcorpusResults, (DataStreamPlain) ds);
        } else {
            hitsCsvWriteHits(params, result.hits, result.groups, WebserviceOperations.getAnnotationsToWrite(blIndex(), params),
                    result.subcorpusResults,
                    (DataStreamPlain) ds);
        }

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
