package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public abstract class PropertyValueContext extends PropertyValue {

    protected final Terms terms;

    protected final Annotation annotation;

    PropertyValueContext(BlackLabIndex index, Annotation annotation) {
        this.annotation = annotation;
        this.terms = index == null ? null : index.annotationForwardIndex(annotation).terms();
    }

    PropertyValueContext(Terms terms, Annotation annotation) {
        this.annotation = annotation;
        this.terms = terms;
    }

    public static int deserializeToken(Terms terms, String term) {
        int termId;
        if (term.equals("~"))
            termId = Terms.NO_TERM; // no token, effectively a "null" value
        else {
            if (term.startsWith("~~")) {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                term = term.substring(1);
            }
            termId = terms.indexOf(term);
        }
        return termId;
    }

    public static String serializeTerm(Terms terms, int valueTokenId) {
        String token;
        if (valueTokenId < 0)
            token = "~"; // no token, effectively a "null" value
        else {
            token = terms.get(valueTokenId);
            if (token.length() > 0 && token.charAt(0) == '~') {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                token = "~" + token;
            }
        }
        return token;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PropertyValueContext other = (PropertyValueContext) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        return true;
    }

}
