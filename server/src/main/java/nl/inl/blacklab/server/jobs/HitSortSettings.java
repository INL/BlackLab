package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.HitProperty;

public class HitSortSettings {

    private HitProperty sortBy;

    public HitSortSettings(HitProperty sortBy) {
        super();
        this.sortBy = sortBy;
    }

    public HitProperty sortBy() {
        return sortBy;
    }

    @Override
    public String toString() {
        return "hitsort=" + (sortBy == null ? "" : sortBy.serialize());
    }

}
