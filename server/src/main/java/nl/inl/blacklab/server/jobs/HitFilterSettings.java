package nl.inl.blacklab.server.jobs;

public class HitFilterSettings {

    private String crit;

    private String value;

    public String getProperty() {
        return crit;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "hitfiltercrit=" + crit + "&hitfilterval=" + value;
    }

    public HitFilterSettings(String crit, String value) {
        this.crit = crit;
        this.value = value;
    }

}
