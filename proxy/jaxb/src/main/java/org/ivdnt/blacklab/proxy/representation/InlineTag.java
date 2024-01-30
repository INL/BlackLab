package org.ivdnt.blacklab.proxy.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.ivdnt.blacklab.proxy.helper.MapAdapter;
import org.ivdnt.blacklab.proxy.helper.SerializationUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
public class InlineTag {

    public int start;

    public int end;

    public String type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @XmlJavaTypeAdapter(MapAdapter.class)
    @JsonSerialize(using = SerializationUtil.StringMapSerializer.class)
    @JsonDeserialize(using = SerializationUtil.StringMapDeserializer.class)
    public Map<String, String> attributes;

    @Override
    public String toString() {
        return "InlineTag{" +
                "type='" + type + '\'' +
                ", attributes='" + attributes + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
