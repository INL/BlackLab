package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.Searcher.LuceneDocTask;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;

/** Export the original corpus from a BlackLab index. */
public class ExportCorpus {

	public static void main(String[] args) throws IOException {
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
		if (!Searcher.isIndex(indexDir)) {
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

	Searcher searcher;

	public ExportCorpus(File indexDir) throws IOException {
		System.out.println("Open index " + indexDir + "...");
		searcher = Searcher.open(indexDir);
		System.out.println("Done.");
	}

	/** Export the whole corpus.
	 * @param exportDir directory to export to
	 */
	private void export(final File exportDir) {

		System.out.println("Getting IndexReader...");
		final IndexReader reader = searcher.getIndexReader();

		System.out.println("Calling forEachDocument()...");
		searcher.forEachDocument(new LuceneDocTask() {

			int totalDocs = reader.maxDoc() - reader.numDeletedDocs();

			int docsDone = 0;

			@Override
			public void perform(Document doc) {
				String fromInputFile = doc.get("fromInputFile");
				System.out.println("Getting content for " + fromInputFile + "...");
				try {
					String xml = searcher.getContent(doc);
					File file = new File(exportDir, fromInputFile);
					System.out.println("Got content, exporting to " + file + "...");
					if (file.exists()) {
						// Add a number so we don't have to overwrite the previous file.
						System.out.println("WARNING: File " + file + " exists, using different name to avoid overwriting...");
						file = FileUtil.addNumberToExistingFileName(file);
					}
					System.out.println(file);
					File dir = file.getParentFile();
					if (!dir.exists())
						dir.mkdirs(); // create any subdirectories required
					try (PrintWriter pw = FileUtil.openForWriting(file)) {
						pw.write(xml);
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
