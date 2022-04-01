package nl.inl.blacklab.tools.frequency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
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
 *
 * Major changes:
 * - store metadata values as strings, not PropertyValue
 * - always group on annotations first, then metadata fields
 * - don't create HitGroups, return Map with counts directly
 * - don't check if we exceed maxHitsToCount
 * - always process all documents (no document filter query)
 * - return sorted map, so we can perform sub-groupings and merge them later
 *   (uses ConcurrentSkipListMap, or alternatively wraps a TreeMap at the end;
 *    note that using ConcurrentSkipListMap has consequences for the compute() method, see there)
 */
@SuppressWarnings("DuplicatedCode") // see above
class CalcTokenFrequencies {

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param index index
     * @param annotations annotations to group on
     * @param metadataFields metadata fields to group on
     * @param occurrences grouping to add to
     */
    public static void get(BlackLabIndex index, List<Annotation> annotations,
                                                               List<String> metadataFields, List<Integer> docIds,
                                                               ConcurrentMap<GroupIdHash, OccurrenceCounts> occurrences) {

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
            fieldsToLoad.addAll(metadataFields);

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
                    Map<GroupIdHash, OccurrenceCounts> occsInDoc = new HashMap<>();

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
                            OccurrenceCounts occ = occsInDoc.get(groupId);
                            if (occ == null) {
                                occ = new OccurrenceCounts(1, 1);
                                occsInDoc.put(groupId, occ);
                            } else {
                                occ.hits++;
                            }


                        }


                        // Merge occurrences in this doc with global occurrences
                        if (occurrences instanceof ConcurrentSkipListMap) {
                            // NOTE: we cannot modify groupSize or occ here like we do in HitGroupsTokenFrequencies,
                            //       because we use ConcurrentSkipListMap, which may call the remapping function
                            //       multiple times if there's potential concurrency issues.
                            occsInDoc.forEach((groupId, occ) -> {
                                occurrences.compute(groupId, (__, groupSize) -> {
                                    if (groupSize == null)
                                        return occ; // reusing occ here is okay because it doesn't change on subsequent calls
                                    else
                                        return new OccurrenceCounts(groupSize.hits + occ.hits, groupSize.docs + occ.docs);
                                });
                            });
                        } else {
                            // Not using ConcurrentSkipListMap but ConcurrentHashMap. It's okay to re-use occ,
                            // because our remapping function will only be called once.
                            occsInDoc.forEach((groupId, occ) -> {
                                occurrences.compute(groupId, (__, groupSize) -> {
                                    // NOTE: we cannot modify groupSize or occ here like we do in HitGroupsTokenFrequencies,
                                    //       because we use ConcurrentSkipListMap, which may call the remapping function
                                    //       multiple times if there's potential concurrency issues.
                                    if (groupSize != null) {
                                        // Group existed already
                                        // Count hits and doc
                                        occ.hits += groupSize.hits;
                                        occ.docs += groupSize.docs;
                                    }
                                    return occ; // reusing occ here is okay because it doesn't change on subsequent calls
                                });
                            });
                        }

                    }
                } catch (IOException e) {
                    throw BlackLabRuntimeException.wrap(e);
                }
            });
        }

    }
}
