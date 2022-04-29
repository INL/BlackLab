package org.ivdnt.blacklab.aggregator.representation;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.aggregator.helper.JacksonUtil;
import org.ivdnt.blacklab.aggregator.helper.MapAdapter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchSummary {

    public SearchParam searchParam = new SearchParam();

    public long searchTime;

    public long countTime;

    public long windowFirstResult;

    public long requestedWindowSize;

    public long actualWindowSize;

    public boolean windowHasPrevious;

    public boolean windowHasNext;

    public boolean stillCounting;

    public long numberOfHits;

    public long numberOfHitsRetrieved;

    public boolean stoppedCountingHits;

    public boolean stoppedRetrievingHits;

    public long numberOfDocs;

    public long numberOfDocsRetrieved;

    public FieldInfo docFields;

    // (just include an empty element here)
    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = JacksonUtil.StringMapSerializer.class)
    @JsonDeserialize(using = JacksonUtil.StringMapDeserializer.class)
    public Map<String, String> metadataFieldDisplayNames = new HashMap<>();

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
