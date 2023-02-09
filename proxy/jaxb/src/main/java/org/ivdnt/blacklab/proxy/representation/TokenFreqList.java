package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapterTermFreq;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class TokenFreqList {

    @XmlJavaTypeAdapter(MapAdapterTermFreq.class)
    @JsonSerialize(using = SerializationUtil.TermFreqMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.TermFreqMapDeserializer.class)
    public Map<String, Long> tokenFrequencies;
}
