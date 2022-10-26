package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public class ResultAnnotationInfo {

    private Annotation annotation;

    private boolean showValues;

    private Set<String> terms = Collections.emptySet();

    private boolean valueListComplete = true;

    ResultAnnotationInfo(BlackLabIndex index, Annotation annotation, Collection<String> listValuesFor) {
        this.annotation = annotation;
        showValues = annotationMatches(annotation.name(), listValuesFor);
        if (showValues && !index.isEmpty()) {
            boolean[] valueListCompleteArray = {
                    true }; // array because we have to access them from the closures
            terms = WebserviceOperations.getAnnotationValues(index, annotation, valueListCompleteArray);
            valueListComplete = valueListCompleteArray[0];
        }
    }

    public static boolean annotationMatches(String annotationPattern, Collection<String> annotations) {
        //return showValuesFor.contains(name);
        for (String expr: annotations) {
            if (annotationPattern.matches("^" + expr + "$")) {
                return true;
            }
        }
        return false;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public boolean isShowValues() {
        return showValues;
    }

    public Set<String> getTerms() {
        return terms;
    }

    public boolean isValueListComplete() {
        return valueListComplete;
    }
}
