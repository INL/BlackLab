package org.ivdnt.blacklab.aggregator.representation;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;

public class Word {

    public static final String MAIN_ANNOTATION_NAME = "word";

    @XmlValue
    public String mainAnnotation = "theword";

    @XmlAnyAttribute
    public Map<QName, String> otherAnnotations;

    public Word() {}

    public Word(Map<String, String> w) {
        otherAnnotations = new LinkedHashMap<>();
        for (Map.Entry<String, String> e: w.entrySet()) {
            if (e.getKey().equals(MAIN_ANNOTATION_NAME))
                mainAnnotation = e.getValue();
            else
                otherAnnotations.put(new QName(e.getKey()), e.getValue());
        }
    }

    public int compareTo(Word word, String annotation, boolean sensitive) {
        if (annotation.equals(MAIN_ANNOTATION_NAME))
            return mainAnnotation.compareTo(word.mainAnnotation);
        String a = otherAnnotations.getOrDefault(annotation, "");
        String b = word.otherAnnotations.getOrDefault(annotation, "");
        return sensitive ? a.compareTo(b) : a.compareToIgnoreCase(b);
    }

    @Override
    public String toString() {
        return "Word{" +
                "mainAnnotation='" + mainAnnotation + '\'' +
                ", otherAnnotations=" + otherAnnotations +
                '}';
    }
}
