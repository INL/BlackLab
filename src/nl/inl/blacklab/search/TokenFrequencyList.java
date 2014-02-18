package nl.inl.blacklab.search;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A collection of tokens and their (absolute) frequencies.
 *
 * This class calculates the total frequency of the entries added,
 * but you can also set the total frequency explicitly (after all
 * entries have been added) if you want to calculate relative frequencies
 * based on a different total.
 */
public class TokenFrequencyList extends AbstractList<TokenFrequency> {
	List<TokenFrequency> list;

	long totalFrequency = 0;

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
		totalFrequency += e.frequency;
		return list.add(e);
	}

	public void sort() {
		Collections.sort(list);
	}

	/**
	 * Get the frequency of a specific token
	 *
	 * @param token the token to get the frequency for
	 * @return the frequency
	 */
	public long getFrequency(String token) {
		// TODO: maybe speed this up by keeping a map of tokens and frequencies?
		for (TokenFrequency tf: list) {
			if (tf.token.equals(token))
				return tf.frequency;
		}
		return 0;
	}

	public TokenFrequencyList() {
		super();
		list = new ArrayList<TokenFrequency>();
	}

	public TokenFrequencyList(int capacity) {
		super();
		list = new ArrayList<TokenFrequency>(capacity);
	}

	public long getTotalFrequency() {
		return totalFrequency;
	}

	public void setTotalFrequency(long totalFrequency) {
		this.totalFrequency = totalFrequency;
	}

}
