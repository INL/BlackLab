package nl.inl.blacklab.search.results;

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
