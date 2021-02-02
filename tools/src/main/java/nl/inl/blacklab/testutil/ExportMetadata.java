package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Doc;
import nl.inl.blacklab.search.DocTask;
import nl.inl.util.LogUtil;

/** Export the metadata of all documents from a BlackLab index. */
public class ExportMetadata {

    private static String escapeTabs(String str) {
        return str.replaceAll("\t", "\\t");
    }

    public static void main(String[] args) throws ErrorOpeningIndex, FileNotFoundException {
        try {
            LogUtil.setupBasicLoggingConfig(Level.DEBUG);

            if (args.length != 2) {
                System.out.println("Usage: ExportMetadata <indexDir> <exportFile>");
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

            File exportFile = new File(args[1]);

            ExportMetadata exportMetadata = new ExportMetadata(indexDir);
            System.out.println("Collecting metadata...");
            exportMetadata.collect();
            System.out.println("Exporting metadata...");
            exportMetadata.exportCsv(exportFile);
            System.out.println("Done exporting metadata.");
            System.out.flush();
        } finally {
            System.out.println("Done closing index.");
            System.out.flush();
        }
        System.out.flush();
    }

    Set<String> fieldNames = new HashSet<>();

    BlackLabIndex index;

    List<Map<String, String>> values = new ArrayList<>();

    public ExportMetadata(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        System.out.println("Done.");
    }

    /**
     * Export the whole corpus.
     *
     * @param exportDir directory to export to
     */
    private void collect() {

        System.out.println("Getting IndexReader...");
        final IndexReader reader = index.reader();

        System.out.println("Calling forEachDocument()...");
        index.forEachDocument(new DocTask() {

            int docsDone = 0;

            int totalDocs = reader.maxDoc() - reader.numDeletedDocs();

            @Override
            public void perform(Doc doc) {
                Document luceneDoc = doc.luceneDoc();
                Map<String, String> metadata = new HashMap<>();
                for (IndexableField f: luceneDoc.getFields()) {
                    // If this is a regular metadata field, not a control field
                    if (!f.name().contains("#")) {
                        fieldNames.add(f.name());
                        if (f.stringValue() != null)
                            metadata.put(f.name(), f.stringValue());
                        else if (f.numericValue() != null)
                            metadata.put(f.name(), f.numericValue().toString());
                    }
                }
                values.add(metadata);
                docsDone++;
                if (docsDone % 100 == 0) {
                    int perc = docsDone * 100 / totalDocs;
                    System.out.println(docsDone + " docs exported (" + perc + "%)...");
                }
            }
        });
    }

    private void exportCsv(File exportFile) throws FileNotFoundException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8))) {
            List<String> listFieldNames = new ArrayList<>(fieldNames);
            Collections.sort(listFieldNames);
            for (String fieldName: listFieldNames) {
                pw.append(fieldName).append("\t");
            }
            pw.println();
            for (Map<String, String> documentMetadata: values) {
                for (String fieldName: listFieldNames) {
                    pw.append(escapeTabs(documentMetadata.getOrDefault(fieldName, ""))).append("\t");
                }
                pw.println();
            }
            System.out.println("Close export file...");
        }
    }
}
