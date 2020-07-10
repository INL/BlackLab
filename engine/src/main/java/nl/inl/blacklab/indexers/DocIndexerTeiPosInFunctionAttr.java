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

import nl.inl.blacklab.index.DocWriter;

/**
 * Index a TEI P4/P5 file with the PoS in the "function" attribute.
 *
 * For information about TEI, see http://www.tei-c.org/
 */
public class DocIndexerTeiPosInFunctionAttr extends DocIndexerTeiBase {

    public static String getDisplayName() {
        return "TEI-DocIndexer-function (alternate TEI indexer)";
    }

    public static String getDescription() {
        return "Main contents should be in body element, should be tokenized and PoS tags should be in the function attribute.";
    }

    public DocIndexerTeiPosInFunctionAttr(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader, "body", false);
    }
}
