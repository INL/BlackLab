package nl.inl.blacklab.search.results;

/**
 * Base class for single result classes, e.g. Hit, DocResult, HitGroup, etc.
 *
 * @param <T> the type itself, e.g. Hit (for Comparable)
 */
public interface Result<T> extends Comparable<T> {

    @Override
    int compareTo(T o);

    @Override
    boolean equals(Object with);

    @Override
    String toString();

    @Override
    int hashCode();

}
