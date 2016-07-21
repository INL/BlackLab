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
package nl.inl.blacklab.search;




/**
 * NOT operator for TextPattern queries at token and sequence level.
 * Really only makes sense for 1-token clauses, as it produces all tokens
 * that don't match the clause.
 */
public class TextPatternNot extends TextPatternCombiner {
	public TextPatternNot(TextPattern clause) {
		super(clause);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		//throw new RuntimeException("Cannot search for isolated NOT query (must always be AND NOT)");
		return translator.not(context, clauses.get(0).translate(translator, context));
	}

	@Override
	public TextPattern inverted() {
		return clauses.get(0); // Just return our clause, dropping the NOT operation
	}

	@Override
	protected boolean okayToInvertForOptimization() {
		// Yes, inverting is actually an improvement
		return true;
	}

	@Override
	public boolean isSingleTokenNot() {
		return true;
	}

	/**
	 * Rewrites NOT queries by returning the inverted rewritten clause.
	 *
	 * This eliminates double-NOT constructions which would be relatively inefficient
	 * to execute.
	 */
	@Override
	public TextPattern rewrite() {
		TextPattern rewritten = clauses.get(0).rewrite();
		if (rewritten == clauses.get(0) && !rewritten.okayToInvertForOptimization())
			return this; // Nothing to rewrite
		return rewritten.inverted();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternNot) {
			return super.equals(obj);
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return true;
	}

	@Override
	public int getMinLength() {
		return 1;
	}

	@Override
	public int getMaxLength() {
		return 1;
	}

	@Override
	public String toString(QueryExecutionContext context) {
		return "NOT(" + clauses.get(0).toString(context) + ")";
	}
}
