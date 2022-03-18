package nl.inl.blacklab.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

/**
 * More optimized version of HitGroupsTokenFrequencies.
 *
 * Takes shortcuts to be able to process huge corpora without
 * running out of memory, at the expense of genericity.
 */
@SuppressWarnings("DuplicatedCode")
public class CalculateTokenFrequenciesWholeCorpus {

    /** Precalculated hashcode for group id, to save time while grouping and sorting. */
    static class GroupIdHash {
        private final int[] tokenIds;
        private final int[] tokenSortPositions;
        private final String[] metadataValues;
        private final int hash;

        /**
         *  @param tokenSortPositions sort position for each token in the group id
         * @param metadataValues relevant metadatavalues
         * @param metadataValuesHash since many tokens per document, precalculate md hash for that thing
         */
        public GroupIdHash(int[] tokenIds, int[] tokenSortPositions, String[] metadataValues, int metadataValuesHash) {
            this.tokenIds = tokenIds;
            this.tokenSortPositions = tokenSortPositions;
            this.metadataValues = metadataValues;
            hash = Arrays.hashCode(tokenSortPositions) ^ metadataValuesHash;
        }

        public int[] getTokenIds() {
            return tokenIds;
        }

        public String[] getMetadataValues() {
            return metadataValues;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        // Assume only called with other instances of IdHash (faster for large groupings)
        @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
        @Override
        public boolean equals(Object obj) {
            return ((GroupIdHash) obj).hash == this.hash &&
                   Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions) &&
                   Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues);
        }
    }

    /** Counts of hits and docs while grouping. */
    static final class OccurranceCounts {
        public int hits;
        public int docs;

        public OccurranceCounts(int hits, int docs) {
            this.hits = hits;
            this.docs = docs;
        }
    }

    /** Info about an annotation we're grouping on. */
    private static final class AnnotInfo {
        private final AnnotationForwardIndex annotationForwardIndex;

        private final MatchSensitivity matchSensitivity;

        private final Terms terms;

        public AnnotationForwardIndex getAnnotationForwardIndex() {
            return annotationForwardIndex;
        }

        public MatchSensitivity getMatchSensitivity() {
            return matchSensitivity;
        }

        public Terms getTerms() {
            return terms;
        }

        public AnnotInfo(AnnotationForwardIndex annotationForwardIndex, MatchSensitivity matchSensitivity) {
            this.annotationForwardIndex = annotationForwardIndex;
            this.matchSensitivity = matchSensitivity;
            this.terms = annotationForwardIndex.terms();
        }
    }

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param index index
     * @param annotations annotations to group on
     * @param metadataFields metadata fields to group on
     * @return token frequencies
     */
    public static Map<GroupIdHash, OccurranceCounts> get(BlackLabIndex index, List<Annotation> annotations, List<String> metadataFields) {

        // This is where we store our groups while we're computing/gathering them. Maps from group Id to number of hits and number of docs
        final ConcurrentHashMap<GroupIdHash, OccurranceCounts> occurances = new ConcurrentHashMap<>();

        /*
         * Document properties that are used in the grouping. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year")
         * This is not necessarily limited to just metadata, can also contain any other DocProperties such as document ID, document length, etc.
         */

        // Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and Terms
        final List<AnnotInfo> hitProperties = annotations.stream().map(ann -> {
            AnnotationForwardIndex afi = index.annotationForwardIndex(ann);
            return new AnnotInfo(afi, MatchSensitivity.INSENSITIVE);
        }).collect(Collectors.toList());
        final List<String> docProperties = new ArrayList<>(metadataFields);

        final int numAnnotations = hitProperties.size();

        try (final BlockTimer c = BlockTimer.create("Top Level")) {

            // Collect all doc ids that match the given filter (or all docs if no filter specified)
            final List<Integer> docIds = new ArrayList<>();
            try (BlockTimer ignored = c.child("Gathering documents")) {
                index.forEachDocument((index1, id) -> docIds.add(id));
            }

            // Start actually calculating the requests frequencies.

            // We do have hit properties, so we need to use both document metadata and the tokens from the forward index to
            // calculate the frequencies.
            //final IntUnaryOperator incrementUntilMax = (v) -> v < maxHitsToCount ? v + 1 : v;
            final String fieldName = annotations.get(0).field().name();
            final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

            // Determine all the fields we want to be able to load, so we don't need to load the entire document
            final List<String> annotationFINames = hitProperties.stream().map(tr -> tr.getAnnotationForwardIndex().annotation().forwardIndexIdField()).collect(Collectors.toList());
            final Set<String> fieldsToLoad = new HashSet<>();
            fieldsToLoad.add(lengthTokensFieldName);
            fieldsToLoad.addAll(annotationFINames);

            final IndexReader reader = index.reader();

            docIds.parallelStream().forEach(docId -> {

                try {

                    // Step 1: read all values for the to-be-grouped annotations for this document
                    // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation

                    final Document doc = reader.document(docId, fieldsToLoad);
                    final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                    final List<int[]> sortValuesPerAnnotation = new ArrayList<>();

                    try (BlockTimer ignored = c.child("Read annotations from forward index")) {
                        for (AnnotInfo annot : hitProperties) {
                            final AnnotationForwardIndex afi = annot.getAnnotationForwardIndex();
                            final String annotationFIName = afi.annotation().forwardIndexIdField();
                            final int fiid = doc.getField(annotationFIName).numericValue().intValue();
                            final int[] tokenValues = afi.getDocument(fiid);
                            tokenValuesPerAnnotation.add(tokenValues);

                            // Look up sort values
                            // NOTE: tried moving this to a TermsReader.arrayOfIdsToSortPosition() method,
                            //       but that was slower...
                            int docLength = tokenValues.length;
                            int[] sortValues = new int[docLength];
                            for (int tokenIndex = 0; tokenIndex < docLength; ++tokenIndex) {
                                final int termId = tokenValues[tokenIndex];
                                sortValues[tokenIndex] = annot.getTerms().idToSortPosition(termId, annot.getMatchSensitivity());
                            }
                            sortValuesPerAnnotation.add(sortValues);
                        }

                    }

                    // Step 2: retrieve the to-be-grouped metadata for this document
                    int docLength = Integer.parseInt(doc.get(lengthTokensFieldName)) - BlackLabIndex.IGNORE_EXTRA_CLOSING_TOKEN;
                    //final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(queryInfo.index(), docId), 0, docLength);
                    final String[] metadataValuesForGroup = !docProperties.isEmpty() ? new String[docProperties.size()] : null;
                    for (int i = 0; i < docProperties.size(); ++i)
                    metadataValuesForGroup[i] = doc.get(docProperties.get(i));
                    final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                    // now we have all values for all relevant annotations for this document
                    // iterate again and pair up the nth entries for all annotations, then store that as a group.

                    // Keep track of term occurrences in this document; later we'll merge it with the global term frequencies
                    Map<GroupIdHash, OccurranceCounts> occsInDoc = new HashMap<>();

                    try (BlockTimer ignored = c.child("Group tokens")) {

                        for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                int[] annotationValuesForThisToken = new int[numAnnotations];
                            int[] sortPositions = new int[numAnnotations];

                            // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                            // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                            // so in essence it's also an "id", but a case-insensitive one.
                            // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                            // that hashes the token ids instead of the sortpositions in that case.
                            for (int annotationIndex = 0; annotationIndex < numAnnotations; ++annotationIndex) {
                                int[] tokenValues = tokenValuesPerAnnotation.get(annotationIndex);
                                annotationValuesForThisToken[annotationIndex] = tokenValues[tokenIndex];
                                int[] sortValuesThisAnnotation = sortValuesPerAnnotation.get(annotationIndex);
                                sortPositions[annotationIndex] = sortValuesThisAnnotation[tokenIndex];
                            }
                            final GroupIdHash groupId = new GroupIdHash(annotationValuesForThisToken, sortPositions, metadataValuesForGroup, metadataValuesHash);

                            // Count occurrence in this doc
                            OccurranceCounts occ = occsInDoc.get(groupId);
                            if (occ == null) {
                                occ = new OccurranceCounts(1, 1);
                                occsInDoc.put(groupId, occ);
                            } else {
                                occ.hits++;
                            }


                        }

                        // Merge occurrences in this doc with global occurrences
                        occsInDoc.forEach((groupId, occ) -> {
                            occurances.compute(groupId, (__, groupSize) -> {
                                if (groupSize != null) {
                                    // Group existed already
                                    // Count hits and doc
                                    groupSize.hits += occ.hits;
                                    groupSize.docs += 1;
                                    return groupSize;
                                } else {
                                    // New group. Count hits and doc.
                                    return occ;
                                }
                            });
                        });

                    }
                } catch (IOException e) {
                    throw BlackLabRuntimeException.wrap(e);
                }
            });
        }

        return occurances;
    }
}
