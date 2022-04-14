package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsInternal;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.util.XmlHighlighter;
import nl.inl.util.XmlHighlighter.HitCharSpan;

/**
 * Several utility methods related to documents and highlighting.
 */
public class DocUtil {

    private DocUtil() {
    }

    public static void characterOffsets(BlackLabIndex index, int id, Field field, int[] startsOfWords, int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
        if (startsOfWords.length == 0)
            return; // nothing to do
        try {
            // Determine lowest and highest word position we'd like to know something about.
            // This saves a little bit of time for large result sets.
            int minP = -1, maxP = -1;
            int numStarts = startsOfWords.length;
            int numEnds = endsOfWords.length;
            for (int i = 0; i < numStarts; i++) {
                if (startsOfWords[i] < minP || minP == -1)
                    minP = startsOfWords[i];
                if (startsOfWords[i] > maxP)
                    maxP = startsOfWords[i];
            }
            for (int i = 0; i < numEnds; i++) {
                if (endsOfWords[i] < minP || minP == -1)
                    minP = endsOfWords[i];
                if (endsOfWords[i] > maxP)
                    maxP = endsOfWords[i];
            }
            if (minP < 0 || maxP < 0)
                throw new BlackLabRuntimeException("Can't determine min and max positions");

            String fieldPropName = field.offsetsField();

            org.apache.lucene.index.Terms terms = index.reader().getTermVector(id, fieldPropName);
            if (terms == null)
                throw new IllegalArgumentException("Field " + fieldPropName + " in doc " + id + " has no term vector");
            if (!terms.hasPositions())
                throw new IllegalArgumentException(
                        "Field " + fieldPropName + " in doc " + id + " has no character postion information");

            //int lowestPos = -1, highestPos = -1;
            int lowestPosFirstChar = -1, highestPosLastChar = -1;
            int total = numStarts + numEnds;
            boolean[] done = new boolean[total]; // NOTE: array is automatically initialized to zeroes!
            int found = 0;

            // Iterate over terms
            TermsEnum termsEnum = terms.iterator();
            while (termsEnum.next() != null) {
                PostingsEnum dpe = termsEnum.postings(null, PostingsEnum.POSITIONS);

                // Iterate over docs containing this term (NOTE: should be only one doc!)
                while (dpe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    // Iterate over positions of this term in this doc
                    int positionsRead = 0;
                    int numberOfPositions = dpe.freq();
                    while (positionsRead < numberOfPositions) {
                        int position = dpe.nextPosition();
                        if (position == -1)
                            break;
                        positionsRead++;

                        // Keep track of the lowest and highest char pos, so
                        // we can fill in the character positions we didn't find
                        int startOffset = dpe.startOffset();
                        if (startOffset < lowestPosFirstChar || lowestPosFirstChar == -1) {
                            lowestPosFirstChar = startOffset;
                        }
                        int endOffset = dpe.endOffset();
                        if (endOffset > highestPosLastChar) {
                            highestPosLastChar = endOffset;
                        }

                        // We've calculated the min and max word positions in advance, so
                        // we know we can skip this position if it's outside the range we're interested in.
                        // (Saves a little time for large result sets)
                        if (position < minP || position > maxP) {
                            continue;
                        }

                        for (int m = 0; m < numStarts; m++) {
                            if (!done[m] && position == startsOfWords[m]) {
                                done[m] = true;
                                startsOfWords[m] = startOffset;
                                found++;
                            }
                        }
                        for (int m = 0; m < numEnds; m++) {
                            if (!done[numStarts + m] && position == endsOfWords[m]) {
                                done[numStarts + m] = true;
                                endsOfWords[m] = endOffset;
                                found++;
                            }
                        }

                        // NOTE: we might be tempted to break here if found == total,
                        // but that would foul up our calculation of highestPosLastChar and
                        // lowestPosFirstChar.
                    }
                }

            }
            if (found < total) {
                if (!fillInDefaultsIfNotFound)
                    throw new BlackLabRuntimeException("Could not find all character offsets!");

                if (lowestPosFirstChar < 0 || highestPosLastChar < 0)
                    throw new BlackLabRuntimeException("Could not find default char positions!");

                for (int m = 0; m < numStarts; m++) {
                    if (!done[m])
                        startsOfWords[m] = lowestPosFirstChar;
                }
                for (int m = 0; m < numEnds; m++) {
                    if (!done[numStarts + m])
                        endsOfWords[m] = highestPosLastChar;
                }
            }

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private static List<HitCharSpan> getCharacterOffsets(BlackLabIndex index, int id, Hits hits) {
        if (hits.size() > HitsInternal.MAX_ARRAY_SIZE)
            throw new BlackLabRuntimeException("Cannot handle more than " + HitsInternal.MAX_ARRAY_SIZE + " hits in a single doc");
        int[] starts = new int[(int)hits.size()];
        int[] ends = new int[(int)hits.size()];
        Iterator<EphemeralHit> hitsIt = hits.ephemeralIterator();
        for (int i = 0; i < starts.length; i++) {
            EphemeralHit hit = hitsIt.next(); // hits.get(i);
            starts[i] = hit.start;
            ends[i] = hit.end - 1; // end actually points to the first word not in the hit, so
                                   // subtract one
        }

        characterOffsets(index, id, hits.field(), starts, ends, true);

        List<HitCharSpan> hitspans = new ArrayList<>(starts.length);
        for (int i = 0; i < starts.length; i++) {
            hitspans.add(new HitCharSpan(starts[i], ends[i]));
        }
        return hitspans;
    }

    /**
     * Convert start/end word positions to char positions.
     *
     * @param field field to use
     * @param startAtWord where to start getting the content (-1 for start of
     *            document, 0 for first word)
     * @param endAtWord where to end getting the content (-1 for end of document)
     * @return the start and end char position as a two element int array (with any
     *         -1's preserved)
     */
    private static int[] startEndWordToCharPos(BlackLabIndex index, int id, Field field, int startAtWord, int endAtWord) {
        if (startAtWord == -1 && endAtWord == -1) {
            // No need to translate anything
            return new int[] { -1, -1 };
        }

        // Translate word pos to char pos and retrieve content
        // NOTE: this boolean stuff is a bit iffy, but is necessary because
        // getCharacterOffsets doesn't handle -1 to mean start/end of doc.
        // We should probably fix that some time.
        boolean startAtStartOfDoc = startAtWord == -1;
        boolean endAtEndOfDoc = endAtWord == -1;
        int[] starts = { startAtStartOfDoc ? 0 : startAtWord };
        int[] ends = { endAtEndOfDoc ? starts[0] : endAtWord };
        characterOffsets(index, id, field, starts, ends, true);
        if (startAtStartOfDoc)
            starts[0] = -1;
        if (endAtEndOfDoc)
            ends[0] = -1;
        return new int[] { starts[0], ends[0] };
    }

    public static String contentsByCharPos(BlackLabIndex index, int id, Document d, Field field, int startAtChar, int endAtChar) {
        if (d == null)
            d = index.luceneDoc(id);
        if (!field.hasContentStore()) {
            // No special content accessor set; assume a stored field
            return d.get(field.contentsFieldName()).substring(startAtChar, endAtChar);
        }
        return index.contentAccessor(field).getSubstringsFromDocument(d, new int[] { startAtChar }, new int[] { endAtChar })[0];
    }

    public static String highlightContent(BlackLabIndex index, int id, Hits hits, int startAtWord, int endAtWord) {
        // Convert word positions to char positions
        int lastWord = endAtWord < 0 ? endAtWord : endAtWord - 1; // if whole content, don't subtract one
        AnnotatedField field = hits.field();
        int[] startEndCharPos = startEndWordToCharPos(index, id, field, startAtWord, lastWord);

        // Get content by char positions
        int startAtChar = startEndCharPos[0];
        int endAtChar = startEndCharPos[1];
        String content = contentsByCharPos(index, id, null, field, startAtChar, endAtChar);

        boolean wholeDocument = startAtWord == -1 && endAtWord == -1;
        boolean mustFixUnbalancedTags = !wholeDocument;

        // Do we have anything to highlight, or do we have an XML fragment that needs balancing?
        ResultsStats hitsStats = hits.hitsStats();
        if (hitsStats.processedAtLeast(1) || mustFixUnbalancedTags) {
            // Find the character offsets for the hits and highlight
            List<HitCharSpan> hitspans = null;
            if (hitsStats.processedAtLeast(1)) // if hits == null, we still want the highlighter to make it well-formed
                hitspans = getCharacterOffsets(index, id, hits);
            XmlHighlighter hl = new XmlHighlighter();
            hl.setUnbalancedTagsStrategy(index.defaultUnbalancedTagsStrategy());
            if (startAtChar == -1)
                startAtChar = 0;
            content = hl.highlight(content, hitspans, startAtChar);
        }
        return content;
    }

    private static String[] getSubstringsFromDocument(BlackLabIndex index,
            Document d, Field field, int[] starts, int[] ends) {
        if (!field.hasContentStore()) {
            String[] content;
            // No special content accessor set; assume a non-annotated stored field
            String fieldContent = d.get(field.contentsFieldName());
            content = new String[starts.length];
            for (int i = 0; i < starts.length; i++) {
                content[i] = fieldContent.substring(starts[i], ends[i]);
            }
            return content;
        }
        // Content accessor set. Use it to retrieve the content.
        return index.contentAccessor(field).getSubstringsFromDocument(d, starts, ends);
    }

    public static List<Concordance> makeConcordancesFromContentStore(BlackLabIndex index, int id,
            Field field, int[] startsOfWords, int[] endsOfWords, XmlHighlighter hl) {
        // Determine starts and ends
        int n = startsOfWords.length / 2;
        int[] starts = new int[n];
        int[] ends = new int[n];
        for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
            starts[j] = startsOfWords[i];
            ends[j] = endsOfWords[i + 1];
        }

        // Retrieve 'em all
        Document d = index.luceneDoc(id);
        String[] content = getSubstringsFromDocument(index, d, field, starts, ends);

        // Cut 'em up
        List<Concordance> rv = new ArrayList<>();
        for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
            // Put the concordance in the Hit object
            int absLeft = startsOfWords[i];
            int absRight = endsOfWords[i + 1];
            int relHitLeft = startsOfWords[i + 1] - absLeft;
            int relHitRight = endsOfWords[i] - absLeft;
            String currentContent = content[j];

            // Determine context and build concordance.
            // Note that hit text may be empty for hits of length zero,
            // such as a search for open tags (which have a location but zero length,
            // like a search for a word has a length 1)
            String hitText = relHitRight < relHitLeft ? ""
                    : currentContent.substring(relHitLeft,
                            relHitRight);
            String leftContext = currentContent.substring(0, relHitLeft);
            String rightContext = currentContent.substring(relHitRight, absRight - absLeft);

            // Make each fragment well-formed
            hitText = hl.makeWellFormed(hitText);
            leftContext = hl.makeWellFormed(leftContext);
            rightContext = hl.makeWellFormed(rightContext);

            rv.add(new Concordance(new String[] { leftContext, hitText, rightContext }));
        }
        return rv;
    }

