package nl.inl.blacklab.search.results;

import nl.inl.blacklab.resultproperty.PropertyValue;

public class HitGroupWithoutResults extends HitGroup {
    private static class HitsWithoutResults extends Hits {
        protected final boolean maxHitsProcessed;
        protected final boolean maxHitsCounted;

        public HitsWithoutResults(QueryInfo queryInfo, int totalHits, int totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
            super(queryInfo, true);
            this.hitsCounted = totalHits;
            this.docsCounted = totalDocuments;
            this.docsRetrieved = 0;

            this.maxHitsProcessed = maxHitsProcessed;
            this.maxHitsCounted = maxHitsCounted;
        }

        @Override
        protected void ensureResultsRead(int number) {
            // NOP
        }

        @Override
        public boolean doneProcessingAndCounting() {
            return true;
        }

        @Override
        public MaxStats maxStats() {
            return new MaxStats(maxHitsProcessed, maxHitsCounted);
        }
    }

    public HitGroupWithoutResults(QueryInfo queryInfo, PropertyValue groupIdentity, int totalHits, int totalDocuments, boolean maxHitsProcessed, boolean maxHitsCounted) {
        super(groupIdentity, new HitsWithoutResults(queryInfo, totalHits, totalDocuments, maxHitsCounted, maxHitsProcessed), totalHits);
    }
}
