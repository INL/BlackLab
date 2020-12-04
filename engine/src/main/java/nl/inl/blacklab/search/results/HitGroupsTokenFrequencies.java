package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
import nl.inl.blacklab.resultproperty.DocPropertyMultiple;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWord;
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
        private int[] tokenValues;
        private PropertyValue[] metadataValues;
        private int hash;

        /**
         * 
         * @param tokenValues
         * @param metadataValues
         * @param metadataValuesHash since many tokens per document, precalculate md hash for that thing
         */
        public GroupIdHash(int[] tokenValues, PropertyValue[] metadataValues, int metadataValuesHash) {
            this.tokenValues = tokenValues;
            this.metadataValues = metadataValues;
            hash = Arrays.hashCode(tokenValues) ^ metadataValuesHash;
        };

        @Override
        public int hashCode() {
            return hash;
        }

        // Assume only called with other instances of IdHash
        @Override
        public boolean equals(Object obj) {
            return ((GroupIdHash) obj).hash == this.hash && 
                Arrays.equals(((GroupIdHash) obj).tokenValues, this.tokenValues) && 
                Arrays.deepEquals(((GroupIdHash) obj).metadataValues, this.metadataValues);
        }
    }

    public static HitGroups get(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        try {
            final ConcurrentHashMap<GroupIdHash, Integer> occurances = new ConcurrentHashMap<>();
            
            int numAnnotations;
            List<AnnotationForwardIndex> fis;
            List<MatchSensitivity> sensitivities;
            List<DocProperty> docProperties; // document properties (metadatafields) that also need to be grouped on (e.g. all tokens, grouped by lemma (hitprop) + year (docprop). I.e. 100 hits for lemma "word" in docs from 2005)
            BlackLabIndex index;
            
            
            try (final Timer.Context c = groupingTimer.time()) {
                // Determine the annotation forward indexes we need
                index = queryInfo.index();
                fis = property.needsContext().stream().map(a -> index.annotationForwardIndex(a))
                        .collect(Collectors.toList());
                numAnnotations = fis.size();
                sensitivities = property.getSensitivities();
                
                
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
                final List<Pair<AnnotationForwardIndex, String>> fiidFieldNames = fis.stream().map(fi -> Pair.of(fi, fi.annotation().forwardIndexIdField())).collect(Collectors.toList());
                final int[] minusOne = new int[] { -1 };
                docIds.parallelStream().forEach(docId -> {
                    try {                        
                        final Document doc = reader.document(docId);
                        
                        // :( still need a propertyvalue
                        // 
                        
                        // separate array per document - need to combine with annotation props still
                        // so no use wrapping in propertyvaluemultiple
                        // (besides - propvaluemultiple inside propvaluemultiple is not supported)
                        final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();
                        int docLength = -1;
                        
                        try (Timer.Context e = readTermIdsFromForwardIndexTerm.time()) {
                            for (Pair<AnnotationForwardIndex, String> annot : fiidFieldNames) {
                                final int fiid = doc.getField(annot.getRight()).numericValue().intValue();
                                final List<int[]> tokenValues = annot.getLeft().retrievePartsInt(fiid, minusOne, minusOne);
                                tokenValuesPerAnnotation.addAll(tokenValues);
                                docLength = tokenValues.get(0).length;
                            }
                        }
                        
                        final DocResult synthesizedDocResult = DocResult.fromDoc(queryInfo, new PropertyValueDoc(new DocImpl(queryInfo.index(), docId)), 0 /* todo */, docLength);
                        final PropertyValue[] metadataValuesForGroup = !docProperties.isEmpty() ? new PropertyValue[docProperties.size()] : null;
                        final int metadataValuesHash = Arrays.hashCode(metadataValuesForGroup); // precompute, it's the same for all hits in document
                        for (int i = 0; i < docProperties.size(); ++i) { metadataValuesForGroup[i] = docProperties.get(i).get(synthesizedDocResult); }
                        
                        

                        // now we have all values for all relevant annotations for this document
                        // iterate again and pair up the nth entries for all annotations, then mark that occurrence.
                        try (Timer.Context f = groupTermIds.time()) {
                            for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                int[] tokenValues = new int[fiidFieldNames.size()];
                                for (int fieldIndex = 0; fieldIndex < fiidFieldNames.size(); ++fieldIndex) {
                                    tokenValues[fieldIndex] = tokenValuesPerAnnotation.get(fieldIndex)[tokenIndex];
                                }
                                final GroupIdHash groupId = new GroupIdHash(tokenValues, metadataValuesForGroup, metadataValuesHash);
                                occurances.compute(groupId, (__, groupSize) -> groupSize != null ? groupSize + 1 : 1);  
                            }
                        }
                    } catch (IOException e) {
                        throw BlackLabRuntimeException.wrap(e);
                    }
                });
            }

            ConcurrentHashMap<PropertyValueMultiple, HitGroup> groups = new ConcurrentHashMap<>();
            try (final Timer.Context c = convertTermIdsToStrings.time()) {
                occurances.entrySet().parallelStream().forEach(e -> {
                    // allocate new - is not copied when moving into propertyvaluemultiple
                    final int groupSize = e.getValue();
                    final int[] annotationValues = e.getKey().tokenValues; 
                    final PropertyValue[] metadataValues = e.getKey().metadataValues;
                    final PropertyValue[] groupIdAsList = new PropertyValue[annotationValues.length + (metadataValues != null ? metadataValues.length : 0)];

                    for (int i = 0; i < numAnnotations; ++i) {
                        groupIdAsList[i] = new PropertyValueContextWord(index, fis.get(i).annotation(), sensitivities.get(i), annotationValues[i]);
                    }
                    
                    if (metadataValues != null) {
                        for (int i = 0; i < metadataValues.length; ++i) {
                            groupIdAsList[i + numAnnotations] = metadataValues[i];
                        }
                    }
                    
                    // Occurances contains groupings but they are all case-sensitive.
                    // since we may have been tasked to group case-insensitively, 
                    // we still need to collapse groups that only differ by capitalization of their constituent token values
                    
                    PropertyValueMultiple groupId = new PropertyValueMultiple(groupIdAsList);
                    // So check if the group already has an entry with different capitalization of the values:
                    
                    // use compute() function as otherwise read + write are non-atomic
                    groups.compute(groupId, (__, v) -> v != null 
                        ? new HitGroup(queryInfo, groupId, v.size() + groupSize) // merge the groups if already one at the key
                        : new HitGroup(queryInfo, groupId, groupSize) // insert brand new otherwise
                    );
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