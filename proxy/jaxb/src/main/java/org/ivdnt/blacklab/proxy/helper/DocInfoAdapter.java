package org.ivdnt.blacklab.proxy.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.namespace.QName;

import org.ivdnt.blacklab.proxy.representation.DocInfo;
import org.ivdnt.blacklab.proxy.representation.MetadataValues;

/**
 * Helps us to (de)serialize autocomplete response in XML.
 */
public class DocInfoAdapter extends XmlAdapter<DocInfoAdapter.DocInfoWrapper, DocInfo> {

    @XmlSeeAlso({MetadataValues.class})
    public static class DocInfoWrapper {
        @XmlAttribute
        public String pid;

        @XmlAnyElement
        public List<JAXBElement<?>> elements;
    }

    @Override
    public DocInfoWrapper marshal(DocInfo m) {
        DocInfoWrapper wrapper = new DocInfoWrapper();
        wrapper.pid = m.pid;
        wrapper.elements = new ArrayList<>();
        for (Map.Entry<String, MetadataValues> e: m.metadata.entrySet()) {
            QName elName = new QName(SerializationUtil.getCleanLabel(e.getKey()));
            JAXBElement<?> jaxbElement = new JAXBElement<>(elName, MetadataValues.class,
                    e.getValue());
            wrapper.elements.add(jaxbElement);
        }
        wrapper.elements.add(new JAXBElement<>(new QName("lengthInTokens"), Integer.class, m.lengthInTokens));
        wrapper.elements.add(new JAXBElement<>(new QName("mayView"), Boolean.class, m.mayView));
        return wrapper;
    }

    @Override
    public DocInfo unmarshal(DocInfoWrapper wrapper) {
        DocInfo docInfo = new DocInfo();
        for (JAXBElement<?> element: wrapper.elements) {
            String name = element.getName().getLocalPart();
            if (name.equals("lengthInTokens")) {
                docInfo.lengthInTokens = Integer.parseInt(element.getValue().toString());
            } else if (name.equals("mayView")) {
                docInfo.mayView = Boolean.parseBoolean(element.getValue().toString());
            } else {
                // Actual metadata value.
                docInfo.metadata.put(name, (MetadataValues) element.getValue());
            }
        }
        return docInfo;
    }
}
