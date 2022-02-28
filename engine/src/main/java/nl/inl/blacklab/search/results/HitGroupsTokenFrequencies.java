package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.util.BlockTimer;

/**
 * Determine token frequencies for (a subset of) a corpus, given a HitProperty to group on.
 *
 * Allows us to e.g. find lemma frequencies, or lemma frequencies per year.
 * This implementation is faster than finding all hits, then grouping those.
 */
public class HitGroupsTokenFrequencies {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;
    public static final boolean DEBUG = true;

    private static final Logger logger = LogManager.getLogger(HitGroupsTokenFrequencies.class);

    /** Precalculated hashcode for group id, to save time while grouping and sorting. */
    private static class GroupIdHash {
        private int[] tokenIds;
        private int[] tokenSortPositions;
        private PropertyValue[] metadataValues;
        private int hash;

        /**
         *
         * @param tokenIds token term id for each token in the group id
         * @param tokenSortPositions sort position for each token in the group id
         * @param metadataValues relevant metadatavalues
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

    /**
     * Can the given grouping request be answered using the faster codepath in this classs?
     *
     * If not, it will be handled by {@link HitGroups}, see {@link nl.inl.blacklab.searches.SearchHitGroupsFromHits}.
     *
     * @param mustStoreHits do we need stored hits? if so, we can't use this path
     * @param hitsSearch hits search to group. Must be any token query
     * @param property property to group on. Must consist of DocProperties or HitPropertyHitText
     * @return true if this path can be used
     */
    public static boolean canUse(boolean mustStoreHits, SearchHits hitsSearch, HitProperty property) {
        return !mustStoreHits && HitGroupsTokenFrequencies.TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED && hitsSearch.isAnyTokenQuery() && property.isDocPropOrHitText();
    }

    /** Counts of hits and docs while grouping. */
    private static final class OccurranceCounts {
        public int hits;
        public int docs;

        public OccurranceCounts(int hits, int docs) {
            this.hits = hits;
            this.docs = docs;
        }
    }

    /** Info about doc and hit properties while grouping. */
    private static final class PropInfo {

        public static PropInfo doc(int index) {
            return new PropInfo(true, index);
        }

        public static PropInfo hit(int index) {
            return new PropInfo(false, index);
        }

        private boolean docProperty;

        private int indexInList;

        public boolean isDocProperty() {
            return docProperty;
        }

        public int getIndexInList() {
            return indexInList;
        }

        private PropInfo(boolean docProperty, int indexInList) {
            this.docProperty = docProperty;
            this.indexInList = indexInList;
        }
    }

    /** Info about an annotation we're grouping on. */
    private static final class AnnotInfo {
        private AnnotationForwardIndex annotationForwardIndex;

        private MatchSensitivity matchSensitivity;

        private Terms terms;

        public AnnotationForwardIndex getAnnotationForwardIndex() {
            return annotationForwardIndex;
        }

        public MatchSensitivity getMatchSensitivity() {
            return matchSensitivity;
        }

        public Terms getTerms() {
            return terms;
        }

        public AnnotInfo(AnnotationForwardIndex annotationForwardIndex, MatchSensitivity matchSensitivity, Terms terms) {
            this.annotationForwardIndex = annotationForwardIndex;
            this.matchSensitivity = matchSensitivity;
            this.terms = terms;
        }
    }

