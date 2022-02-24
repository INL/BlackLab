package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.DocProperty;

public class DocSortSettings {
    private DocProperty sortBy;

    public DocSortSettings(DocProperty sortBy) {
        super();
        this.sortBy = sortBy;
    }

    public DocProperty sortBy() {
        return sortBy;
    }

    @Override
    public String toString() {
        return "docsort=" + sortBy.serialize();
    }

}
