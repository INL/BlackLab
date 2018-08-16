package nl.inl.blacklab.server.jobs;

import java.util.Map;

import nl.inl.blacklab.resultproperty.HitGroupProperty;

public class HitGroupSortSettings {

    private HitGroupProperty sortBy;

    private boolean reverse;

    public HitGroupSortSettings(HitGroupProperty sortBy, boolean reverse) {
        super();
        this.sortBy = sortBy;
        this.reverse = reverse;
    }

    public HitGroupProperty sortBy() {
        return sortBy;
    }

    public boolean reverse() {
        return reverse;
    }

    @Override
    public String toString() {
        return "hitgroupsort=" + sortBy + ", sortreverse=" + reverse;
    }

    public void getUrlParam(Map<String, String> param) {
        param.put("sort", (reverse ? "-" : "") + sortBy.serialize());
    }

}
