package nl.inl.blacklab.server.jobs;

import java.util.Map;

import nl.inl.blacklab.resultproperty.GroupProperty;
import nl.inl.blacklab.search.results.Hit;

public class HitGroupSortSettings {

    private GroupProperty<Hit> sortBy;

    private boolean reverse;

    public HitGroupSortSettings(GroupProperty<Hit> sortBy, boolean reverse) {
        super();
        this.sortBy = sortBy;
        this.reverse = reverse;
    }

    public GroupProperty<Hit> sortBy() {
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
