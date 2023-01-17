package nl.inl.blacklab.server.lib;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.GapFiller;

public class WebserviceParamsUtils {
    /**
     * Parse the patt parameter.
     *
     * Optionally fill in gaps in the patt parameter using the pattGapData provided.
     *
     * @param index index we're searching
     * @param patt pattern string
     * @param pattLang pattern language (usually corpusql)
     * @param pattGapData optional pattern gap data if the pattern string contains gaps
     * @return text pattern
     */
    public static TextPattern parsePattern(BlackLabIndex index, String patt, String pattLang, String pattGapData) {
        TextPattern pattern = null;
        if (!StringUtils.isBlank(patt)) {
            if (pattLang.equals("corpusql") && !StringUtils.isBlank(pattGapData) && GapFiller.hasGaps(patt)) {
                // CQL query with gaps, and TSV data to put in the gaps
                try {
                    pattern = GapFiller.parseGapQuery(patt, pattGapData);
                } catch (InvalidQuery e) {
                    throw new BadRequest("PATT_SYNTAX_ERROR",
                            "Syntax error in gapped CorpusQL pattern: " + e.getMessage());
                }
            } else {
                String defaultAnnotation = index.mainAnnotatedField().mainAnnotation().name();
                pattern = BlsUtils.parsePatt(index, defaultAnnotation, patt, pattLang, true);
            }
        }
        return pattern;
    }

    /**
     * Parse the filter parameter (document filter query).
     *
     * @param index index we're searching
     * @param docPid if not null, filter is ignored and a filter matching only this document is returned
     * @param filter filter query
     * @param filterLang filter language (usually luceneql)
     * @return document filter query
     */
    public static Query parseFilterQuery(BlackLabIndex index, String docPid, String filter, String filterLang) {
        Query result;
        if (!StringUtils.isEmpty(docPid)) {
            // Only hits in 1 doc (for highlighting)
            int luceneDocId = BlsUtils.getDocIdFromPid(index, docPid);
            if (luceneDocId < 0)
                throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
            result = new SingleDocIdFilter(luceneDocId);
        } else if (!StringUtils.isEmpty(filter)) {
            result = BlsUtils.parseFilter(index, filter, filterLang);
        } else
            result = null;
        return result;
    }
}
