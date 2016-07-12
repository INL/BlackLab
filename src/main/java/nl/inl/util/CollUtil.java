package nl.inl.util;

import java.util.AbstractSet;
import java.util.Iterator;

import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

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

}
