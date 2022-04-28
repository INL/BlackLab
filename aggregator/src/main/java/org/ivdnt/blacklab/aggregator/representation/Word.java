package org.ivdnt.blacklab.aggregator.representation;

import java.util.Map;

import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.namespace.QName;

//@XmlAccessorType(XmlAccessType.FIELD)
public class Word {

    @XmlValue
    String mainAnnotation = "theword";

//    @XmlAttribute
//    String lemma = "testlemma";

    @XmlAnyAttribute
    Map<QName, String> otherAnnotations = Map.of(new QName("lemma"), "thelemma", new QName("pos"), "thepos");

}
