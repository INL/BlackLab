package nl.inl.blacklab.search.results;

import java.util.Set;
import java.util.TreeSet;

/**
 * Implements HitsSample by wrapping a Hits object.
 */
public class HitsSampleImpl extends HitsSample {

    HitsSampleImpl(Hits hits, SampleParameters parameters) {
        super(hits.queryInfo(), parameters);
        selectHits(hits, parameters.numberOfHits(hits.size()));
    }

    private void selectHits(Hits selectFrom, int numberOfHitsToSelect) {
        if (numberOfHitsToSelect > selectFrom.size())
            numberOfHitsToSelect = selectFrom.size(); // default to all hits in this case
        // Choose the hits
        Set<Integer> chosenHitIndices = new TreeSet<>();
        for (int i = 0; i < numberOfHitsToSelect; i++) {
            // Choose a hit we haven't chosen yet
            int hitIndex;
            do {
                hitIndex = random.nextInt(selectFrom.size());
            } while (chosenHitIndices.contains(hitIndex));
            chosenHitIndices.add(hitIndex);
        }

        // Add the hits in order of their index
        int previousDoc = -1;
        for (Integer hitIndex : chosenHitIndices) {
            Hit hit = selectFrom.get(hitIndex);
            if (hit.doc() != previousDoc) {
                docsRetrieved++;
                docsCounted++;
                previousDoc = hit.doc();
            }
            this.hits.add(hit);
            hitsCounted++;
        }
    }
    
    @Override
    public String toString() {
        return "HitsSampleImpl#" + resultsObjId();
    }

}
