package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A collection of tokens and their (absolute) frequencies.
 *
 * This class calculates the total frequency of the entries added, but you can
 * also set the total frequency explicitly (after all entries have been added)
 * if you want to calculate relative frequencies based on a different total.
 */
public class TermFrequencyList implements Iterable<TermFrequency> {
    
    List<TermFrequency> list;

    long totalFrequency = 0;

    public TermFrequencyList(Map<String, Integer> wordFreq, boolean sort) {
        list = new ArrayList<>(wordFreq.size());
        for (Map.Entry<String, Integer> e : wordFreq.entrySet()) {
            list.add(new TermFrequency(e.getKey(), e.getValue()));
        }
        if (sort)
            Collections.sort(list);
    }

    TermFrequencyList(List<TermFrequency> list) {
        this.list = list;
        totalFrequency = 0;
        for (TermFrequency fr: list) {
            totalFrequency += fr.frequency;
        }
    }

    public int size() {
        return list.size();
    }

    @Override
    public Iterator<TermFrequency> iterator() {
        return list.iterator();
    }

    public TermFrequency get(int index) {
        return list.get(index);
    }

    /**
     * Get the frequency of a specific token
     *
     * @param token the token to get the frequency for
     * @return the frequency
     */
    public long getFrequency(String token) {
        // TODO: maybe speed this up by keeping a map of tokens and frequencies?
        for (TermFrequency tf : list) {
            if (tf.term.equals(token))
                return tf.frequency;
        }
        return 0;
    }

    public long getTotalFrequency() {
        return totalFrequency;
    }

    public TermFrequencyList subList(int fromIndex, int toIndex) {
        return new TermFrequencyList(list.subList(fromIndex, toIndex));
    }

}
