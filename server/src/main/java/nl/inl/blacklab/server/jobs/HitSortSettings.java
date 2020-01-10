package nl.inl.blacklab.server.jobs;

import java.util.Map;

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

    public void getUrlParam(Map<String, String> param) {
        if (sortBy != null)
            param.put("sort", sortBy.serialize());
    }

}
