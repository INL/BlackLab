package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.HitGroupProperty;

public class HitGroupSortSettings {

    private final HitGroupProperty sortBy;

    public HitGroupSortSettings(HitGroupProperty sortBy) {
        super();
        this.sortBy = sortBy;
    }

    public HitGroupProperty sortBy() {
        return sortBy;
    }

    @Override
    public String toString() {
        return "hitgroupsort=" + sortBy;
    }

}
