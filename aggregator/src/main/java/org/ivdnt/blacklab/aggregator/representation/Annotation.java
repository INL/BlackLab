package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class Annotation {

    @XmlAttribute
    private String name = "word";

    private String displayName = "Word";

    private String description = "A word in the document";

    private String uiType = "";

    private boolean hasForwardIndex = true;

    private String sensitivity = "ONLY_INSENSITIVE";

    private String offsetsAlternative = "i";

    private boolean isInternal = false;
}
