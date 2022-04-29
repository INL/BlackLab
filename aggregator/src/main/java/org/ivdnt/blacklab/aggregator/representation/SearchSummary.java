package org.ivdnt.blacklab.aggregator.representation;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchSummary {

    private SearchParam searchParam = new SearchParam();

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
    @JsonProperty("metadataFieldDisplayNames")
    private final List<String> metadataFieldDisplayNames = Collections.emptyList();

    public SearchSummary() {

    }

    public SearchSummary(SearchParam searchParam) {
        this.searchParam = searchParam;
    }

    @Override
    public String toString() {
        return "SearchSummary{" +
                "searchParam=" + searchParam +
                ", searchTime=" + searchTime +
                ", countTime=" + countTime +
                ", windowFirstResult=" + windowFirstResult +
                ", requestedWindowSize=" + requestedWindowSize +
                ", actualWindowSize=" + actualWindowSize +
                ", windowHasPrevious=" + windowHasPrevious +
                ", windowHasNext=" + windowHasNext +
                ", stillCounting=" + stillCounting +
                ", numberOfHits=" + numberOfHits +
                ", numberOfHitsRetrieved=" + numberOfHitsRetrieved +
                ", stoppedCountingHits=" + stoppedCountingHits +
                ", stoppedRetrievingHits=" + stoppedRetrievingHits +
                ", numberOfDocs=" + numberOfDocs +
                ", numberOfDocsRetrieved=" + numberOfDocsRetrieved +
                ", docFields=" + docFields +
                ", metadataFieldDisplayNames=" + metadataFieldDisplayNames +
                '}';
    }
}
