package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.apache.logging.log4j.Level;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.DocTask;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;

/** Export the original corpus from a BlackLab index. */
public class ExportCorpus {

    public static void main(String[] args) throws ErrorOpeningIndex {
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        if (args.length != 2) {
            System.out.println("Usage: ExportCorpus <indexDir> <exportDir>");
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

        File exportDir = new File(args[1]);
        if (!exportDir.isDirectory() || !exportDir.canWrite()) {
            System.out.println("Directory doesn't exist or cannot write to it: " + exportDir);
            System.exit(1);
        }

        ExportCorpus exportCorpus = new ExportCorpus(indexDir);
        System.out.println("Calling export()...");
        exportCorpus.export(exportDir);
    }

    BlackLabIndex index;

    public ExportCorpus(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        System.out.println("Done.");
    }

    /**
     * Export the whole corpus.
     * 
     * @param exportDir directory to export to
     */
    private void export(final File exportDir) {

        System.out.println("Getting IndexReader...");
        final IndexReader reader = index.reader();

        System.out.println("Calling forEachDocument()...");
        index.forEachDocument(new DocTask() {

            int totalDocs = reader.maxDoc() - reader.numDeletedDocs();

            int docsDone = 0;

            @Override
            public void perform(Doc doc) {
                String fromInputFile = doc.luceneDoc().get("fromInputFile");
                System.out.println("Getting content for " + fromInputFile + "...");
                try {
                    String xml = doc.contents();
                    File file = new File(exportDir, fromInputFile);
                    System.out.println("Got content, exporting to " + file + "...");
                    if (file.exists()) {
                        // Add a number so we don't have to overwrite the previous file.
                        System.out.println(
                                "WARNING: File " + file + " exists, using different name to avoid overwriting...");
                        file = FileUtil.addNumberToExistingFileName(file);
                    }
                    System.out.println(file);
                    File dir = file.getAbsoluteFile().getParentFile();
                    if (!dir.exists()) {
                        if (!dir.mkdirs()) // create any subdirectories required
                            throw new BlackLabRuntimeException("Could not create dir(s): " + dir);
                    }
                    try (PrintWriter pw = FileUtil.openForWriting(file)) {
                        pw.write(xml);
                    } catch (FileNotFoundException e) {
                        throw BlackLabRuntimeException.wrap(e);
                    }
                } catch (RuntimeException e) {
                    // HACK: a bug in an older content store implementation can cause this
                    //   when exporting an older index. Report and continue.
                    System.out.flush();
                    e.printStackTrace(System.err);
                    System.err.println("### Error exporting " + fromInputFile + ", skipping ###");
                    System.err.flush();
                }
                docsDone++;
                if (docsDone % 100 == 0) {
                    int perc = docsDone * 100 / totalDocs;
                    System.out.println(docsDone + " docs exported (" + perc + "%)...");
                }
            }
        });
    }
}
