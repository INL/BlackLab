package nl.inl.blacklab.server.jobs;

import java.util.Map;

import nl.inl.blacklab.resultproperty.HitGroupProperty;

public class HitGroupSortSettings {

    private HitGroupProperty sortBy;

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

    public void getUrlParam(Map<String, String> param) {
        param.put("sort", sortBy.serialize());
    }

}
