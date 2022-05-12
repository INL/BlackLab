package org.ivdnt.blacklab.aggregator.representation;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.aggregator.helper.SerializationUtil;
import org.ivdnt.blacklab.aggregator.helper.MapAdapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
public class SearchSummary implements Cloneable {

    public SearchParam searchParam = new SearchParam();

    public long searchTime;

    @JsonInclude(Include.NON_NULL)
    public Long countTime;

    @JsonInclude(Include.NON_NULL)
    public Long numberOfGroups;

    @JsonInclude(Include.NON_NULL)
    public Long largestGroupSize;

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

    @JsonInclude(Include.NON_NULL)
    public Map<String, Long> subcorpusSize;

    @JsonInclude(Include.NON_NULL)
    public FieldInfo docFields;

    @JsonInclude(Include.NON_NULL)
    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> metadataFieldDisplayNames = new LinkedHashMap<>();

    public SearchSummary() {

    }

    public SearchSummary(SearchParam searchParam) {
        this.searchParam = searchParam;
    }

    @Override
    public SearchSummary clone() throws CloneNotSupportedException {
        return (SearchSummary)super.clone();
    }

    @Override
    public String toString() {
        return "SearchSummary{" +
                "searchParam=" + searchParam +
                ", searchTime=" + searchTime +
                ", countTime=" + countTime +
                ", numberOfGroups=" + numberOfGroups +
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
                ", subcorpusSize=" + subcorpusSize +
                ", docFields=" + docFields +
                ", metadataFieldDisplayNames=" + metadataFieldDisplayNames +
                '}';
    }
}
