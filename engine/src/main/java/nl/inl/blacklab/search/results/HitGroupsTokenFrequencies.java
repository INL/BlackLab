package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
import nl.inl.blacklab.resultproperty.DocPropertyMultiple;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocImpl;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class HitGroupsTokenFrequencies {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;

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
                   Arrays.equals(((GroupIdHash) obj).tokenIds, this.tokenIds) &&
                   Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues);
        }
    }

    public static HitGroups get(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        try {
            /** Maps from group Id to number of hits (left) and number of docs (right) */
            final ConcurrentHashMap<GroupIdHash, MutablePair<Integer, Integer>> occurances = new ConcurrentHashMap<>();
            
            int numAnnotations;
            List<Pair<AnnotationForwardIndex, String>> fiidFields;
            List<MatchSensitivity> sensitivities;
            List<DocProperty> docProperties; // document properties (metadatafields) that also need to be grouped on (e.g. all tokens, grouped by lemma (hitprop) + year (docprop). I.e. 100 hits for lemma "word" in docs from 2005)
            BlackLabIndex index;
            
            
            try (final Timer.Context c = groupingTimer.time()) {
                // Determine the annotation forward indexes we need
                index = queryInfo.index();
                fiidFields = property.needsContext() != null ? property.needsContext().stream()
                    .map(a -> Pair.of(index.annotationForwardIndex(a), index.annotationForwardIndex(a).annotation().forwardIndexIdField()))
                    .collect(Collectors.toList()) : new ArrayList<>();
                numAnnotations = fiidFields.size();
                sensitivities = property.getSensitivities() != null ? property.getSensitivities() : new ArrayList<>();
                
                DocProperty docProp = property.docPropsOnly();
                if (docProp instanceof DocPropertyMultiple) {
                    docProperties = ((DocPropertyMultiple) docProp).props();
                } else if (docProp != null) {
                    docProperties = Arrays.asList(docProp);
                } else {
                    docProperties = new ArrayList<>();
                }
                
                
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
                if (fiidFields.isEmpty()) {
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
                            final Document doc = reader.document(docId);
                            // separate array per document - need to combine with annotation props still
                            final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                            int docLength = -1;
                            int firstHitInDocument = 1;
                            
                            try (Timer.Context e = readTermIdsFromForwardIndexTerm.time()) {
                                for (Pair<AnnotationForwardIndex, String> annot : fiidFields) {
                                    final int fiid = doc.getField(annot.getRight()).numericValue().intValue();
                                    final List<int[]> tokenValues = annot.getLeft().retrievePartsInt(fiid, minusOne, minusOne);
                                    tokenValuesPerAnnotation.addAll(tokenValues);
                                    docLength = Math.max(docLength, tokenValues.get(0).length);
                                }
                            }
                            
                            /** Bookkeeping: track which groups we've already encountered in this document, so that we can count the n* of document in each group */
                            HashSet<GroupIdHash> groupsInThisDocument = new HashSet<>();
                           
                            final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0 , docLength);
                            final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                            final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document
                            for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                            
                            // now we have all values for all relevant annotations for this document
                            // iterate again and pair up the nth entries for all annotations, then mark that occurrence.
                            try (Timer.Context f = groupTermIds.time()) {
                                for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                    // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
                                    // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
                                    // so in essence it's also an "id", but a case-insensitive one.
                                    // The code to retrieve these insensitive positions may be slow, not sure.
                                    // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
                                    // that hashes the token ids instead of the sortpositions in that case.
                                    int[] tokenValues = new int[fiidFields.size()];
                                    int[] sortPositions = new int[tokenValues.length];
                                    for (int fieldIndex = 0; fieldIndex < fiidFields.size(); ++fieldIndex) {
                                        tokenValues[fieldIndex] = tokenValuesPerAnnotation.get(fieldIndex)[tokenIndex];
                                        sortPositions[fieldIndex] = fiidFields.get(fieldIndex).getLeft().terms().idToSortPosition(tokenValues[fieldIndex * 2], sensitivities.get(fieldIndex));
                                    }
                                    final GroupIdHash groupId = new GroupIdHash(tokenValues, sortPositions, metadataValuesForGroup, metadataValuesHash);
                                    occurances.compute(groupId, (__, groupSize) -> {
                                        if (groupSize != null) {
                                            groupSize.left += 1;
                                            groupSize.right += groupsInThisDocument.add(groupId) ? 1 : 0;
                                            return groupSize;
                                        } else {
                                            return MutablePair.of(1, groupsInThisDocument.add(groupId) ? 1 : 0);
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

            ConcurrentHashMap<PropertyValue, HitGroup> groups = new ConcurrentHashMap<>();
            try (final Timer.Context c = convertTermIdsToStrings.time()) {
                occurances.entrySet().parallelStream().forEach(e -> {
                    // allocate new - is not copied when moving into propertyvaluemultiple
                    final int groupSizeHits = e.getValue().getLeft();
                    final int groupSizeDocs = e.getValue().getRight();
                    final int[] annotationValues = e.getKey().tokenIds; 
                    final PropertyValue[] metadataValues = e.getKey().metadataValues;
                    final PropertyValue[] groupIdAsList = new PropertyValue[annotationValues.length + (metadataValues != null ? metadataValues.length : 0)];

                    for (int i = 0; i < numAnnotations; ++i) {
                        groupIdAsList[i] = new PropertyValueContextWords(index, fiidFields.get(i).getLeft().annotation(), sensitivities.get(i), new int[] {annotationValues[i]}, false);
                    }
                    
                    if (metadataValues != null) {
                        for (int i = 0; i < metadataValues.length; ++i) {
                            groupIdAsList[i + numAnnotations] = metadataValues[i];
                        }
                    }
                    
                    // Occurances contains groupings but they are all case-sensitive.
                    // since we may have been tasked to group case-insensitively, 
                    // we still need to collapse groups that only differ by capitalization of their constituent token values
                    // So check if the group already has an entry with different capitalization of the values:
                    
                    PropertyValue groupId = groupIdAsList.length > 1 ? new PropertyValueMultiple(groupIdAsList) : groupIdAsList[0];
                    
                    // use compute() function as otherwise read + write are non-atomic
                    groups.compute(groupId, (__, v) -> {
                        if (v != null) {
                            throw new RuntimeException("Identical groups - should never happen");
//                            v.storedResults().docsCounted += 1;
//                            v.storedResults().hitsCounted += v.size();
//                            return v;
                        } else {
                            return new HitGroupWithoutResults(queryInfo, groupId, groupSizeHits, groupSizeDocs, false, false);
                        }
                    });
                });
            }

            System.out.println("fast path used for grouping");
        
            // pass total documents explicitly since the groups have no stored results.
            return HitGroups.fromList(queryInfo, new ArrayList<>(groups.values()), property, null, null);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
}