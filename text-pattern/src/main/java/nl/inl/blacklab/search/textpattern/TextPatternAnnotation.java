package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain
 * annotation on an annotated field.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternAnnotation("lemma", new TextPatternWildcard("bla*"));
 * </code>
 */
public class TextPatternAnnotation extends TextPattern {
    private final TextPattern input;

    private final String annotationName;

    public TextPatternAnnotation(String annotationName, TextPattern input) {
        this.annotationName = annotationName == null ? "" : annotationName;
        this.input = input;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        String[] parts = annotationName.split("/", -1);
        if (parts.length > 2)
            throw new InvalidQuery("Invalid query: annotation name '" + annotationName + "' contains more than one slash");
        Annotation annotation;
        if (context.index().metadata().subannotationsStoredWithParent()) {
            // Old-style index, where subannotations are stored in their parent's Lucene field
            annotation = context.field().annotation(parts[0]);
            if (annotation == null)
                throw new InvalidQuery("Invalid query: annotation '" + annotationName + "' doesn't exist");
            if (parts.length > 1)
                annotation = annotation.subannotation(parts[1]);
        } else {
            // New-style index, where subannotations have their own Lucene field
            String name = parts[0];
            if (parts.length > 1)
                name += AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + parts[1];
            annotation = context.field().annotation(name);
            if (annotation == null)
                throw new InvalidQuery("Invalid query: annotation '" + name + "' doesn't exist");
        }
        return input.translate(context.withAnnotation(annotation));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnnotation) {
            TextPatternAnnotation tp = ((TextPatternAnnotation) obj);
            return input.equals(tp.input) && annotationName.equals(tp.annotationName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return input.hashCode() + annotationName.hashCode();
    }

    @Override
    public String toString() {
        return "PROP(" + annotationName + ", " + input.toString() + ")";
    }

}
