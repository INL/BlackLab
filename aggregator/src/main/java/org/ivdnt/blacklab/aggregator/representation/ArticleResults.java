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

import nl.inl.anw.api.resources.Articles;

/**
 * A list of article properties.
 */
@XmlRootElement(name="articles")
@XmlAccessorType(XmlAccessType.FIELD)
public class ArticleResults {

    @XmlElement(name="article")
    @JsonProperty("articles")
    private List<Article> articles;

    // This should be a link to /articles?offset=...
    @InjectLink(
        resource = Articles.class,
        method = "find",
        style = Style.ABSOLUTE_PATH,
        condition = "${instance.hasNext}",
        bindings = {
            @Binding(name="offset", value="${instance.nextOffset}"),
        }
    )
    @JsonInclude(Include.NON_NULL)
    private URI nextUrl;

    // This should be a link to /articles?offset=...
    @InjectLink(
        resource = Articles.class,
        method = "find",
        style = Style.ABSOLUTE_PATH,
        condition = "${instance.offset > 0}",
        bindings = {
            @Binding(name="offset", value="${instance.previousOffset}"),
        }
    )
    @JsonInclude(Include.NON_NULL)
    private URI previousUrl;

    @XmlTransient
    private String lemma;

    @XmlTransient
    private int id;

    @XmlTransient
    private int pid;

    @XmlTransient
    private int lastedit;

    @XmlTransient
    private String editor;

    @XmlTransient
    private String phase;

    @XmlTransient
    private String subset;

    @XmlTransient
    private String xmllike;

    @XmlTransient
    private String xpath;

    @XmlTransient
    private int offset;

    @XmlTransient
    private int limit;

    @XmlTransient
    private boolean hasNext;

    @XmlTransient
    private String sort;

    public int getOffset() {
        return offset;
    }

    public int getLimit() {
        return limit;
    }

    public boolean getHasNext() {
        return hasNext;
    }

    public List<Article> getArticles() {
        return articles;
    }

    public URI getNextUrl() {
        return nextUrl;
    }

    public URI getPreviousUrl() {
        return previousUrl;
    }

    public String getLemma() {
        return lemma;
    }

    public int getId() {
        return id;
    }

    public int getPid() {
        return pid;
    }

    public int getLastedit() {
        return lastedit;
    }

    public String getEditor() {
        return editor;
    }

    public String getPhase() {
        return phase;
    }

    public String getSubset() {
        return subset;
    }

    public String getXmllike() {
        return xmllike;
    }

    public String getXpath() {
        return xpath;
    }

    public String getSort() {
        return sort;
    }

    public int getNextOffset() {
        return offset + limit;
    }

    public int getPreviousOffset() {
        return Math.max(0, offset - limit);
    }

    // required for Jersey
    @SuppressWarnings("unused")
    private ArticleResults() {}

    public ArticleResults(List<Article> articles, String lemma, int id, int pid, int lastEdit, String editor,
            String phases, String subsets, String xmlLike, String xpath, int offset, int limit, boolean hasNext, String sort) {
        super();
        this.articles = articles;
        this.lemma = lemma;
        this.id = id;
        this.pid = pid;
        this.lastedit = lastEdit;
        this.editor = editor;
        this.phase = phases;
        this.subset = subsets;
        this.xmllike = xmlLike;
        this.xpath = xpath;
        this.offset = offset;
        this.limit = limit;
        this.hasNext = hasNext;
        this.sort = sort;
    }
}
