package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SimpleCollector;
import org.eclipse.collections.api.iterator.MutableIntIterator;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import io.dropwizard.metrics5.Timer;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValueContextWord;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class HitGroupsTokenFrequencies extends HitGroups {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;

    private static final Timer groupingTimer = BlackLab.metrics.timer("building groups");
    private static final Timer getDocsTimer = BlackLab.metrics.timer("getting doc ids");
    private static final Timer getFITimer = BlackLab.metrics.timer("reading FI");
    private static final Timer countTokensTimer = BlackLab.metrics.timer("grouping tokens");
    private static final Timer propertyValueTimer = BlackLab.metrics.timer("converting to propertyvalues");


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

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IdHash && ((IdHash) obj).hash == this.hash
                    && Arrays.equals(((IdHash) obj).id, this.id);
        }
    }

    public HitGroupsTokenFrequencies(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        super(queryInfo, getResults(queryInfo, filterQuery, property, maxHits), property, null, null);
    }

    private static List<HitGroup> getResults(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        try {
            final Map<IdHash, Integer> occurances = new HashMap<>();
            int numAnnotations;
            List<AnnotationForwardIndex> fis;
            BlackLabIndex index;
            try (final Timer.Context c = groupingTimer.time()) {
             // Determine the annotation forward indexes we need
                index = queryInfo.index();
                fis = property.needsContext().stream().map(a -> index.annotationForwardIndex(a))
                        .collect(Collectors.toList());
                numAnnotations = fis.size();

                // TODO: We need the sensitivities, in the same order as the forward indexes. (see below)
                //List<MatchSensitivity> sensitivities = property.getSensitivities();

                final IntArrayList docIds = new IntArrayList();
                try (Timer.Context d = getDocsTimer.time()) {
                    queryInfo.index().searcher().search(filterQuery == null ? new MatchAllDocsQuery() : filterQuery,
new SimpleCollector() {
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

                final MutableIntIterator docIdIt = docIds.intIterator();
                while (docIdIt.hasNext()) {
                    final int docId = docIdIt.next();
                    final Document doc = reader.document(docId);
                    final List<int[]> tokenValuesPerAnnotation = new ArrayList<>();

                    int docLength = -1;

                    try (Timer.Context e = getFITimer.time()) {
                        for (Pair<AnnotationForwardIndex, String> annot : fiidFieldNames) {
                            final int fiid = doc.getField(annot.getRight()).numericValue().intValue();
                            final List<int[]> tokenValues = annot.getLeft().retrievePartsInt(fiid, new int[] {-1}, new int[] {-1});
                            tokenValuesPerAnnotation.addAll(tokenValues);
                            docLength = tokenValues.get(0).length;
                        }
                    }

                    // now we have all values for all relevant annotations for this document
                    // iterate again and pair up the nth entries for all annotations, then mark that occurance.
                    try (Timer.Context f = countTokensTimer.time()) {
                        for (int tokenIndex = 0; tokenIndex < docLength; ++ tokenIndex) {
                            int[] tokenValues = new int[fiidFieldNames.size()];
                            for (int fieldIndex = 0; fieldIndex < fiidFieldNames.size(); ++fieldIndex) {
                                tokenValues[fieldIndex] = tokenValuesPerAnnotation.get(fieldIndex)[tokenIndex];
                            }
                            final IdHash id = new IdHash(tokenValues);
                            final Integer groupSize = occurances.get(id);
                            occurances.put(id, groupSize == null ? 1 : groupSize + 1);
                        }
                    }
                }
            }


            List<HitGroup> groups = new ArrayList<>();
            try (final Timer.Context c = propertyValueTimer.time()) {
                // Now (unfortunately)
                // map back the group entries to their actual values.
                for (Entry<IdHash, Integer> e : occurances.entrySet()) {
                    // allocate new - is not copied when moving into propertyvaluemultiple
                    final PropertyValueContextWord[] groupIdAsList = new PropertyValueContextWord[numAnnotations];
                    final int[] annotationValues = e.getKey().id;
                    final int groupSize = e.getValue();
                    for (int i = 0; i < numAnnotations; ++i) {
                        groupIdAsList[i] = new PropertyValueContextWord(index, fis.get(i).annotation(),
                                MatchSensitivity.CASE_INSENSITIVE, annotationValues[i]);
                    }

                    PropertyValueMultiple groupId = new PropertyValueMultiple(groupIdAsList);
                    groups.add(new HitGroup(queryInfo, groupId, groupSize));
                }
            }

            System.out.println("fast path used for grouping");
            return groups;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
}