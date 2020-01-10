package nl.inl.blacklab.server.jobs;

import java.util.Map;

public class HitGroupSettings {
    private String groupBy;

    public HitGroupSettings(String groupBy) {
        super();
        this.groupBy = groupBy;
    }

    public String groupBy() {
        return groupBy;
    }

    @Override
    public String toString() {
        return "hitgroup=" + groupBy;
    }

    public void getUrlParam(Map<String, String> param) {
        param.put("group", groupBy);
    }

}
