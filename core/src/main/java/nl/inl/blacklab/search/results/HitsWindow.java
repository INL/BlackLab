/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a subset of a Hits object, for example a page of hits.
 */
public class HitsWindow extends HitsList implements ResultsWindow {
    /**
     * Number of hits in the window
     */
    private int windowSize;

    /**
     * First hit in the window
     */
    private int first;

    private boolean hasNext;

    /**
     * Construct a HitsWindow object.
     *
     * NOTE: this method will be made package-private in a future release. Use
     * Hits.window() to construct a HitsWindow instead.
     *
     * @param source the larger Hits object we would like a window into
     * @param first the first hit in our window
     * @param windowSize the size of our window
     */
    HitsWindow(Hits source, int first, int windowSize) {
        super(source.queryInfo(), (List<Hit>) null);
        this.first = first;
        this.windowSize = windowSize;

        // Error if first out of range
        boolean emptyResultSet = !source.hitsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
                (!emptyResultSet && !source.hitsProcessedAtLeast(first + 1))) {
            throw new IllegalArgumentException("First hit out of range");
        }

        // Auto-clamp number
        int number = windowSize;
        if (!source.hitsProcessedAtLeast(first + number))
            number = source.size() - first;

        // Copy the hits we're interested in.
        hits = new ArrayList<>();
        if (source.hasCapturedGroups())
            capturedGroups = new CapturedGroupsImpl(source.capturedGroups().names());
        for (int i = first; i < first + number; i++) {
            Hit hit = source.get(i);
            hits.add(hit);
            if (capturedGroups != null)
                capturedGroups.put(hit, source.capturedGroups().get(hit));
            // OPT: copy context as well..?
        }
        hasNext = source.hitsProcessedAtLeast(first + windowSize + 1);
    }

    /**
     * Are there more hits in the original Hits object beyond our window?
     *
     * @return true if there are, false if not.
     */
    @Override
    public boolean hasNext() {
        return hasNext;
    }

    /**
     * Are there more hits in the original Hits object "to the left" of our window?
     *
     * @return true if there are, false if not.
     */
    @Override
    public boolean hasPrevious() {
        return first > 0;
    }

    /**
     * Where would the next window start?
     *
     * @return index of the first hit beyond our window
     */
    @Override
    public int nextFrom() {
        return first + hits.size();
    }

    /**
     * Where would the previous window start?
     *
     * @return index of the start hit for the previous page
     */
    @Override
    public int prevFrom() {
        return first - windowSize;
    }

    /**
     * What's the first in the window?
     *
     * @return index of the first hit
     */
    @Override
    public int first() {
        return first;
    }

    /**
     * What's the last in the window?
     *
     * @return index of the last hit
     */
    @Override
    public int last() {
        return first + hits.size() - 1;
    }

    /**
     * How many hits are in this window?
     *
     * Note that this may be different from the specified "window size", as the
     * window may not be full.
     *
     * @return number of hits
     */
    @Override
    public int windowSize() {
        return hits.size();
    }

    @Override
    public int requestedWindowSize() {
        return windowSize;
    }

    @Override
    public String toString() {
            return "HitsWindow#" + resultsObjId() + " (first=" + first + ", number=" + windowSize + ", source=" + queryInfo().resultsObjectId() + ")";
    }

}
