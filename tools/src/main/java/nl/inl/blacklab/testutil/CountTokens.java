package nl.inl.blacklab.testutil;

import java.io.File;

import org.apache.logging.log4j.Level;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.DocTask;
import nl.inl.util.LogUtil;

/**
 * Calculate total token count (for older BlackLab indices that don't store this
 * in the metadata file).
 */
public class CountTokens {

    private static final class CountTask implements DocTask {
        private final String tokenLengthField;
        int totalDocs;
        int docsDone = 0;
        public long totalTokens = 0;

        CountTask(IndexReader reader, String tokenLengthField) {
            this.tokenLengthField = tokenLengthField;
            totalDocs = reader.maxDoc() - reader.numDeletedDocs();
        }

        @Override
        public void perform(Doc doc) {
            totalTokens += Long.parseLong(doc.luceneDoc().get(tokenLengthField));
            docsDone++;
            if (docsDone % 100 == 0) {
                int perc = docsDone * 100 / totalDocs;
                System.out.println(docsDone + " docs exported (" + perc + "%)...");
            }
        }
    }

    public static void main(String[] args) throws ErrorOpeningIndex {
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        if (args.length != 1) {
            System.out.println("Usage: CountTokens <indexDir>");
            System.exit(1);
        }

        File indexDir = new File(args[0]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            System.out.println("Directory doesn't exist or is unreadable: " + indexDir);
            System.exit(1);
        }
        if (!BlackLabIndex.isIndex(indexDir)) {
            System.out.println("Not a BlackLab index: " + indexDir);
            System.exit(1);
        }

        CountTokens exportCorpus = new CountTokens(indexDir);
        System.out.println("Calling export()...");
        exportCorpus.count();
    }

    BlackLabIndex index;

    public CountTokens(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        System.out.println("Done.");
    }

    /**
     * Export the whole corpus.
     */
    private void count() {

        System.out.println("Getting IndexReader...");
        final IndexReader reader = index.reader();

        final String tokenLengthField = index.mainAnnotatedField().tokenLengthField();

        System.out.println("Calling forEachDocument()...");
        CountTask task = new CountTask(reader, tokenLengthField);
        index.forEachDocument(task);
        System.out.println("TOTAL TOKENS: " + task.totalTokens);
    }
}
