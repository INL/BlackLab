package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWord;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class HitGroupsTokenFrequencies extends HitGroups {

    public static final boolean TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED = false;

    public HitGroupsTokenFrequencies(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
        super(queryInfo, getResults(queryInfo, filterQuery, property, maxHits), property, null, null);
    }

    private static List<HitGroup> getResults(QueryInfo queryInfo, Query filterQuery, HitProperty property, int maxHits) {
       
        // UNTESTED AND SLOOOW... MAKE IT GO FASTER PLZ :)
        // - voorkom dat de hele tijd PropertyValue objects worden aangemaakt tijdens de telling
        // - waarschijnlijk ook niet met PropertyValueContextWord werken maar met integer term ids blijven werken tot het eind 
        
        // Determine the annotation forward indexes we need
        BlackLabIndex index = queryInfo.index();
        List<AnnotationForwardIndex> fis = property.needsContext().stream().map(a -> index.annotationForwardIndex(a)).collect(Collectors.toList());
        // TODO: We need the sensitivities, in the same order as the forward indexes. (see below)
        //List<MatchSensitivity> sensitivities = property.getSensitivities(); 
        
        DocResults results = index.queryDocuments(filterQuery);
        String fiidField = queryInfo.field().contentIdField();
        Map<PropertyValue, Integer> freq = new HashMap<>();
        for (DocResult result: results) {
            List<int[]> doc = new ArrayList<>();
            int n = Integer.MAX_VALUE;
            for (AnnotationForwardIndex fi: fis) {
                String strFiid = result.identity().luceneDoc().get(fiidField);
                int fiid = Integer.parseInt(strFiid);
                List<int[]> parts = fi.retrievePartsInt(fiid, new int[] { -1 }, new int[] { -1 });
                int[] annotDoc = parts.get(0);
                if (n > annotDoc.length)
                    n = annotDoc.length;
                doc.add(annotDoc);
            }
            
            // iterate through tokens and count frequency of each token
            // (NOTE: this is the slow part!)
            for (int i = 0; i < n; i++) {
                List<PropertyValue> ws = new ArrayList<>();
                for (int[] d: doc) {
                    MatchSensitivity sensitivity = MatchSensitivity.CASE_INSENSITIVE; // FIXME: get actual sensitivity from property... (see above)
                    ws.add(new PropertyValueContextWord(index, fis.get(i).annotation(), sensitivity, d[i]));
                }
                PropertyValue p = new PropertyValueMultiple(ws);
                Integer count = freq.get(p);
                if (count == null)
                    count = 0;
                count++;
                freq.put(p, count);
            }
            
        }
        return freq.entrySet().stream().map(e -> new HitGroup(e.getKey(), null, e.getValue())).collect(Collectors.toList());
    }
}
