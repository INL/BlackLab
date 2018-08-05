package nl.inl.blacklab.interfaces.results;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Property of a result (hit, doc, group, ...)
 * 
 * This is used to sort and group on a variety of properties, like context,
 * metadata fields, group size, etc.
 *
 * @param <Result> type of result
 */
public interface ResultProperty<Result> extends Comparator<Result>, Iterable<ResultProperty<Result>> {
	
	ResultPropertyValue get(Result result);

	/**
	 * Compares two hits on this property.
	 *
	 * The default implementation uses get() to compare
	 * the two hits. Subclasses may override this method to
	 * provide a more efficient implementation.
	 *
	 * @param a first hit
	 * @param b second hit
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	default int compare(Result a, Result b) {
		return get(a).compareTo(get(b));
	}

    /**
     * Is the comparison reversed?
     * 
     * NOTE: {@link #compare(Object, Object) compare()} should take this into account;
     * {@link #get(Object) get()} should not, as it can't always do that
     * (we can make a number negative, but we can't do that with a string)
     * 
     * @return true if it is, false if not
     */
    boolean isReverse();

    /**
     * Serialize this Property so we can deserialize it later (to pass it
     * via URL, for example)
     * @return the String representation of this HitProperty
     */
    String serialize();

	String name();
    
    /**
     * Is this a compound of multiple property values?
     * 
     * If so, call iterator() or stream() to access them.
     * 
     * @return true if this is a compound, false if not
     */
    default boolean isCompound() {
        return false;
    }

    /**
     * Iterate over any constituent result properties.
     * 
     * Because it is possible to sort or group on multiple properties, 
     * it is useful to be able to retrieve them all separately,
     * e.g. for display in the interface.
     * 
     * For single values, will return the empty iterator. Check {@link #isCompound()} to know
     * whether or not it is useful to call this method.
     * 
     * @return iterator over constituent values
     */
    @Override
    default Iterator<ResultProperty<Result>> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Stream any constituent result properties.
     * 
     * Because it is possible to sort or group on multiple properties, 
     * it is useful to be able to retrieve them all separately,
     * e.g. for display in the interface.
     * 
     * For single values, will return the empty stream. Check {@link #isCompound()} to know
     * whether or not it is useful to call this method.
     * 
     * @return stream of constituent values
     */
    default Stream<ResultProperty<Result>> stream() {
        return Stream.empty();
    }

}
