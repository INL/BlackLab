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
 * A TextPattern returning hits from the "include" clause, but only in documents where the "exclude"
 * clause doesn't occur.
 */
public class TextPatternDocLevelAndNot extends TextPattern {

	private TextPattern include;

	private TextPattern exclude;

	public TextPatternDocLevelAndNot(TextPattern include, TextPattern exclude) {
		this.include = include;
		this.exclude = exclude;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.docLevelAndNot(include.translate(translator, context),
				exclude.translate(translator, context));
	}

	@Override
	public TextPattern rewrite() {
		TextPattern inclRewr = include.rewrite();
		TextPattern exclRewr = exclude.rewrite();
		if (inclRewr == include && exclRewr == exclude)
			return this; // Nothing to rewrite
		return new TextPatternDocLevelAndNot(inclRewr, exclRewr);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternDocLevelAndNot) {
			return include.equals(((TextPatternDocLevelAndNot) obj).include) &&
					exclude.equals(((TextPatternDocLevelAndNot) obj).exclude);
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return include.hasConstantLength();
	}

	@Override
	public int getMinLength() {
		return include.getMinLength();
	}

	@Override
	public int getMaxLength() {
		return include.getMaxLength();
	}

}
