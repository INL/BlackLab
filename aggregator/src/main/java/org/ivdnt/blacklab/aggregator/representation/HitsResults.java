package org.ivdnt.blacklab.aggregator.representation;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class HitsResults {

    private SearchSummary summary = new SearchSummary();

    @XmlElementWrapper(name="hits")
    @XmlElement(name = "hit")
    private List<Hit> hits = Collections.emptyList();

    @XmlElementWrapper(name="docInfos")
    @XmlElement(name = "docInfo")
    private List<DocInfo> docInfos = Collections.emptyList();

    // required for Jersey
    public HitsResults() {}

    public HitsResults(SearchSummary summary, List<Hit> hits,
            List<DocInfo> docInfos) {
        this.summary = summary;
        this.hits = hits;
        this.docInfos = docInfos;
    }
}
