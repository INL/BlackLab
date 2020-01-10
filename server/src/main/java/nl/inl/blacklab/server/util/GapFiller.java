package nl.inl.blacklab.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternOr;

/**
 * Fills gaps in a template string with column values from TSV data.
 *
 * URL parameters:
 *
 * <p>
 * <ul>
 * <li>template: The string with gaps to be filled (default: @@) in it
 * <li>gap: Gap pattern (regex; default: @@)
 * <li>values: A table of values to fill in (default: TSV)
 * <li>valsep: Value separator (single character; default: tab)
 * <li>vallinesep: Value line separator (regex; default: newline)
 * <li>outsep: Output value separator (default: |)
 * </ul>
 *
 * Example URL: /gap-filler?valsep=$&vallinesep=!&outsep=|&
 * template=The%20@@%20fox%20jumps%20over%20the%20@@%20dog.&
 * values=quick$lazy!brown$sleepy
 *
 */
public class GapFiller {

    private static final String VALUE_LINE_SEPARATOR = "\n";

    private static final char VALUE_SEPARATOR = '\t';

    private static final String OUTPUT_SEPARATOR = "|";

    private static final String GAP_REGEX = "@@";
    private static final Pattern GAP_REGEX_PATT = Pattern.compile(GAP_REGEX, Pattern.DOTALL);

    public static TextPattern parseGapQuery(String queryTemplate, String tsvValues) throws InvalidQuery {
        try {
            // Fill in the gaps
            Iterable<CSVRecord> values = parseTsv(new BufferedReader(new StringReader(tsvValues)), VALUE_SEPARATOR,
                    VALUE_LINE_SEPARATOR);
            return parseGapQuery(queryTemplate, GAP_REGEX, values, OUTPUT_SEPARATOR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse TSV format into lists of row values.
     *
     * @param in the TSV data
     * @param separator value separator
     * @param lineSeparator line separator
     * @return the row values lists
     * @throws IOException on error
     */
    private static Iterable<CSVRecord> parseTsv(BufferedReader in, char separator, String lineSeparator)
            throws IOException {
        String data = StringUtils.join(IOUtils.readLines(in), '\n');
        if (!lineSeparator.equals("\n")) {
            // Commons CSV can only parse lines
            data = data.replaceAll("\n", "\\n"); // escape any \n's in data
            data = data.replaceAll(lineSeparator, "\n"); // replace our line separator with \n's
        }
        CSVFormat ourFormat = CSVFormat.TDF.withDelimiter(separator);
        Iterable<CSVRecord> records = ourFormat.parse(new StringReader(data));
        return records;
    }

    /**
     * Fill the data values in in the template.
     *
     * @param template the template with gaps to fill in values
     * @param gapRegex pattern to find gaps with
     * @param values values to fill in, one list of strings per gap
     * @param valueSeparator separator to use when filling in multiple values into a
     *            gap
     * @return the filled-in template
     * @throws InvalidQuery if the resulting CQL contains an error
     */
    private static TextPattern parseGapQuery(String template, String gapRegex, Iterable<CSVRecord> values,
            String valueSeparator) throws InvalidQuery {
        String[] parts = template.split(gapRegex, -1);

        List<TextPattern> results = new ArrayList<>();
        for (CSVRecord valueRow : values) {
            if (valueRow.size() == 0 || valueRow.size() == 1 && valueRow.get(0).isEmpty())
                continue; // empty row

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                result.append(parts[i]);
                if (i < parts.length - 1) {
                    String val = valueRow.size() > i ? valueRow.get(i) : "";
                    String replaced = val.replaceAll("\"", "\\\\\"");
                    result.append(replaced);
                }
            }
            TextPattern tp = CorpusQueryLanguageParser.parse(result.toString());
            results.add(tp);
        }
        return new TextPatternOr(results.toArray(new TextPattern[0]));
    }

    public static boolean hasGaps(String patt) {
        return GAP_REGEX_PATT.matcher(patt).find();
    }

}
