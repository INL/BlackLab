package org.ivdnt.blacklab.aggregator.helper;

import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.ivdnt.blacklab.aggregator.representation.MetadataValues;

@XmlSeeAlso(MetadataValues.class)
class MapWrapperMetadataValues {

    @XmlAnyElement
    List<JAXBElement<MetadataValues>> elements;
}
