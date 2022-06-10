package org.ivdnt.blacklab.aggregator.logic.hits;

import java.util.Objects;

/** Search parameters */
class Params {
    final String corpusName;
    final String patt;
    final String sort;
    final String group;
    final String viewGroup;

    public Params(String corpusName, String patt, String sort, String group, String viewGroup) {
        this.corpusName = corpusName;
        this.patt = patt;
        this.sort = sort;
        this.group = group;
        this.viewGroup = viewGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Params params = (Params) o;
        return Objects.equals(corpusName, params.corpusName) && Objects.equals(patt, params.patt)
                && Objects.equals(sort, params.sort) && Objects.equals(group, params.group)
                && Objects.equals(viewGroup, params.viewGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(corpusName, patt, sort, group, viewGroup);
    }
}
