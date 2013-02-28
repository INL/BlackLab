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
 * A TextPattern searching for TextPatterns that contain a hit from another TextPattern. This may be
 * used to search for sentences containing a certain word, etc.
 */
public class TextPatternContaining extends TextPatternCombiner {

	public TextPatternContaining(TextPattern containers, TextPattern search) {
		super(containers, search);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		T trContainers = clauses.get(0).translate(translator, context);
		T trSearch = clauses.get(1).translate(translator, context);
		return translator.containing(context, trContainers, trSearch);
	}

}
