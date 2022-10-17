package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

public class ResultAnnotatedField {
    private String indexName;

    private AnnotatedField fieldDesc;

    private Map<String, ResultAnnotationInfo> annotInfos;

    ResultAnnotatedField(String indexName, AnnotatedField fieldDesc,
            Map<String, ResultAnnotationInfo> annotInfos) {
        this.indexName = indexName;
        this.fieldDesc = fieldDesc;
        this.annotInfos = annotInfos;
    }

    public String getIndexName() {
        return indexName;
    }

    public AnnotatedField getFieldDesc() {
        return fieldDesc;
    }

    public Map<String, ResultAnnotationInfo> getAnnotInfos() {
        return annotInfos;
    }
}
