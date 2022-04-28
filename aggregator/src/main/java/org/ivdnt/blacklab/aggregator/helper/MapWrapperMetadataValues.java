package org.ivdnt.blacklab.aggregator.helper;

import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.ivdnt.blacklab.aggregator.representation.MetadataValues;

@XmlSeeAlso(MetadataValues.class)
//@JsonSerialize(using = MapWrapperMetadataValues.Serializer.class)
class MapWrapperMetadataValues {

    /** Use this to serialize this class to JSON
    public static class Serializer extends JsonSerializer<MapWrapperMetadataValues> {
        @Override
        public void serialize(MapWrapperMetadataValues value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            jgen.writeStartObject();
            for (JAXBElement<MetadataValues> el: value.elements) {
                String name = el.getName().getLocalPart();
                MetadataValues metadataValues = el.getValue();
                jgen.writeArrayFieldStart(name);
                for (String v: metadataValues.getValues()) {
                    jgen.writeString(v);
                }
                jgen.writeEndArray();
            }
            jgen.writeEndObject();
        }
    } */

    @XmlAnyElement
    List<JAXBElement<MetadataValues>> elements;
}
