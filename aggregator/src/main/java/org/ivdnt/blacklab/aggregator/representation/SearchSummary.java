package org.ivdnt.blacklab.aggregator.representation;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchSummary {

    private SearchParam searchParam;

    private long searchTime;

    private long countTime;

    private long windowFirstResult;

    private long requestedWindowSize;

    private long actualWindowSize;

    private boolean windowHasPrevious;

    private boolean windowHasNext;

    private boolean stillCounting;

    private long numberOfHits;

    private long numberOfHitsRetrieved;

    private boolean stoppedCountingHits;

    private boolean stoppedRetrievingHits;

    private long numberOfDocs;

    private long numberOfDocsRetrieved;

    private FieldInfo docFields;

    // (just include an empty element here)
    @XmlElementWrapper(name="metadataFieldDisplayNames")
    @XmlElement(name = "item")
    private final List<String> metadataFieldDisplayNames = Collections.emptyList();

}
