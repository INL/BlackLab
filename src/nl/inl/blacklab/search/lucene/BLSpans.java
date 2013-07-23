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


/**
 * Will be the base class for all our own Spans classes. Is able to give extra
 * guarantuees about the hits in this Spans object, such as if every
 * hit is equal in length, if there may be duplicates, etc. This information
 * will help us optimize certain operations, such as sequence queries, in certain
 * cases.
 *
 * The default implementation is appropriate for Spans classes that return only
 * single-term hits.
 */
public abstract class BLSpans extends SpansAbstract {

	/**
	 * When hit B follows hit A, is it guaranteed that B.end &gt;= A.end?
	 * Also, if A.end == B.end, is B.start &gt; A.start?
	 *
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsEndPointSorted() {
		return true;
	}

	/**
	 * When hit B follows hit A, is it guaranteed that B.start &gt;= A.start?
	 * Also, if A.start == B.start, is B.end &gt; A.end?
	 *
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsStartPointSorted() {
		return true;
	}

	/**
	 * Are all hits the same number of tokens long?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsAllSameLength() {
		return true;
	}

	/**
	 * If all hits are the same number of tokens long, how long?
	 * @return the length if all hits are guaranteed to be the same length and that length is known, or a negative number otherwise.
	 */
	public int hitsLength() {
		return 1;
	}

	/**
	 * Is it guaranteed that no two hits have the same start position?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsHaveUniqueStart() {
		return true;
	}

	/**
	 * Is it guaranteed that no two hits have the same end position?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsHaveUniqueEnd() {
		return true;
	}

	/**
	 * Is it guaranteed that no two hits have the same start and end position?
	 * @return true if this is guaranteed, false if not
	 */
	public boolean hitsAreUnique() {
		return true;
	}

}
