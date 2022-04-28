package org.ivdnt.blacklab.aggregator.helper;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.QName;

import org.ivdnt.blacklab.aggregator.representation.MetadataEntry;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


// FIXME does not work...
public class MapAdapterMetaEntry extends XmlAdapter<MapWrapper, MetadataEntry> {

    @Override
    public MapWrapper marshal(MetadataEntry m) throws Exception {
        MapWrapper wrapper = new MapWrapper();
        wrapper.elements = new ArrayList();
        wrapper.elements.add(new JAXBElement<>(new QName(getCleanLabel(m.getKey())),
                List.class, m.getValue()));
        return wrapper;
    }

    @Override
    public MetadataEntry unmarshal(MapWrapper v) throws Exception {
        MetadataEntry returnval = new MetadataEntry();
        Object o = v.elements.get(0);
        Element e = (Element) o;
        returnval.setKey(e.getTagName());
        List<String> values = new ArrayList<>();
        NodeList nl = e.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            values.add(nl.item(i).getTextContent());
        }
        returnval.setValue(values);
        return returnval;
    }

    // Return a XML-safe attribute.  Might want to add camel case support
    private String getCleanLabel(String attributeLabel) {
        attributeLabel = attributeLabel.replaceAll("[()]", "").replaceAll("[^\\w\\s]", "_").replaceAll(" ", "_");
        return attributeLabel;
    }
}
