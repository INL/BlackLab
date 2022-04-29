package org.ivdnt.blacklab.aggregator.representation;

import java.util.HashMap;
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

    @Override
    public String toString() {
        return "Word{" +
                "mainAnnotation='" + mainAnnotation + '\'' +
                ", otherAnnotations=" + otherAnnotations +
                '}';
    }

    public Word() {}

    public Word(Map<String, String> w) {
        otherAnnotations = new HashMap<>();
        for (Map.Entry<String, String> e: w.entrySet()) {
            if (e.getKey().equals(MAIN_ANNOTATION_NAME))
                mainAnnotation = e.getValue();
            else
                otherAnnotations.put(new QName(e.getKey()), e.getValue());
        }
    }
}
