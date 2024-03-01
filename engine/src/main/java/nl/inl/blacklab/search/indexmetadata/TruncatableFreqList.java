package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import nl.inl.util.LimitUtil;

/**
 * Possibly truncated value frequency list.
 * Used for metadata, annotation and relation attribute values.
 */
public class TruncatableFreqList implements LimitUtil.Limitable<TruncatableFreqList> {

    private long limitValues = Integer.MAX_VALUE;

    private final Map<String, Long> values;

    private boolean truncated;

    public TruncatableFreqList(long limitValues) {
        this.limitValues = limitValues;
        values = new HashMap<>();
        truncated = false;
    }

    public TruncatableFreqList(Map<String, Long> values, boolean truncated) {
        this.values = values;
        this.truncated = truncated;
    }

    public static TruncatableFreqList dummy() {
        return new TruncatableFreqList(0);
    }

    public TruncatableFreqList truncated(long maxValues) {
        if (values.size() == maxValues || !truncated && values.size() < maxValues) {
            // Current object is fine, either truncated to the right value or no need to truncate.
            return this;
        }
        if (truncated && values.size() < maxValues)
            throw new IllegalArgumentException(
                    "Cannot re-truncate value list of size " + values.size() + " to " + maxValues);
        return new TruncatableFreqList(LimitUtil.limit(values, maxValues), true);
    }

    public boolean canTruncateTo(long maxValues) {
        return !truncated || maxValues <= values.size();
    }

    public void add(String value, long count) {
        if (values.containsKey(value)) {
            // Seen this value before; increment frequency
            values.compute(value, (__, prevCount) -> prevCount + count);
        } else {
            // New value; add it
            if (values.size() >= limitValues) {
                // Reached the limit; stop storing now and indicate that there's more.
                truncated = true;
            } else {
                values.put(value, count);
            }
        }
    }

    public void add(String value) {
        add(value, 1);
    }

    public Map<String, Long> getValues() {
        return Collections.unmodifiableMap(values);
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int size() {
        return values.size();
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public void subtract(String value, int amount) {
        values.compute(value, (__, prevCount) -> prevCount == null || prevCount <= amount ? null : prevCount - amount);
    }

    @Override
    public TruncatableFreqList withLimit(long max) {
        return truncated(max);
    }
}
