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
package nl.inl.blacklab.indexers;

import java.io.Reader;

import nl.inl.blacklab.index.Indexer;


/**
 * Index a TEI P4/P5 file.
 * For information about TEI, see http://www.tei-c.org/
 * @deprecated use DocIndexerTei, which supports (basic) P4 and P5
 */
@Deprecated
public class DocIndexerTeiP4 extends DocIndexerTei {
	public DocIndexerTeiP4(Indexer indexer, String fileName, Reader reader) {
		super(indexer, fileName, reader);
	}
}
