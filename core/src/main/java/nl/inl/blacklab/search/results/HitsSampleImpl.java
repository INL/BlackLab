package nl.inl.blacklab.search.results;

import java.util.Set;
import java.util.TreeSet;

/**
 * Implements HitsSample by wrapping a Hits object.
 */
public class HitsSampleImpl extends HitsSample {

    HitsSampleImpl(Hits hits, float ratio, long seed) {
        super(hits.queryInfo(), ratio, seed);
        if (ratio < 0 || ratio > 1)
            throw new IllegalArgumentException("ratio must be in the range 0-1");

        // Determine how many hits there are, and how many to choose
        int totalNumberOfHits = hits.size();
        numberOfHitsToSelect = Math.round(totalNumberOfHits * ratio);
        if (numberOfHitsToSelect == 0 && totalNumberOfHits > 0 && ratio > 0)
            numberOfHitsToSelect = 1; // always choose at least one hit, unless we specify ratio 0 (why..??)

        selectHits(hits);
    }

    HitsSampleImpl(Hits hits, int number, long seed) {
        super(hits.queryInfo(), number, seed);
        if (number < 0)
            throw new IllegalArgumentException("Negative sample number specified");
        if (number > hits.size())
            numberOfHitsToSelect = number = hits.size(); // default to all hits in this case

        // Determine how many hits there are, and how many to choose
        ratioOfHitsToSelect = (float) number / hits.size();

        selectHits(hits);
    }

    private void selectHits(Hits selectFrom) {
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
