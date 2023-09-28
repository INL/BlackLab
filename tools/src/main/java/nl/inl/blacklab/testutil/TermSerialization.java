package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import java.text.Collator;

import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TermSerialization {

    private static Terms terms;

    public static void main(String[] args) throws IOException {
        String path = args.length >= 1 ? args[0] : ".";
        String word = args.length >= 2 ? args[1] : "in";
        String annotationName = args.length >= 3 ? args[2] : "";

        BlackLabIndex index = BlackLab.open(new File(path));
        AnnotatedField field = index.annotatedField("contents");
        Annotation annotation = annotationName.isEmpty() ? field.mainAnnotation() : field.annotation(annotationName);
        AnnotationForwardIndex fi = index.annotationForwardIndex(annotation);
        terms = fi.terms();

        MutableIntSet s = new IntHashSet();
        int sensitiveIndex = terms.indexOf(word);
        s.add(sensitiveIndex);
        report("terms.indexOf", s);
        s.clear();
        terms.indexOf(s, word, MatchSensitivity.SENSITIVE);
        report("terms.indexOf sensitive", s);
        s.clear();
        terms.indexOf(s, word, MatchSensitivity.INSENSITIVE);
        report("terms.indexOf insensitive", s);

        Collators collators = fi.collators();
        Collator collator = collators.get(MatchSensitivity.SENSITIVE);

        System.out.println("Checking these insensitive terms...");
        System.out.flush();
        IntIterator it = s.intIterator();
        while (it.hasNext()) {
            int termId = it.next();
            String term = terms.get(termId);
            int termId2 = terms.indexOf(term);
            String term2 = terms.get(termId2);
            if (collator.compare(term, term2) != 0) {
                System.out.println("term != term2: '" + term + "' != '" + term2 + "'");
            }
        }

        System.out.println("Checking all terms...");
        System.out.flush();
        int n = 0;
        for (int termId = 0; termId < terms.numberOfTerms(); termId++) {
            String term = terms.get(termId);
            int termId2 = terms.indexOf(term);
            String term2 = terms.get(termId2);
            if (collator.compare(term, term2) != 0) {
                System.out.println("term != term2: '" + term + "' != '" + term2 + "'");
                System.out.flush();
            }
            n++;
            if (n % 100000 == 0) {
                System.out.println(n + " terms checked...");
                System.out.flush();
            }
        }
    }

    private static void report(String prompt, MutableIntSet s1) {
        StringBuilder values = new StringBuilder();
        for (int i : s1.toArray()) {
            values.append(i).append(" (").append(terms.get(i)).append("); ");
        }
        System.out.println(prompt + ": " + values);
        System.out.flush();
    }

}
