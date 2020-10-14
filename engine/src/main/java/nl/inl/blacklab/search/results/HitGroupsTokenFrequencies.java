package nl.inl.blacklab.search.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SimpleCollector;
import org.eclipse.collections.api.iterator.MutableIntIterator;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValueContextWord;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class HitGroupsTokenFrequencies extends HitGroups {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = true;

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
            return obj instanceof IdHash && ((IdHash) obj).hash == this.hash && Arrays.equals(((IdHash) obj).id, this.id);
        }

    }
    
    public HitGroupsTokenFrequencies(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        super(queryInfo, getResults(queryInfo, filterQuery, property, maxHits), property, null, null);
    }

    private static List<HitGroup> getResults(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        // Determine the annotation forward indexes we need
        BlackLabIndex index = queryInfo.index();
        List<AnnotationForwardIndex> fis = property.needsContext().stream().map(a -> index.annotationForwardIndex(a))
                .collect(Collectors.toList());
        final int numAnnotations = fis.size();

        // TODO: We need the sensitivities, in the same order as the forward indexes. (see below)
        //List<MatchSensitivity> sensitivities = property.getSensitivities();

        // speed up doc collection by not constructing DocResults object, IDS are good enough for us.
        final IntArrayList docIds = new IntArrayList();
        try {
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
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        // retrieve fiids for the documents we need.
        final String fiidField = queryInfo.field().contentIdField();
        final int[] fiids;

        final IndexReader reader = queryInfo.index().reader();
        final DocumentStoredFieldVisitor visitor = new DocumentStoredFieldVisitor(fiidField);

        try {
            // Not sure why this works this way but whatever.
            final MutableIntIterator it = docIds.intIterator();
            while (it.hasNext()) { reader.document(it.next(), visitor); }
            final List<IndexableField> values = visitor.getDocument().getFields(); // only retrieved one field - no need to filter on fiid fieldname again.
            fiids = new int[values.size()];
            for (int i = 0; i < fiids.length; ++i) {
                fiids[i] = values.get(i).numericValue().intValue() - 1; // wtf
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        // sort the fiids, maybe improving access times (they should now be more linear). Not sure if this has any effect, needs to be measured.
        Arrays.sort(fiids);

        
        final Map<IdHash, Integer> occurances = new HashMap<>();
        for (int fiid : fiids) { // in document here
            // okay nu hebben we id van het document in de forward index
            final List<int[]> valuesPerAnnotationForThisDoc = new ArrayList<>();

            int docLength = -1;
            for (AnnotationForwardIndex fi : fis) {
                List<int[]> parts = fi.retrievePartsInt(fiid, new int[] { -1 }, new int[] { -1 }); // returned list will only be length 1 - we're requesting 1 part: the entire document
                int[] thisAnnotationsValuesForThisDoc = parts.get(0);
                valuesPerAnnotationForThisDoc.add(thisAnnotationsValuesForThisDoc);
                docLength = thisAnnotationsValuesForThisDoc.length;
            }

            // iterate through tokens and count frequency of each token
            // (NOTE: this is the slow part!)


            for (int tokenIndex = 0; tokenIndex < docLength; ++tokenIndex) { // enter per token
                final int[] groupId = new int[numAnnotations];
                for (int i = 0; i < numAnnotations; ++i) { // enter per annotation
                    groupId[i] = valuesPerAnnotationForThisDoc.get(i)[tokenIndex]; // yuck, boxing, but whatever. we'll have to see whether it's slow in practice.
                }
                IdHash id = new IdHash(groupId);
                final Integer curOccurances = occurances.get(id);
                occurances.put(id, curOccurances == null ? 1 : curOccurances + 1);
            }
        }

        List<HitGroup> groups = new ArrayList<>();

        // Now (unfortunately)
        // map back the group entries to their actual values.
        for (Entry<IdHash, Integer> e : occurances.entrySet()) {
            // allocate new - is not copied when moving into propertyvaluemultiple
            final PropertyValueContextWord[] groupIdAsList = new PropertyValueContextWord[numAnnotations];
            final int[] annotationValues = e.getKey().id;
            final int groupSize = e.getValue();
            for (int i = 0; i < numAnnotations; ++i) {
                groupIdAsList[i] = new PropertyValueContextWord(index, fis.get(i).annotation(), MatchSensitivity.CASE_INSENSITIVE, annotationValues[i]);
            }

            PropertyValueMultiple groupId = new PropertyValueMultiple(groupIdAsList);
            groups.add(new HitGroup(queryInfo, groupId, groupSize));

        }

        return groups;
    }
}
