package nl.inl.blacklab.server.requesthandlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.csv.CSVPrinter;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.lib.User;

/**
 * Base class for handling CSV requests for hits and documents.
 */
public abstract class RequestHandlerCsvAbstract extends RequestHandler {
    public RequestHandlerCsvAbstract(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathInfo) {
        super(servlet, request, user, indexName, urlResource, urlPathInfo);
    }

    private final static ArrayList<String> temp = new ArrayList<>();

    private static synchronized void writeRow(CSVPrinter printer, int numColumns, Object... values) {
        for (Object o : values)
            temp.add(o.toString());
        for (int i = temp.size(); i < numColumns; ++i)
            temp.add("");
        try {
            printer.printRecord(temp);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write response");
        }
        temp.clear();
    }

    /**
     * Output most of the fields of the search summary.
     *
     * @param numColumns number of columns to output per row, minimum 2
     * @param printer the output printer
     * @param searchParam original search parameters
     * @param groups (optional) if results are grouped, the groups
     * @param subcorpusSize global sub corpus information (i.e. inter-group)
     */
    // TODO tidy up csv handling
    private static <T> void addSummaryCsvCommon(
            CSVPrinter printer,
            int numColumns,
            SearchParameters searchParam,
            ResultGroups<T> groups,
            CorpusSize subcorpusSize
    ) {
        for (Map.Entry<String, String> param : searchParam.getParameters().entrySet()) {
            if (param.getKey().equals("listvalues") || param.getKey().equals("listmetadatavalues"))
                continue;
            writeRow(printer, numColumns, "summary.searchParam."+param.getKey(), param.getValue());
        }

        writeRow(printer, numColumns, "summary.subcorpusSize.documents", subcorpusSize.getDocuments());
        writeRow(printer, numColumns, "summary.subcorpusSize.tokens", subcorpusSize.getTokens());

        if (groups != null) {
            writeRow(printer, numColumns, "summary.numberOfGroups", groups.size());
            writeRow(printer, numColumns, "summary.largestGroupSize", groups.largestGroupSize());
        }

        SampleParameters sample = searchParam.getSampleSettings();
        if (sample != null) {
            writeRow(printer, numColumns, "summary.sampleSeed", sample.seed());
            if (sample.isPercentage())
                writeRow(printer, numColumns, "summary.samplePercentage", Math.round(sample.percentageOfHits() * 100 * 100) / 100.0);
            else
                writeRow(printer, numColumns, "summary.sampleSize", sample.numberOfHitsSet());
        }
    }

    /**
     *
     * @param printer CSV printer
     * @param numColumns number of columns
     * @param hits hits to summarize
     * @param groups (optional) if grouped
     * @param subcorpusSize (optional) if available
     */
    protected void addSummaryCsvHits(CSVPrinter printer, int numColumns, Hits hits, ResultGroups<Hit> groups, CorpusSize subcorpusSize) {
        addSummaryCsvCommon(printer, numColumns, searchParam, groups, subcorpusSize);
        writeRow(printer, numColumns, "summary.numberOfHits", hits.size());
        writeRow(printer, numColumns, "summary.numberOfDocs", hits.docsStats().countedSoFar());
    }

    /**
     * @param printer CSV printer
     * @param numColumns number of columns
     * @param docResults all docs as the input for groups, or contents of a specific group (viewgroup)
     * @param groups (optional) if grouped
     */
    protected void addSummaryCsvDocs(
            CSVPrinter printer,
            int numColumns,
            DocResults docResults,
            DocGroups groups,
            CorpusSize subcorpusSize
    ) {
        addSummaryCsvCommon(printer, numColumns, searchParam, groups, subcorpusSize);

        writeRow(printer, numColumns, "summary.numberOfDocs", docResults.size());
        writeRow(printer, numColumns, "summary.numberOfHits", docResults.stream().mapToLong(Group::size).sum());
    }


}
