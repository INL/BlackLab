package nl.inl.blacklab.performance;

import java.io.File;
import java.util.List;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.LogUtil;

/**
 * Executes a batch of fetch operations on a forward index.
 */
public class ExportForwardIndex {

    public static void main(String[] args) throws ErrorOpeningIndex {

        LogUtil.setupBasicLoggingConfig(); // suppress log4j warning

        int fileArgNumber = 0;
        File indexDir = null;
        String annotatedFieldName = "contents";
        for (String s: args) {
            String arg = s.trim();
            if (arg.charAt(0) == '-') {
                System.err.println("Illegal option: " + arg);
                usage();
                return;
            }
            switch (fileArgNumber) {
            case 0:
                indexDir = new File(arg);
                if (!indexDir.exists() || !indexDir.isDirectory()) {
                    System.err.println("Index directory not found: " + arg);
                    usage();
                    return;
                }
                break;
            case 1:
                annotatedFieldName = arg;
                break;
            default:
                System.err.println("Too many file arguments (supply index dir)");
                usage();
                return;
            }
            fileArgNumber++;
        }
        if (fileArgNumber < 1) {
            System.err.println("Too few file arguments (supply index dir)");
            usage();
            return;
        }

        // Annotations to skip
        List<String> skipAnnots = List.of("pos", "punct", "starttag");

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            AnnotatedField annotatedField = index.annotatedField(annotatedFieldName);
            ForwardIndex forwardIndex = index.forwardIndex(annotatedField);

            // Export term indexes + term strings
            System.out.println("TERMS");
            for (Annotation annotation: annotatedField.annotations()) {
                if (skipAnnots.contains(annotation.name()) || !annotation.hasForwardIndex())
                    continue;
                System.out.println("  " + annotation.name());
                Terms terms = forwardIndex.terms(annotation);
                for (int i = 0; i < terms.numberOfTerms(); i++) {
                    System.out.println(String.format("    %03d %s", i, terms.get(i)));
                }
            }

            // Export tokens in each doc
            System.out.println("\nDOCS");
            index.forEachDocument((__, docId) -> {
                Document luceneDoc = index.luceneDoc(docId);
                String inputFile = luceneDoc.get("fromInputFile");
                System.out.println(docId + " (" + inputFile + ")");
                for (Annotation annotation: annotatedField.annotations()) {
                    if (skipAnnots.contains(annotation.name()) || !annotation.hasForwardIndex())
                        continue;
                    System.out.println("  " + annotation.name());
                    AnnotationForwardIndex afi = forwardIndex.get(annotation);
                    int[] doc = afi.getDocument(docId);
                    for (int tokenId: doc) {
                        String token = afi.terms().get(tokenId);
                        System.out.println("    " + token);
                    }
                }
            });
        }
    }

    private static void usage() {
        System.out.println("Supply an index directory and, optionally, an annotated field name");
    }
}