    /**
     * Get part of the contents of a document.
     *
     * Pass -1 for start and end positions to get the whole content.
     *
     * @param index our indx
     * @param id document id
     * @param d Lucene document if available, otherwise null
     * @param field field to get contents for
     * @param startAtWord token position to start at
     * @param endAtWord token position to end at (first token not in the resulting snippet)
     * @return contents
     */
    static String contents(BlackLabIndex index, int id, Document d, Field field, int startAtWord, int endAtWord) {
        if (d == null)
            d = index.luceneDoc(id);
        if (!field.hasContentStore()) {
            // No special content accessor set; assume a stored field
            String content = d.get(field.contentsFieldName());
            if (content == null)
                throw new IllegalArgumentException("Field not found: " + field.name());
            return BlackLabIndexImpl.getWordsFromString(content, startAtWord, endAtWord);
        }

        int[] startEnd = startEndWordToCharPos(index, id, field, startAtWord, endAtWord);
        return index.contentAccessor(field).getSubstringsFromDocument(d, new int[] { startEnd[0] }, new int[] { startEnd[1] })[0];
    }

    /**
     * Get the contents of a document.
     *
     * @param index our indx
     * @param id document id
     * @param d Lucene document if available, otherwise null
     * @return contents
     */
    public static String contents(BlackLabIndex index, int id, Document d) {
        return contents(index, id, d, index.mainAnnotatedField(), -1, -1);
    }
}
