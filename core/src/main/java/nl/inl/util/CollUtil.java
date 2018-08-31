package nl.inl.util;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

public class CollUtil {

    public static AbstractSet<Integer> toJavaSet(final MutableIntSet keySet) {
        return new AbstractSet<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                final IntIterator it = keySet.intIterator();
                return new Iterator<Integer>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Integer next() {
                        return it.next();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return keySet.size();
            }

        };
    }

    public static List<Integer> toJavaList(final IntArrayList increments) {
        return new AbstractList<Integer>() {
            @Override
            public Integer get(int index) {
                return increments.get(index);
            }

            @Override
            public int size() {
                return increments.size();
            }
        };
    }

}
