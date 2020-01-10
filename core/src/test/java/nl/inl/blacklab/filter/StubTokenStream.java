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
package nl.inl.blacklab.filter;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

public class StubTokenStream extends TokenStream {
    private CharTermAttribute ta;

    private int i = -1;

    private String[] terms;

    public StubTokenStream(String[] terms) {
        this.terms = terms;
        ta = addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute pa = addAttribute(PositionIncrementAttribute.class);
        pa.setPositionIncrement(1);
    }

    @Override
    final public boolean incrementToken() {
        i++;
        if (i >= terms.length)
            return false;
        ta.copyBuffer(terms[i].toCharArray(), 0, terms[i].length());
        return true;
    }

}
