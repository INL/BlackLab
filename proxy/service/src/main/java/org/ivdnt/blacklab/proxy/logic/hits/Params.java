package org.ivdnt.blacklab.proxy.logic.hits;

import java.util.Objects;

/** Search parameters */
class Params {
    final String corpusName;
    final String patt;
    final String filter;
    final String sort;
    final String group;
    final String viewGroup;

    public Params(String corpusName, String patt, String filter, String sort, String group, String viewGroup) {
        this.corpusName = corpusName;
        this.patt = patt;
        this.filter = filter;
        this.sort = sort;
        this.group = group;
        this.viewGroup = viewGroup;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Params params = (Params) o;
        return corpusName.equals(params.corpusName) && patt.equals(params.patt) && filter.equals(params.filter)
                && sort.equals(params.sort) && group.equals(params.group) && viewGroup.equals(params.viewGroup);
    }

    @Override public int hashCode() {
        return Objects.hash(corpusName, patt, filter, sort, group, viewGroup);
    }
}
