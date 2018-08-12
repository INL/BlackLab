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

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * A list of DocResult objects (document-level query results). The list may be
 * sorted by calling DocResults.sort().
 */
public class DocResultsWindow extends DocResults {
    
    private WindowStats windowStats;

    /**
     *
     * @param source
     * @param first
     * @param numberPerPage
     */
    DocResultsWindow(DocResults source, int first, int numberPerPage) {
        super(source.queryInfo());

        boolean emptyResultSet = !source.docsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
                (!emptyResultSet && !source.docsProcessedAtLeast(first + 1))) {
            throw new BlackLabRuntimeException("First hit out of range");
        }

        // Auto-clamp number
        int number = numberPerPage;
        if (!source.docsProcessedAtLeast(first + number))
            number = source.size() - first;

        // Make sublist (don't use sublist because the backing list may change if not
        // all hits have been read yet)
        results = new ArrayList<>();
        for (int i = first; i < first + number; i++) {
            results.add(source.get(i));
        }
        windowStats = new WindowStats(source.docsProcessedAtLeast(first + number + 1), first, numberPerPage, number);
    }
    
    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

}
