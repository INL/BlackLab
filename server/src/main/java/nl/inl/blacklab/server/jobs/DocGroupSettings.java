package nl.inl.blacklab.server.jobs;

import java.util.Map;

import nl.inl.blacklab.resultproperty.DocProperty;

public class DocGroupSettings {

    DocProperty groupBy;

    public DocGroupSettings(DocProperty groupBy) {
        super();
        this.groupBy = groupBy;
    }

    public DocProperty groupBy() {
        return groupBy;
    }

    @Override
    public String toString() {
        return "docgroup=" + groupBy.serialize();
    }

    public void getUrlParam(Map<String, String> param) {
        param.put("group", groupBy.serialize());
    }

}