    /**
     * Get the token frequencies for the given query and hit property.
     *
     * @param source query to find token frequencies for
     * @param requestedGroupingProperty
     * @return token frequencies
     */
    public static HitGroups get(SearchHits source, HitProperty requestedGroupingProperty) {

        QueryInfo queryInfo = source.queryInfo();
        Query filterQuery = source.getFilterQuery();
        SearchSettings searchSettings = source.searchSettings();

        try {
            /** This is where we store our groups while we're computing/gathering them. Maps from group Id to number of hits and number of docs */
            final ConcurrentHashMap<GroupIdHash, OccurranceCounts> occurances = new ConcurrentHashMap<>();

            final BlackLabIndex index = queryInfo.index();

            /**
             * Document properties that are used in the grouping. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year")
             * This is not necessarily limited to just metadata, can also contain any other DocProperties such as document ID, document length, etc.
             */
            final List<DocProperty> docProperties = new ArrayList<>();

            /** Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and Terms */
            final List<AnnotInfo> hitProperties = new ArrayList<>();

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
            final List<PropInfo> originalOrderOfUnpackedProperties = new ArrayList<>();

            // Unpack the requestedGroupingProperty into its constituents and sort those into the appropriate categories: hit and doc properties.
            {
                List<HitProperty> props = requestedGroupingProperty.props() != null ? requestedGroupingProperty.props() : Arrays.asList(requestedGroupingProperty);
                for (HitProperty p : props) {
                    final DocProperty asDocPropIfApplicable = p.docPropsOnly();
                    if (asDocPropIfApplicable != null) { // property can be converted to docProperty (applies to the document instead of the token/hit)
                        if (DEBUG && asDocPropIfApplicable.props() != null) {
                            throw new RuntimeException("Nested PropertyMultiples detected, should never happen (when this code was originally written)");
                        }
                        final int positionInUnpackedList = docProperties.size();
                        docProperties.add(asDocPropIfApplicable);
                        originalOrderOfUnpackedProperties.add(PropInfo.doc(positionInUnpackedList));
                    } else { // Property couldn't be converted to DocProperty (is null). The current property is an actual HitProperty (applies to annotation/token/hit value)
                        List<Annotation> annot = p.needsContext();
                        if (DEBUG && (annot == null || annot.size() != 1)) {
                            throw new RuntimeException("Grouping property does not apply to singular annotation (nested propertymultiple? non-annotation grouping?) should never happen.");
                        }

                        final int positionInUnpackedList = hitProperties.size();
                        final AnnotationForwardIndex annotationFI = index.annotationForwardIndex(annot.get(0));
                        hitProperties.add(new AnnotInfo(annotationFI, p.getSensitivities().get(0), annotationFI.terms()));
                        originalOrderOfUnpackedProperties.add(PropInfo.hit(positionInUnpackedList));
                    }
                }
            }

            final int numAnnotations = hitProperties.size();
            long numberOfDocsProcessed;
            final AtomicInteger numberOfHitsProcessed = new AtomicInteger();
            final AtomicBoolean hitMaxHitsToCount = new AtomicBoolean(false);

            try (final BlockTimer c = BlockTimer.create("Top Level")) {

                // Collect all doc ids that match the given filter (or all docs if no filter specified)
                final List<Integer> docIds = new ArrayList<>();
                try (BlockTimer d = c.child("Gathering documents")) {
                    queryInfo.index().searcher().search(filterQuery == null ? new MatchAllDocsQuery() : filterQuery, new SimpleCollector() {
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

                // Start actually calculating the requests frequencies.
                if (hitProperties.isEmpty()) {
                    // Matched all tokens but not grouping by a specific annotation, only metadata
                    // This requires a different approach because we never retrieve the individual tokens if there's no annotation
                    // e.g. match '*' group by document year --
                    // What we do instead is for every document just retrieve how many tokens it contains (from its metadata), and add that count to the appropriate group
                    numberOfDocsProcessed = docIds.size();
                    try (BlockTimer f = c.child("Grouping documents (metadata only path)")) {
                        String fieldName = index.mainAnnotatedField().name();
                        DocPropertyAnnotatedFieldLength propTokens = new DocPropertyAnnotatedFieldLength(index, fieldName);
                        final int[] emptyTokenValuesArray = new int[0];

                        docIds.parallelStream().forEach(docId -> {
                            final int docLength = (int) propTokens.get(docId) - BlackLabIndex.IGNORE_EXTRA_CLOSING_TOKEN;
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0, docLength);
                            final PropertyValue[] metadataValuesForGroup = new PropertyValue[docProperties.size()];
                            for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                            numberOfHitsProcessed.addAndGet(docLength);

                            // Add all tokens in document to the group.
                            final GroupIdHash groupId = new GroupIdHash(emptyTokenValuesArray, emptyTokenValuesArray, metadataValuesForGroup, metadataValuesHash);
                            occurances.compute(groupId, (__, groupSizes) -> {
                                if (groupSizes != null) {
                                    groupSizes.hits += docLength;
                                    groupSizes.docs += 1;
                                    return groupSizes;
                                } else {
                                    return new OccurranceCounts(docLength, 1);
                                }
                            });
                        });
                    }
                } else {
                    // We do have hit properties, so we need to use both document metadata and the tokens from the forward index to
                    // calculate the frequencies.
                    // TODO: maybe we don't need to respect the maxHitsToCount setting here? The whole point of this
                    //       code is that it can perform this operation faster and using less memory, and the setting
                    //       exists to manage server load, so maybe we can ignore it here? I guess then we might need
                    //       another setting that can limit this operation as well.
                    final int maxHitsToCount = searchSettings.maxHitsToCount() > 0 ? searchSettings.maxHitsToCount() : Integer.MAX_VALUE;
                    //final IntUnaryOperator incrementUntilMax = (v) -> v < maxHitsToCount ? v + 1 : v;
                    final String fieldName = index.mainAnnotatedField().name();
                    final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

                    // Determine all the fields we want to be able to load, so we don't need to load the entire document
                    final List<String> annotationFINames = hitProperties.stream().map(tr -> tr.getAnnotationForwardIndex().annotation().forwardIndexIdField()).collect(Collectors.toList());
                    final Set<String> fieldsToLoad = new HashSet<>();
                    fieldsToLoad.add(lengthTokensFieldName);
                    fieldsToLoad.addAll(annotationFINames);

                    final IndexReader reader = queryInfo.index().reader();

                    numberOfDocsProcessed = docIds.parallelStream().filter(docId -> {

                        // If we've already exceeded the maximum, skip this doc
                        if (numberOfHitsProcessed.get() >= maxHitsToCount)
                            return false;

                        try {

                            // Step 1: read all values for the to-be-grouped annotations for this document
                            // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation

                            final Document doc = reader.document(docId, fieldsToLoad);
                            final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                            final List<int[]> sortValuesPerAnnotation = new ArrayList<>();

                            try (BlockTimer e = c.child("Read annotations from forward index")) {
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
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0, docLength);
                            final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                            for (int i = 0; i < docProperties.size(); ++i)
                                metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult);
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document

                            // now we have all values for all relevant annotations for this document
                            // iterate again and pair up the nth entries for all annotations, then store that as a group.
                            /** Keep track of term occurrences in this document; later we'll merge it with the global term frequencies */
                            Map<GroupIdHash, OccurranceCounts> occsInDoc = new HashMap<>();
                            try (BlockTimer f = c.child("Group tokens")) {

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


                                // If we exceeded maxHitsToCount, remember that and don't process more docs.
                                // (NOTE: we don't care if we don't get exactly maxHitsToCount in this case; just that
                                //  we stop the operation before the server is overloaded)
                                if (numberOfHitsProcessed.getAndUpdate(i -> i + docLength) >= maxHitsToCount) {
                                    hitMaxHitsToCount.set(true);
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
                    final int groupSizeHits = e.getValue().hits;
                    final int groupSizeDocs = e.getValue().docs;
                    final int[] annotationValues = e.getKey().tokenIds;
                    final PropertyValue[] metadataValues = e.getKey().metadataValues;
                    // allocate new - is not copied when moving into propertyvaluemultiple
                    final PropertyValue[] groupIdAsList = new PropertyValue[numAnnotations + numMetadataValues];

                    // Convert all raw values (integers) into their appropriate PropertyValues
                    // Taking care to preserve the order of the resultant PropertyValues with the order of the input HitProperties
                    int indexInOutput = 0;
                    for (PropInfo p : originalOrderOfUnpackedProperties) {
                        final int indexInInput = p.getIndexInList();
                        if (p.isDocProperty()) {
                            // is docprop, add PropertyValue as-is
                            groupIdAsList[indexInOutput++] = metadataValues[indexInInput];
                        } else {
                             // is hitprop, convert value to PropertyValue.
                            AnnotInfo annotInfo = hitProperties.get(indexInInput);
                            Annotation annot = annotInfo.getAnnotationForwardIndex().annotation();
                            MatchSensitivity sens = annotInfo.getMatchSensitivity();
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

            ResultsStats hitsStats = new ResultsStatsStatic(numberOfHitsProcessed.get(), numberOfHitsProcessed.get(), new MaxStats(hitMaxHitsToCount.get(), hitMaxHitsToCount.get()));
            ResultsStats docsStats = new ResultsStatsStatic((int) numberOfDocsProcessed, (int) numberOfDocsProcessed, new MaxStats(hitMaxHitsToCount.get(), hitMaxHitsToCount.get()));
            return HitGroups.fromList(queryInfo, groups, requestedGroupingProperty, null, null, hitsStats, docsStats);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
}
