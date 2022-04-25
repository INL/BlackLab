package org.ivdnt.blacklab.aggregator.representation;

import java.util.List;

public class ArticleCreationRequest {
    /** Article lemma */
    private String lemma;
    
    /** (optional) XML template, to which ID, pid and lemma will be added. Overrides default template. */
    private String xmlTemplate = null;
    
    /** (optional) What subsets to place new article in. Defaults to the default subset, if any. */
    private List<String> subsets = null;
    
    public String getLemma() {
        return lemma;
    }

    public String getXmlTemplate() {
        return xmlTemplate;
    }

    public List<String> getSubsets() {
        return subsets;
    }

}
