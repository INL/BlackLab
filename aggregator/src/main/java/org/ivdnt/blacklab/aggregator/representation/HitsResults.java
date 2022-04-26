package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class HitsResults {

    private SearchSummary summary;

    private String indexName = "";

    private String displayName = "";

    private String description = "";

    private String status = "available";

    private boolean contentViewable = false;

    private String textDirection = "ltr";

    private String documentFormat = "tei";

    private String timeModified = "";

    private long tokenCount = 0;

    private long documentCount = 0;

    private VersionInfo versionInfo;

    private FieldInfo fieldInfo;

    @XmlElementWrapper(name="annotatedField")
    @XmlElement(name = "annotatedField")
    private List<AnnotatedField> annotatedFields;

    @XmlElementWrapper(name="hits")
    @XmlElement(name = "hit")
    private List<Hit> hits;

    // required for Jersey
    @SuppressWarnings("unused")
    private HitsResults() {}

    public HitsResults(String indexName, FieldInfo fieldInfo,
            List<AnnotatedField> annotatedFields) {
        this.indexName = indexName;
        this.fieldInfo = fieldInfo;
        this.annotatedFields = annotatedFields;
    }
}
