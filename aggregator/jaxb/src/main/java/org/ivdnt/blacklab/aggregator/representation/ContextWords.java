package org.ivdnt.blacklab.aggregator.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonSerialize(using = ContextWords.Serializer.class)
@JsonDeserialize(using = ContextWords.Deserializer.class)
public class ContextWords {

    /** Use this to serialize this class to JSON */
    static class Serializer extends JsonSerializer<ContextWords> {
        @Override
        public void serialize(ContextWords value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            // See what annotation there are (because not every word will have a value for every annotation)
            Set<QName> annotations = new LinkedHashSet<>();
            annotations.add(new QName(Word.MAIN_ANNOTATION_NAME));
            for (Word w: value.words) {
                for (QName a: w.otherAnnotations.keySet()) {
                    annotations.add(a);
                }
            }

            jgen.writeStartObject();
            for (QName annotation: annotations) {
                jgen.writeArrayFieldStart(annotation.getLocalPart());
                for (Word w: value.words) {
                    String x;
                    if (annotation.getLocalPart().equals(Word.MAIN_ANNOTATION_NAME))
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

    static class Deserializer extends JsonDeserializer<ContextWords> {

        @Override
        public ContextWords deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException, JacksonException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);

            Map<String, List<String>> wordsPerAnnot = new LinkedHashMap<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;
                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                String annotationName = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_ARRAY)
                    throw new RuntimeException("Expected START_ARRAY, found " + token);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY)
                        break;
                    if (token != JsonToken.VALUE_STRING)
                        throw new RuntimeException("Expected END_ARRAY or VALUE_STRING, found " + token);
                    String value = parser.getValueAsString();
                    wordsPerAnnot.computeIfAbsent(annotationName, __ -> new ArrayList<>()).add(value);
                }
            }

            // Convert to per-Word (JSON is structured per-annotation)
            List<Word> result = new ArrayList<>();
            int n = wordsPerAnnot.values().iterator().next().size();
            for (int i = 0; i < n; i++) {
                Word word = new Word();
                Map<QName, String> m = word.otherAnnotations = new LinkedHashMap<>();
                for (String name: wordsPerAnnot.keySet()) {
                    String w = wordsPerAnnot.get(name).get(i);
                    if (name.equals(Word.MAIN_ANNOTATION_NAME))
                        word.mainAnnotation = w;
                    else
                        m.put(new QName(name), w);
                }
                result.add(word);
            }
            return new ContextWords(result);
        }
    }

    @XmlElement(name="w")
    public List<Word> words;

    private ContextWords() {}

    public ContextWords(List<Word> w) {
        this.words = w;
    }

    @Override
    public String toString() {
        return "ContextWords{" +
                "words=" + words +
                '}';
    }
}
