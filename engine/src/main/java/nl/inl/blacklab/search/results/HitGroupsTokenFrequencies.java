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
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValueContextWord;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class HitGroupsTokenFrequencies {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;

    private static final Timer groupingTimer = BlackLab.metrics.timer("building groups");
    private static final Timer getDocsTimer = BlackLab.metrics.timer("getting doc ids");
    private static final Timer readTermIdsFromForwardIndexTerm = BlackLab.metrics.timer("read doc token ids from FI");
    private static final Timer groupTermIds = BlackLab.metrics.timer("grouping term IDs");
    private static final Timer convertTermIdsToStrings = BlackLab.metrics.timer("converting term ID arrays to propertyvalues and group instances");


    private static class IdHash {
        private int[] id;
        private int hash;

        public IdHash(int[] id) {
            this.id = id;
            hash = Arrays.hashCode(id);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        // Assume only called with other instances of IdHash
        @Override
        public boolean equals(Object obj) {
            return ((IdHash) obj).hash == this.hash && Arrays.equals(((IdHash) obj).id, this.id);
        }
    }

    public static HitGroups get(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        try {
            final ConcurrentHashMap<IdHash, Integer> occurances = new ConcurrentHashMap<>();
            
            int numAnnotations;
            List<AnnotationForwardIndex> fis;
            List<MatchSensitivity> sensitivities;
            BlackLabIndex index;

            try (final Timer.Context c = groupingTimer.time()) { 
                // Determine the annotation forward indexes we need
                index = queryInfo.index();
                fis = property.needsContext().stream().map(a -> index.annotationForwardIndex(a))
                        .collect(Collectors.toList());
                numAnnotations = fis.size();
                sensitivities = property.getSensitivities();
                
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

                        // now we have all values for all relevant annotations for this document
                        // iterate again and pair up the nth entries for all annotations, then mark that occurrence.
                        try (Timer.Context f = groupTermIds.time()) {
                            for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                                int[] tokenValues = new int[fiidFieldNames.size()];
                                for (int fieldIndex = 0; fieldIndex < fiidFieldNames.size(); ++fieldIndex) {
                                    tokenValues[fieldIndex] = tokenValuesPerAnnotation.get(fieldIndex)[tokenIndex];
                                }
                                final IdHash groupId = new IdHash(tokenValues);
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
                    final PropertyValueContextWord[] groupIdAsList = new PropertyValueContextWord[numAnnotations];
                    final int[] annotationValues = e.getKey().id;
                    final int groupSize = e.getValue();
                    for (int i = 0; i < numAnnotations; ++i) {
                        groupIdAsList[i] = new PropertyValueContextWord(index, fis.get(i).annotation(),
                                sensitivities.get(i), annotationValues[i]);
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