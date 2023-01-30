package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapterTermFreq;

@XmlRootElement(name="blacklabResponse")
public class TermFreqList {

    @XmlJavaTypeAdapter(MapAdapterTermFreq.class)
    Map<String, Long> termFreq;
}
