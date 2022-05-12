package org.ivdnt.blacklab.aggregator.logic;

import java.util.Comparator;

import org.ivdnt.blacklab.aggregator.helper.Util;
import org.ivdnt.blacklab.aggregator.representation.HitGroup;

public class HitGroupComparators {

    private static final Comparator<HitGroup> CMP_IDENTITY = (a, b) -> Util.DEFAULT_COLLATOR.compare(a.identity, b.identity);

    private static final Comparator<HitGroup> CMP_SIZE = (a, b) -> Long.compare(b.size, a.size);

    public static Comparator<HitGroup> deserialize(String sort) {
        boolean reversed = sort.charAt(0) == '-';
        if (reversed)
            sort = sort.substring(1);

        Comparator<HitGroup> cmp = sort.equals("identity") ? CMP_IDENTITY : CMP_SIZE;
        return reversed ? cmp.reversed() : cmp;
    }
}
