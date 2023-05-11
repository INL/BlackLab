package nl.inl.blacklab.search.lucene;

/**
 * Wrapper for SpanGuarantees that allows us to override some methods.
 */
public abstract class SpanGuaranteesAdapter implements SpanGuarantees {

    private final SpanGuarantees wrapped;

    public SpanGuaranteesAdapter() {
        this(NONE);
    }

    public SpanGuaranteesAdapter(SpanGuarantees wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean okayToInvertForOptimization() {
        return wrapped.okayToInvertForOptimization();
    }

    @Override
    public boolean isSingleTokenNot() {
        return wrapped.isSingleTokenNot();
    }

    @Override
    public boolean producesSingleTokens() {
        return wrapped.producesSingleTokens();
    }

    @Override
    public boolean hitsAllSameLength() {
        return wrapped.hitsAllSameLength();
    }

    @Override
    public int hitsLengthMin() {
        return wrapped.hitsLengthMin();
    }

    @Override
    public int hitsLengthMax() {
        return wrapped.hitsLengthMax();
    }

    @Override
    public boolean hitsEndPointSorted() {
        return wrapped.hitsEndPointSorted();
    }

    @Override
    public boolean hitsStartPointSorted() {
        return wrapped.hitsStartPointSorted();
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return wrapped.hitsHaveUniqueStart();
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return wrapped.hitsHaveUniqueEnd();
    }

    @Override
    public boolean hitsAreUniqueWithMatchInfo() {
        return wrapped.hitsAreUniqueWithMatchInfo();
    }

    @Override
    public boolean hitsAreUnique() {
        return wrapped.hitsAreUnique();
    }

    @Override
    public boolean hitsCanOverlap() {
        return wrapped.hitsCanOverlap();
    }

    @Override
    public boolean isSingleAnyToken() {
        return wrapped.isSingleAnyToken();
    }
}
