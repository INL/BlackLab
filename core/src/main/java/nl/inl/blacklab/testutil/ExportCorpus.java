package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.Searcher.LuceneDocTask;
import nl.inl.util.FileUtil;

/** Export the original corpus from a BlackLab index. */
public class ExportCorpus {

	public static void main(String[] args) throws IOException {
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
		exportCorpus.export(exportDir);
	}

	Searcher searcher;

	public ExportCorpus(File indexDir) throws IOException {
		searcher = Searcher.open(indexDir);
	}

	/** Export the whole corpus.
	 * @param exportDir directory to export to
	 */
	private void export(final File exportDir) {

		final IndexReader reader = searcher.getIndexReader();

		searcher.forEachDocument(new LuceneDocTask() {

			int totalDocs = reader.maxDoc() - reader.numDeletedDocs();

			int docsDone = 0;

			@Override
			public void perform(Document doc) {
				String fromInputFile = doc.get("fromInputFile");
				String xml = searcher.getContent(doc);
				File file = new File(exportDir, fromInputFile);
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
				docsDone++;
				if (docsDone % 100 == 0) {
					int perc = docsDone * 100 / totalDocs;
					System.out.println(docsDone + " docs exported (" + perc + "%)...");
				}
			}
		});
	}
}
