package nl.inl.blacklab.server.lib;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.lib.results.ResultAnnotatedField;
import nl.inl.blacklab.server.lib.results.ResultIndexStatus;
import nl.inl.blacklab.server.lib.results.ResultMetadataField;

public class ResultIndexMetadata {

    private ResultIndexStatus progress;
    private List<ResultAnnotatedField> afs;
    private List<ResultMetadataField> mfs;
    private Map<String, List<String>> metadataFieldGroups;

    public ResultIndexMetadata(ResultIndexStatus progress, List<ResultAnnotatedField> afs,
            List<ResultMetadataField> mfs, Map<String, List<String>> metadataFieldGroups) {
        this.progress = progress;
        this.afs = afs;
        this.mfs = mfs;
        this.metadataFieldGroups = metadataFieldGroups;
    }

    public IndexMetadata getMetadata() {
        return progress.getMetadata();
    }

    public ResultIndexStatus getProgress() {
        return progress;
    }

    public List<ResultAnnotatedField> getAnnotatedFields() {
        return afs;
    }

    public List<ResultMetadataField> getMetadataFields() {
        return mfs;
    }

    public Map<String, List<String>> getMetadataFieldGroups() {
        return metadataFieldGroups;
    }
}
