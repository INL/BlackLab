package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SimpleCollector;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocImpl;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.BlockTimer;

public class HitGroupsTokenFrequencies {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;
    public static final boolean DEBUG = true;

    private static final Logger logger = LogManager.getLogger(HitGroupsTokenFrequencies.class);

    /** Document length is always reported as one higher due to punctuation being a trailing value */
    private static final int subtractClosingToken = 1;

    private static class GroupIdHash {
        private int[] tokenIds;
        private int[] tokenSortPositions;
        private PropertyValue[] metadataValues;
        private int hash;

        /**
         *
         * @param tokenValues
         * @param metadataValues
         * @param metadataValuesHash since many tokens per document, precalculate md hash for that thing
         */
        public GroupIdHash(int[] tokenIds, int[] tokenSortPositions, PropertyValue[] metadataValues, int metadataValuesHash) {
            this.tokenIds = tokenIds;
            this.tokenSortPositions = tokenSortPositions;
            this.metadataValues = metadataValues;
            hash = Arrays.hashCode(tokenSortPositions) ^ metadataValuesHash;
        };

        @Override
        public int hashCode() {
            return hash;
        }

        // Assume only called with other instances of IdHash
        @Override
        public boolean equals(Object obj) {
            return ((GroupIdHash) obj).hash == this.hash &&
                   Arrays.equals(((GroupIdHash) obj).tokenSortPositions, this.tokenSortPositions) &&
                   Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues);
        }
    }

    public static HitGroups get(QueryInfo queryInfo, Query filterQuery, SearchSettings searchSettings, HitProperty requestedGroupingProperty, int maxHitsPerGroup) {
        try {
            /** This is where we store our groups while we're computing/gathering them. Maps from group Id to number of hits (left) and number of docs (right) */
            final ConcurrentHashMap<GroupIdHash, MutablePair<Integer, Integer>> occurances = new ConcurrentHashMap<>();

            final BlackLabIndex index = queryInfo.index();
            /**
             * Document properties that are used in the grouping. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year")
             * This is not necessarily limited to just metadata, can also contain any other DocProperties such as document ID, document length, etc.
             */
            final List<DocProperty> docProperties = new ArrayList<>();
            /** Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and Terms */
            final List<Triple<AnnotationForwardIndex, MatchSensitivity, Terms>> hitProperties = new ArrayList<>();
            /**
             * Stores the original index every (doc|hit)property has in the original interleaved/intertwined list.
             * The requestedGroupingProperty sometimes represents more than one property (in the form of HitPropertyMultiple) such as 3 properties: [token text, document year, token lemma]
             * The groups always get an id that is (roughly) the concatenation of the properties (in the example case [token text, document year, token lemma]),
             * and it's important this id contains the respective values in the same order.
             * We need to keep this list because otherwise we'd potentially change the order.
             *
             * Integer contains index in the source list (docProperties or hitProperties, from just above)
             * Boolean is true when origin list was docProperties, false for hitProperties.
             */
            final List<Pair<Integer, Boolean>> originalOrderOfUnpackedProperties = new ArrayList<>();

            // Unpack the requestedGroupingProperty into its constituents and sort those into the appropriate categories: hit and doc properties.
            {
                @SuppressWarnings("unchecked")
                List<HitProperty> props = requestedGroupingProperty.props() != null ? (List<HitProperty>) requestedGroupingProperty.props() : Arrays.asList(requestedGroupingProperty);
                for (HitProperty p : props) {
                    final DocProperty asDocPropIfApplicable = p.docPropsOnly();
                    if (asDocPropIfApplicable != null) { // property can be converted to docProperty (applies to the document instead of the token/hit)
                        if (DEBUG && asDocPropIfApplicable.props() != null) {
                            throw new RuntimeException("Nested PropertyMultiples detected, should never happen (when this code was originally written)");
                        }
                        final int positionInUnpackedList = docProperties.size();
                        docProperties.add(asDocPropIfApplicable);
                        originalOrderOfUnpackedProperties.add(Pair.of(positionInUnpackedList, true));
                    } else { // Property couldn't be converted to DocProperty (is null). The current property is an actual HitProperty (applies to annotation/token/hit value)
                        List<Annotation> annot = p.needsContext();
                        if (DEBUG && (annot == null || annot.size() != 1)) {
                            throw new RuntimeException("Grouping property does not apply to singular annotation (nested propertymultiple? non-annotation grouping?) should never happen.");
                        }

                        final int positionInUnpackedList = hitProperties.size();
                        final AnnotationForwardIndex annotationFI = index.annotationForwardIndex(annot.get(0));
                        hitProperties.add(Triple.of(annotationFI, p.getSensitivities().get(0), annotationFI.terms()));
                        originalOrderOfUnpackedProperties.add(Pair.of(positionInUnpackedList, false));
                    }
                }
            }

            final int numAnnotations = hitProperties.size();
            long numberOfDocsProcessed;
            final AtomicInteger numberOfHitsProcessed = new AtomicInteger();
            final AtomicBoolean hitMaxHitsToProcess = new AtomicBoolean(false);

            try (final BlockTimer c = BlockTimer.create("Top Level")) {
                final List<Integer> docIds = new ArrayList<>();
                try (BlockTimer d = c.child("Gathering documents")) {
                    queryInfo.index().searcher().search(filterQuery == null ? new MatchAllDocsQuery() : filterQuery,new SimpleCollector() {
                        private int docBase;

                        @Override
                        protected void doSetNextReader(LeafReaderContext context) throws IOException {
                            docBase = context.docBase;
                            super.doSetNextReader(context);
                        }

                        @Override
                        public void collect(int docId) throws IOException {
                            int globalDocId = docId + docBase;
                            docIds.add(globalDocId);
                        }

                        @Override
                        public boolean needsScores() {
                            return false;
                        }
                    });
                }


                numberOfDocsProcessed = docIds.size();
                final IndexReader reader = queryInfo.index().reader();
                final int[] minusOne = new int[] { -1 };

                // Matched all tokens but not grouping by a specific annotation, only metadata
                // This requires a different approach because we never retrieve the individual tokens if there's no annotation
                // e.g. match '*' group by document year --
                // What we do instead is for every document just retrieve how many tokens it contains (from its metadata), and add that count to the appropriate group
                if (hitProperties.isEmpty()) {
                    try (BlockTimer f = c.child("Grouping documents (metadata only path)")) {
                        String fieldName = index.mainAnnotatedField().name();
                        DocPropertyAnnotatedFieldLength propTokens = new DocPropertyAnnotatedFieldLength(index, fieldName);
                        final int[] emptyTokenValuesArray = new int[0];

                        docIds.parallelStream().forEach(docId -> {
                            final int docLength = (int) propTokens.get(docId) - subtractClosingToken; // ignore "extra closing token"
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0, docLength);
                            final PropertyValue[] metadataValuesForGroup = new PropertyValue[docProperties.size()];
                            for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                            numberOfHitsProcessed.addAndGet(docLength);

                            // Add all tokens in document to the group.
                            final GroupIdHash groupId = new GroupIdHash(emptyTokenValuesArray, emptyTokenValuesArray, metadataValuesForGroup, metadataValuesHash);
                            occurances.compute(groupId, (__, groupSizes) -> {
                                if (groupSizes != null) {
                                    groupSizes.left += docLength;
                                    groupSizes.right += 1;
                                    return groupSizes;
                                } else {
                                    return MutablePair.of(docLength, 1);
                                }
                            });
                        });
                    }
                } else {
                    final int maxHitsToProcess = searchSettings.maxHitsToProcess() > 0 ? searchSettings.maxHitsToProcess() : Integer.MAX_VALUE;
                    final IntUnaryOperator incrementUntilMax = (v) -> v < maxHitsToProcess ? v + 1 : v;
                    final String fieldName = index.mainAnnotatedField().name();
                    final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
                    numberOfDocsProcessed = docIds.parallelStream().filter(docId -> {
                        try {

                            // Step 1: read all values for the to-be-grouped annotations for this document
                            // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation

                            final Document doc = reader.document(docId);
                            final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();

                            try (BlockTimer e = c.child("Read annotations from forward index")) {
                                for (Triple<AnnotationForwardIndex, MatchSensitivity, Terms> annot : hitProperties) {
                                    final String annotationFIName = annot.getLeft().annotation().forwardIndexIdField();
                                    final int fiid = doc.getField(annotationFIName).numericValue().intValue();
                                    final List<int[]> tokenValues = annot.getLeft().retrievePartsInt(fiid, minusOne, minusOne);
                                    tokenValuesPerAnnotation.addAll(tokenValues);
                                }
                            }

                            // Step 2: retrieve the to-be-grouped metadata for this document
                            int docLength = Integer.parseInt(doc.get(lengthTokensFieldName)) - subtractClosingToken; // ignore "extra closing token"
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0, docLength);
                            final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                            for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                            // now we have all values for all relevant annotations for this document
                            // iterate again and pair up the nth entries for all annotations, then store that as a group.
                            /**
                             * Bookkeeping: track which groups we've already seen in this document,
                             * so we only count this document once per group
                             */
                            HashSet<GroupIdHash> groupsInThisDocument = new HashSet<>();
                            try (BlockTimer f = c.child("Group tokens")) {
                                for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                    if (numberOfHitsProcessed.getAndUpdate(incrementUntilMax) >= maxHitsToProcess) {
                                        hitMaxHitsToProcess.set(true);
                                        return tokenIndex > 0; // true if any token of this document made the cut, false if we escaped immediately
                                    }


                                    // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                                    // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                                    // so in essence it's also an "id", but a case-insensitive one.
                                    // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                                    // that hashes the token ids instead of the sortpositions in that case.
                                    int[] annotationValuesForThisToken = new int[numAnnotations];
                                    int[] sortPositions = new int[annotationValuesForThisToken.length];
                                    for (int annotationIndex = 0; annotationIndex < numAnnotations; ++annotationIndex) {
                                        int[] tokenValuesThisAnnotation = tokenValuesPerAnnotation.get(annotationIndex);
                                        final int termId = annotationValuesForThisToken[annotationIndex] = tokenValuesThisAnnotation[tokenIndex];
                                        Triple<AnnotationForwardIndex, MatchSensitivity, Terms> currentHitProp = hitProperties.get(annotationIndex);
                                        MatchSensitivity matchSensitivity = currentHitProp.getMiddle();
                                        Terms terms = currentHitProp.getRight();
                                        sortPositions[annotationIndex] = terms.idToSortPosition(termId, matchSensitivity);
                                    }
                                    final GroupIdHash groupId = new GroupIdHash(annotationValuesForThisToken, sortPositions, metadataValuesForGroup, metadataValuesHash);
                                    occurances.compute(groupId, (__, groupSize) -> {
                                        if (groupSize != null) {
                                            groupSize.left += 1;
                                            // second (or more) occurance of these token values in this document
                                            groupSize.right += groupsInThisDocument.add(groupId) ? 1 : 0;
                                            return groupSize;
                                        } else {
                                            return MutablePair.of(1, groupsInThisDocument.add(groupId) ? 1 : 0); // should always return true, but we need to add this group anyway!
                                        }
                                    });
                                }
                            }
                        } catch (IOException e) {
                            throw BlackLabRuntimeException.wrap(e);
                        }
                        return true;
                    }).count();
                    logger.trace("Number of processed docs: " + numberOfDocsProcessed);
                }
            }

            Set<PropertyValue> duplicateGroupsDebug = DEBUG ? new HashSet<PropertyValue>() : null;

            List<HitGroup> groups;
            try (final BlockTimer c = BlockTimer.create("Resolve string values for tokens")) {
                final int numMetadataValues = docProperties.size();
                groups = occurances.entrySet().parallelStream().map(e -> {
                    final int groupSizeHits = e.getValue().getLeft();
                    final int groupSizeDocs = e.getValue().getRight();
                    final int[] annotationValues = e.getKey().tokenIds;
                    final PropertyValue[] metadataValues = e.getKey().metadataValues;
                    // allocate new - is not copied when moving into propertyvaluemultiple
                    final PropertyValue[] groupIdAsList = new PropertyValue[numAnnotations + numMetadataValues];

                    // Convert all raw values (integers) into their appropriate PropertyValues
                    // Taking care to preserve the order of the resultant PropertyValues with the order of the input HitProperties
                    int indexInOutput = 0;
                    for (Pair<Integer, Boolean> p : originalOrderOfUnpackedProperties) {
                        final int indexInInput = p.getLeft();
                        if (p.getRight()) { // is docprop, add PropertyValue as-is
                            groupIdAsList[indexInOutput++] = metadataValues[indexInInput];
                        } else { // is hitprop, convert value to PropertyValue.
                            Annotation annot = hitProperties.get(indexInInput).getLeft().annotation();
                            MatchSensitivity sens = hitProperties.get(indexInInput).getMiddle();
                            groupIdAsList[indexInOutput++] = new PropertyValueContextWords(index, annot, sens, new int[] {annotationValues[indexInInput]}, false);
                        }
                    }

                    PropertyValue groupId = groupIdAsList.length > 1 ? new PropertyValueMultiple(groupIdAsList) : groupIdAsList[0];
                    if (DEBUG) {
                        synchronized (duplicateGroupsDebug) {
                            if (!duplicateGroupsDebug.add(groupId)) {
                                throw new RuntimeException("Identical groups - should never happen");
                            }
                        }
                    }

                    return new HitGroupWithoutResults(queryInfo, groupId, groupSizeHits, groupSizeDocs, false, false);
                }).collect(Collectors.toList());
            }
            logger.debug("fast path used for grouping");

            ResultsStats hitsStats = new ResultsStatsStatic(numberOfHitsProcessed.get(), numberOfHitsProcessed.get(), new MaxStats(hitMaxHitsToProcess.get(), hitMaxHitsToProcess.get()));
            ResultsStats docsStats = new ResultsStatsStatic((int) numberOfDocsProcessed, (int) numberOfDocsProcessed, new MaxStats(hitMaxHitsToProcess.get(), hitMaxHitsToProcess.get()));
            return HitGroups.fromList(queryInfo, groups, requestedGroupingProperty, null, null, hitsStats, docsStats);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
}