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

/**
 * Class for a hit. Normally, hits are iterated over in a Lucene Spans object,
 * but in some places, it makes sense to place hits in separate objects: when
 * caching or sorting hits, or just for convenience in client code.
 *
 * This class has public members for the sake of efficiency; this makes a
 * non-trivial difference when iterating over hundreds of thousands of hits.
 */
public final class HitImpl implements Hit {

    /** The Lucene doc this hits occurs in */
    private int doc;

    /**
     * End of this hit's span (in word positions).
     *
     * Note that this actually points to the first word not in the hit (just like
     * Spans).
     */
    private int end;

    /** Start of this hit's span (in word positions) */
    private int start;

    /**
     * Construct a hit object
     *
     * @param doc the document
     * @param start start of the hit (word positions)
     * @param end end of the hit (word positions)
     */
    protected HitImpl(int doc, int start, int end) {
        this.doc = doc;
        this.start = start;
        this.end = end;
    }

    @Override
    public int doc() {
        return doc;
    }

    @Override
    public int end() {
        return end;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public boolean equals(Object with) {
        if (this == with)
            return true;
        if (with instanceof Hit) {
            Hit o = (Hit) with;
            return doc() == o.doc() && start() == o.start() && end() == o.end();
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("doc %d, words %d-%d", doc(), start(), end());
    }
    
    @Override
    public int hashCode() {
        return (doc() * 17 + start()) * 31 + end();
    }
    
    // POSSIBLE FUTURE OPTIMIZATION

//    /**
//     * Cached hash code, or Integer.MIN_VALUE if not calculated yet.
//     * 
//     * Can help when using Hit as a key in HashMap, e.g. in CapturedGroups 
//     * and possibly with Contexts in the future.
//     * 
//     * Does cost about 17% extra memory for Hit objects. 
//     */
//    private int hashCode = Integer.MIN_VALUE;
//    
//    @Override
//    public int hashCode() {
//        if (hashCode == Integer.MIN_VALUE) {
//            hashCode = (doc() * 17 + start()) * 31 + end();
//        }
//        return hashCode;
//    }
    
    
}
