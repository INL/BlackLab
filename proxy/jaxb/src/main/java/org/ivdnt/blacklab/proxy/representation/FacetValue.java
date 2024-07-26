package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class FacetValue implements Cloneable {

    public String value;

    public long size;

    public FacetValue() {}

    @Override
    protected FacetValue clone() throws CloneNotSupportedException {
        return (FacetValue)super.clone();
    }

    @Override
    public String toString() {
        return "FacetValue{" +
                "value='" + value + '\'' +
                ", size=" + size +
                '}';
    }
}
