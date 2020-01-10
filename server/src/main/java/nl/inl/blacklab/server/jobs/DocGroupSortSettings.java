package nl.inl.blacklab.server.jobs;

import java.util.Map;

import nl.inl.blacklab.resultproperty.DocGroupProperty;

public class DocGroupSortSettings {

    private DocGroupProperty sortBy;

    public DocGroupSortSettings(DocGroupProperty sortBy) {
        super();
        this.sortBy = sortBy;
    }

    public DocGroupProperty sortBy() {
        return sortBy;
    }

    @Override
    public String toString() {
        return "docgroupsort=" + sortBy;
    }

    public void getUrlParam(Map<String, String> param) {
        param.put("sort", sortBy.serialize());
    }

}
