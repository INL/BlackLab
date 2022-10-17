package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.DocProperty;

public class DocGroupSettings {

    final DocProperty groupBy;

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

}
