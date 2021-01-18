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
package nl.inl.blacklab.search.lucene;

import java.util.Arrays;
import java.util.List;

/**
 * Combines SpanQueries using AND. Note that this means that only matches with
 * the same document id, the same start and the same end positions in all
 * SpanQueries will be kept.
 */
public class SpanQueryAnd extends SpanQueryAndNot {
    public SpanQueryAnd(BLSpanQuery first, BLSpanQuery second) {
        super(Arrays.asList(first, second), null);
    }

    public SpanQueryAnd(List<BLSpanQuery> clauscol) {
        super(clauscol, null);
    }

    public SpanQueryAnd(BLSpanQuery[] clauses) {
        super(Arrays.asList(clauses), null);
    }

    // no hashCode() and equals() because super class version is sufficient

}
