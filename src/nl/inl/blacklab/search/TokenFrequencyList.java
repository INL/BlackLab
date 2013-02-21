package nl.inl.blacklab.search;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A collection of tokens and their frequencies.
 */
public class TokenFrequencyList extends AbstractList<TokenFrequency> {
	List<TokenFrequency> list;

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public Iterator<TokenFrequency> iterator() {
		return list.iterator();
	}

	@Override
	public TokenFrequency get(int index) {
		return list.get(index);
	}

	@Override
	public boolean add(TokenFrequency e) {
		return list.add(e);
	}

	public void sort() {
		Collections.sort(list);
	}

	public TokenFrequencyList() {
		super();
		list = new ArrayList<TokenFrequency>();
	}

	public TokenFrequencyList(int capacity) {
		super();
		list = new ArrayList<TokenFrequency>(capacity);
	}


}