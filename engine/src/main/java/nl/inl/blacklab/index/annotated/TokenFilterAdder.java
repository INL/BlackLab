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
/**
 *
 */
package nl.inl.blacklab.index.annotated;

import org.apache.lucene.analysis.TokenStream;

/**
 * Offers an interface to add a number of TokenFilters to a TokenStream input.
 *
 * Used by AnnotatedFieldWriter, to allow the calling application control over how
 * the different properties are indexed. Implementations should probably just
 * use anonymous class definitions. See DocIndexerPageXml for examples.
 */
public interface TokenFilterAdder {
    TokenStream addFilters(TokenStream input);
}
