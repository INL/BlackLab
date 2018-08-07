package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.LuceneDocTask;
import nl.inl.util.LogUtil;

/**
 * Calculate total token count (for older BlackLab indices that don't store this
 * in the metadata file).
 */
public class CountTokens {

    private static final class CountTask implements LuceneDocTask {
        private final String tokenLengthField;
        int totalDocs;
        int docsDone = 0;
        public long totalTokens = 0;

        CountTask(IndexReader reader, String tokenLengthField) {
            this.tokenLengthField = tokenLengthField;
            totalDocs = reader.maxDoc() - reader.numDeletedDocs();
        }

        @Override
        public void perform(Document doc) {
            totalTokens += Long.parseLong(doc.get(tokenLengthField));
            docsDone++;
            if (docsDone % 100 == 0) {
                int perc = docsDone * 100 / totalDocs;
                System.out.println(docsDone + " docs exported (" + perc + "%)...");
            }
        }
    }

    public static void main(String[] args) throws IOException {
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

    BlackLabIndex searcher;

    public CountTokens(File indexDir) throws IOException {
        System.out.println("Open index " + indexDir + "...");
        searcher = BlackLabIndex.open(indexDir);
        System.out.println("Done.");
    }

    /**
     * Export the whole corpus.
     */
    private void count() {

        System.out.println("Getting IndexReader...");
        final IndexReader reader = searcher.reader();

        final String tokenLengthField = searcher.mainAnnotatedField().tokenLengthField();

        System.out.println("Calling forEachDocument()...");
        CountTask task = new CountTask(reader, tokenLengthField);
        searcher.forEachDocument(task);
        System.out.println("TOTAL TOKENS: " + task.totalTokens);
    }
}
