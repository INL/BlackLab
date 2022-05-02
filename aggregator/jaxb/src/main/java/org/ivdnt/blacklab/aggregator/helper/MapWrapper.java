package org.ivdnt.blacklab.aggregator.helper;

import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;

//@XmlSeeAlso(MetadataValues.class)
public class MapWrapper {
    @XmlAnyElement
    public List elements;
}
