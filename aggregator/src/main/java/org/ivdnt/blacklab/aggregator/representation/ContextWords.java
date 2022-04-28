package org.ivdnt.blacklab.aggregator.representation;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
public class ContextWords {

    /** Use this to serialize this class to JSON */
    private static class Serializer extends JsonSerializer<List<Word>> {
        @Override
        public void serialize(List<Word> value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            Set<QName> annotations = new HashSet<>();
            annotations.add(new QName("word"));
            for (Word w: value) {
                for (QName a: w.otherAnnotations.keySet()) {
                    annotations.add(a);
                }
            }

            jgen.writeStartObject();
            for (QName annotation: annotations) {
                jgen.writeArrayFieldStart(annotation.getLocalPart());
                for (Word w: value) {
                    String x;
                    if (annotation.getLocalPart().equals("word"))
                        x = w.mainAnnotation;
                    else
                        x = w.otherAnnotations.getOrDefault(annotation, "");
                    jgen.writeString(x);
                }
                jgen.writeEndArray();
            }
            jgen.writeEndObject();
        }
    }

    @XmlElement(name="w")
    @JsonSerialize(using = ContextWords.Serializer.class)
    List<Word> words = List.of(new Word(), new Word());

    public ContextWords() {}
}
