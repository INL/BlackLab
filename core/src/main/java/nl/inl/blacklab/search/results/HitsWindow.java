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
public class HitsWindow extends HitsList {
    
    WindowStats windowStats;
    
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
        boolean hasNext = source.hitsProcessedAtLeast(first + windowSize + 1);
        windowStats = new WindowStats(hasNext, first, windowSize, number);
    }
    
    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    @Override
    public String toString() {
            return "HitsWindow#" + resultsObjId() + " (source=" + queryInfo().resultsObjectId() + ")";
    }

}
