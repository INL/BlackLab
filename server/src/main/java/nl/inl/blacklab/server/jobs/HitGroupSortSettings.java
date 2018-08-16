package nl.inl.blacklab.server.jobs;

import java.util.Map;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitGroup;

public class HitGroupSortSettings {

    private ResultProperty<HitGroup> sortBy;

    private boolean reverse;

    public HitGroupSortSettings(ResultProperty<HitGroup> sortBy, boolean reverse) {
        super();
        this.sortBy = sortBy;
        this.reverse = reverse;
    }

    public ResultProperty<HitGroup> sortBy() {
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
