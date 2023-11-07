package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonInclude;

//@XmlRootElement(name="blacklabResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class ParsePatternResponse {

    // NOTE: JSON only!

//    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
//    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
//    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> params;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    SummaryTextPattern parsed;
}
