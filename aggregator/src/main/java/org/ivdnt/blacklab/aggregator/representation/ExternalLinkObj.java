package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A link from a DWS article to an external resource.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
@XmlRootElement(name="external-link")
public class ExternalLinkObj {

    /** article lemma */
    private String articleLemma;

    /** article pid*/
    private int articlePid;

	/** source dictionary pid */
    private int srcPid;

    /** type of pid (article/sense/...) */
    private String srcPidType;

	/** source dictionary pid description */
    private String srcPidDescription;

    /** name of the resource it links to (e.g. a lexicon or another dictionary) */
    private String dstResource;

    /** id in the resource it links to */
    private int dstId;

    /** Is this link okay, or is action needed? */
    private int status;

    public ExternalLinkObj(String articleLemma, int articlePid, int srcPid, String srcPidType, String srcPidDescription, String dstResource, int dstId, int status) {
        super();
        this.articleLemma = articleLemma;
        this.articlePid = articlePid;
        this.srcPid = srcPid;
        this.srcPidType = srcPidType;
        this.srcPidDescription = srcPidDescription;
        this.dstResource = dstResource;
        this.dstId = dstId;
        this.status = status;
    }

    // Required for Jersey
    private ExternalLinkObj() {}

}
