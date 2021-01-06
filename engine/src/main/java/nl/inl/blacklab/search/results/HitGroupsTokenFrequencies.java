package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SimpleCollector;

import io.dropwizard.metrics5.Timer;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocImpl;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class HitGroupsTokenFrequencies {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;
    public static final boolean DEBUG = true;
    
    private static final Timer groupingTimer = BlackLab.metrics.timer("building groups");
    private static final Timer getDocsTimer = BlackLab.metrics.timer("getting doc ids");
    private static final Timer readTermIdsFromForwardIndexTerm = BlackLab.metrics.timer("read doc token ids from FI");
    private static final Timer groupTermIds = BlackLab.metrics.timer("grouping term IDs");
    private static final Timer convertTermIdsToStrings = BlackLab.metrics.timer("converting term ID arrays to propertyvalues and group instances");

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

    public static HitGroups get(QueryInfo queryInfo, Query filterQuery, HitProperty requestedGroupingProperty, int maxHits) {
        try {
            /** Maps from group Id to number of hits (left) and number of docs (right) */
            final ConcurrentHashMap<GroupIdHash, MutablePair<Integer, Integer>> occurances = new ConcurrentHashMap<>();

            final BlackLabIndex index = queryInfo.index();
            /** document properties (metadatafields) that also need to be grouped on. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year") */
            final List<DocProperty> docProperties = new ArrayList<>(); 
            /** Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and forward index id field */
            final List<Triple<AnnotationForwardIndex, MatchSensitivity, String>> hitProperties = new ArrayList<>();
            /** Index in respective list, isDocProp */
            final List<Pair<Integer, Boolean>> originalOrderOfUnpackedProperties = new ArrayList<>();
            
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
                        final String annotationFIName = annotationFI.annotation().forwardIndexIdField();
                        hitProperties.add(Triple.of(annotationFI, p.getSensitivities().get(0), annotationFIName));
                        originalOrderOfUnpackedProperties.add(Pair.of(positionInUnpackedList, false));
                    }
                }
            }

            final int numAnnotations = hitProperties.size();

            try (final Timer.Context c = groupingTimer.time()) {
                final List<Integer> docIds = new ArrayList<>();
                try (Timer.Context d = getDocsTimer.time()) {
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

                final IndexReader reader = queryInfo.index().reader();
                final int[] minusOne = new int[] { -1 };

                // Matched all tokens but not grouping by a specific annotation, only metadata
                // This requires a different approach because we never retrieve the individual tokens if there's no annotation
                // e.g. match '*' group by document year --
                if (hitProperties.isEmpty()) {
                    String fieldName = index.mainAnnotatedField().name();
                    DocPropertyAnnotatedFieldLength propTokens = new DocPropertyAnnotatedFieldLength(index, fieldName);
                    final int[] emptyTokenValuesArray = new int[0];
                    
                    docIds.parallelStream().forEach(docId -> {
                        final int docLength = (int) propTokens.get(docId); // weird, doc length is Long, but group size is Int
                        final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0, docLength);
                        final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                        final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document
                        for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                        
                        // Add all tokens in document to the group.
                        try (Timer.Context f = groupTermIds.time()) {
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
                        }
                    });
                } else {
                    docIds.parallelStream().forEach(docId -> {
                        try {
                            
                            // Step 1: read all values for the to-be-grouped annotations for this document
                            // This will create one int[] for every annotation, containing ids that map to the values

                            final Document doc = reader.document(docId);
                            final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                            int docLength = -1;
                            
                            try (Timer.Context e = readTermIdsFromForwardIndexTerm.time()) {
                                for (Triple<AnnotationForwardIndex, MatchSensitivity, String> annot : hitProperties) {
                                    final int fiid = doc.getField(annot.getRight()).numericValue().intValue();
                                    final List<int[]> tokenValues = annot.getLeft().retrievePartsInt(fiid, minusOne, minusOne);
                                    tokenValuesPerAnnotation.addAll(tokenValues);
                                    docLength = Math.max(docLength, tokenValues.get(0).length);
                                }
                            }
                            
                           
                           // Step 2: retrieve the to-be-grouped metadata for this document
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0 , docLength);
                            final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                            for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document
                            
                            // now we have all values for all relevant annotations for this document
                            // iterate again and pair up the nth entries for all annotations, then store that as a group.
                            /** Bookkeeping: track which groups we've already encountered in this document, so that we can count the number of document in each group */
                            HashSet<GroupIdHash> groupsInThisDocument = new HashSet<>();
                            try (Timer.Context f = groupTermIds.time()) {
                                for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                    // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                                    // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                                    // so in essence it's also an "id", but a case-insensitive one.
                                    // The code to retrieve these insensitive positions may be slow, not sure.
                                    // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                                    // that hashes the token ids instead of the sortpositions in that case.
                                    int[] tokenValuesForThisToken = new int[numAnnotations];
                                    int[] sortPositions = new int[tokenValuesForThisToken.length];
                                    for (int fieldIndex = 0; fieldIndex < numAnnotations; ++fieldIndex) {
                                        final int termId = tokenValuesForThisToken[fieldIndex] = tokenValuesPerAnnotation.get(fieldIndex)[tokenIndex];
                                        sortPositions[fieldIndex] = hitProperties.get(fieldIndex).getLeft().terms().idToSortPosition(termId, hitProperties.get(fieldIndex).getMiddle());
                                    }
                                    final GroupIdHash groupId = new GroupIdHash(tokenValuesForThisToken, sortPositions, metadataValuesForGroup, metadataValuesHash);
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
                    });
                }
            }

            
            Set<PropertyValue> duplicateGroupsDebug = DEBUG ? new HashSet<PropertyValue>() : null;
            
            List<HitGroup> groups; 
            try (final Timer.Context c = convertTermIdsToStrings.time()) {
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
                        if (p.getRight()) { // is docprop
                            groupIdAsList[indexInOutput++] = metadataValues[indexInInput];
                        } else { // is hitprop, convert to propertyvalue.
                            Triple<AnnotationForwardIndex, MatchSensitivity, String> o = hitProperties.get(indexInInput);
                            groupIdAsList[indexInOutput++] = new PropertyValueContextWords(index, o.getLeft().annotation(), o.getMiddle(), new int[] {annotationValues[indexInInput]}, false);
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

            System.out.println("fast path used for grouping");
            return HitGroups.fromList(queryInfo, groups, requestedGroupingProperty, null, null);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
}