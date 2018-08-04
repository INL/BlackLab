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
package nl.inl.blacklab.resultproperty;

import java.util.Comparator;

import nl.inl.blacklab.perdocument.DocResult;

public class ComparatorDocProperty implements Comparator<DocResult> {
    private DocProperty prop;

    boolean sortReverse;

    public ComparatorDocProperty(DocProperty prop) {
        this.prop = prop;
        sortReverse = prop.defaultSortDescending();
    }

    @Override
    public int compare(DocResult first, DocResult second) {
        return sortReverse ? prop.compare(second, first) : prop.compare(first, second);
    }
}
