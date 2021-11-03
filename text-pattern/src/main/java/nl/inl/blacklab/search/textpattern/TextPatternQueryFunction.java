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
package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;

/**
 * A TextPattern that applies a function to a list of patterns to produce a new pattern.
 *
 * Right now, this is used for testing purposes, e.g. to experiment with certain optimizations,
 * specifically forward index matching.
 */
public class TextPatternQueryFunction extends TextPattern {

    private String name;

    private List<TextPattern> args;

    public TextPatternQueryFunction(String name, List<TextPattern> args) {
        this.name = name;
        this.args = new ArrayList<>(args);

        if (!name.equals("_FI1") && !name.equals("_FI2"))
            throw new UnsupportedOperationException("Supported query function: _FI1, _FI2");

        if (args.size() != 2)
            throw new UnsupportedOperationException("Query function must get two arguments!");
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery a = args.get(0).translate(context);
        BLSpanQuery b = args.get(1).translate(context);

        switch (name) {
        case "_FI1":
        {
            // Resolve first clause using forward index and the second clause using regular reverse index
            ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromIndex(context.index(), a.getField());
            NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
            return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT, fiAccessor);
        }
        case "_FI2":
        {
            // Resolve second clause using forward index and the first clause using regular reverse index
            ForwardIndexAccessor fiAccessor = ForwardIndexAccessor.fromIndex(context.index(), b.getField());
            NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
            return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b, SpanQueryFiSeq.DIR_TO_RIGHT, fiAccessor);
        }
        default:
            throw new UnsupportedOperationException("Query function " + name + " not implemented!");
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((args == null) ? 0 : args.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternQueryFunction other = (TextPatternQueryFunction) obj;
        if (args == null) {
            if (other.args != null)
                return false;
        } else if (!args.equals(other.args))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "QFUNC(" + name + ", " + StringUtils.join(args, ", ") + ")";
    }
}
