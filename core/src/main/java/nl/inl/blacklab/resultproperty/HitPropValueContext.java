package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.Hits;

public abstract class HitPropValueContext extends HitPropValue {

    protected String fieldName;

    protected Terms terms;

    protected String propName;

    public HitPropValueContext(Hits hits, String propName) {
        this.fieldName = hits.settings().concordanceField();
        this.propName = propName;
        this.terms = hits.getSearcher().getForwardIndex(AnnotatedFieldNameUtil.propertyField(fieldName, propName)).getTerms();
    }
}
