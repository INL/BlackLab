package org.ivdnt.blacklab.aggregator.representation;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.glassfish.jersey.linking.Binding;
import org.glassfish.jersey.linking.InjectLink;
import org.glassfish.jersey.linking.InjectLink.Style;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.anw.api.resources.ExternalLinks;

/**
 * A list of article properties.
 */
@XmlRootElement(name="external-links")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExternalLinkResults {

    @XmlElement(name="external-link")
    @JsonProperty("external-links")
    private List<ExternalLinkObj> externalLinks;

    // This should be a link to /external-links/RESOURCE?offset=...
    @InjectLink(
        resource = ExternalLinks.class,
        method = "list",
        style = Style.ABSOLUTE_PATH,
        condition = "${instance.hasNext}",
        bindings = {
            @Binding(name="offset", value="${instance.nextOffset}"),
        }
    )
    @JsonInclude(Include.NON_NULL)
    private URI nextUrl;

    // This should be a link to /external-links/RESOURCE?offset=...
    @InjectLink(
        resource = ExternalLinks.class,
        method = "list",
        style = Style.ABSOLUTE_PATH,
        condition = "${instance.offset > 0}",
        bindings = {
            @Binding(name="offset", value="${instance.previousOffset}"),
        }
    )
    @JsonInclude(Include.NON_NULL)
    private URI previousUrl;

    @XmlTransient
    private String resource;

    @XmlTransient
    private int status;

    @XmlTransient
    private int offset;

    @XmlTransient
    private int limit;

    @XmlTransient
    private String dstIds;

    @XmlTransient
    private int lastedit;

    @XmlTransient
    private boolean hasNext;

    public URI getPreviousUrl() {
		return previousUrl;
	}

	public int getStatus() {
		return status;
	}

	public String getDst_ids() {
		return dstIds;
	}

	public int getLastedit() {
		return lastedit;
	}

	public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public boolean getHasNext() {
        return hasNext;
    }

    public String getResource() {
    	return resource;
    }

    public List<ExternalLinkObj> getExternalLinks() {
        return externalLinks;
    }

    public URI getNextUrl() {
        return nextUrl;
    }

    public int getNextOffset() {
        return offset + limit;
    }

    public int getPreviousOffset() {
        return Math.max(0, offset - limit);
    }

    // required for Jersey
    @SuppressWarnings("unused")
    private ExternalLinkResults() {}

    public ExternalLinkResults(List<ExternalLinkObj> externalLinks, String resource, int status, int offset, int limit, boolean hasNext, String strIds, int lastEditDays) {
        super();
        this.externalLinks = externalLinks;
        this.resource = resource;
        this.status = status;
        this.offset = offset;
        this.limit = limit;
        this.hasNext = hasNext;
        this.dstIds = strIds;
        this.lastedit = lastEditDays;
    }
}
