package nl.inl.blacklab.search.results;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;

/**
 * A group of results, with its group identity and the results themselves.
 * 
 * @param <T> result type, e.g. Hit 
 */
public abstract class Group<T> implements Result<Group<T>> {
    
    protected PropertyValue groupIdentity;

    private Results<T, ? extends ResultProperty<T>> storedResults;
    
    private long totalSize;

    protected Group(PropertyValue groupIdentity, Results<T, ? extends ResultProperty<T>> storedResults, long totalSize) {
        this.groupIdentity = groupIdentity;
        this.storedResults = storedResults;
        this.totalSize = totalSize;
    }

    public PropertyValue identity() {
        return groupIdentity;
    }
    
    public Results<T, ? extends ResultProperty<T>> storedResults() {
        return storedResults;
    }
    
    public long numberOfStoredResults() {
        return storedResults != null ? storedResults.size() : 0;
    }

    public long size() {
        return totalSize;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + identity() + ", size=" + size() + ")";
    }

    @Override
    public int compareTo(Group<T> o) {
        return identity().compareTo(o.identity());
    }

}
