package nl.inl.blacklab.testutil;

import java.io.File;

import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.util.FileUtil;
import nl.inl.util.Timer;

/**
 * Executes a batch of CorpusQL queries and times them.
 * Doesn't do anything with the hits found. Can optionally (-t) also skip
 * reporting the total number of hits (this saves time for large result sets)
 */
public class BatchQuery {

	public static void main(String[] args) throws Exception {

		boolean determineTotalHits = true;
		int fileArgNumber = 0;
		File indexDir = null, inputFile = null;
		for (int i = 0; i < args.length; i++)  {
			String arg = args[i].trim();
			if (arg.charAt(0) == '-') {
				if (arg.equals("-t")) {
					determineTotalHits = false;
				} else {
					System.err.println("Illegal option: " + arg);
					usage();
					return;
				}
			} else {
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
					inputFile = new File(arg);
					if (!inputFile.exists()) {
						System.err.println("Input file not found: " + arg);
						usage();
						return;
					}
					break;
				default:
					System.err.println("Too many file arguments (supply index dir and input file)");
					usage();
					return;
				}
				fileArgNumber++;
			}
		}
		if (fileArgNumber < 2) {
			System.err.println("Too few file arguments (supply index dir and input file)");
			usage();
			return;
		}

		System.err.print("Opening index... ");
		Searcher searcher = Searcher.open(indexDir);
		System.err.println("done.");

		System.out.print("Query\tSearch Time");
		if (determineTotalHits) {
			System.out.print("\t# Hits\tTotal Time");
		}
		System.out.println("");

		for (String query : FileUtil.readLines(inputFile)) {
			query = query.trim();
			if (query.length() == 0 || query.charAt(0) == '#')
				continue; // skip empty lines and #-comments
			try {
				Timer t = new Timer();
				System.out.print(query + "\t");
				TextPattern tp = CorpusQueryLanguageParser.parse(query);
				Hits hits = searcher.find(tp);
				System.out.print(t.elapsed());
				if (determineTotalHits) {
					System.out.print("\t" + hits.size() + "\t" + t.elapsed());
				}
				System.out.println("");
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Error with query " + query + "; skipping...");
			}
		}
	}

	private static void usage() {
		System.err.println("\nUsage: " + BatchQuery.class.getSimpleName()
				+ " [options] <indexdir> <inputfile>\n\n"
				+ "<inputfile> should contain CQL queries, one per line.\n"
				+ "\n"
				+ "Options:\n"
				+ "-t do not determine total number of hits\n"
				+ "\n"
				+ "Output:\n"
				+ "<query>\t<searchTimeMs>\n"
				+ "<query>\t<searchTimeMs>\t<totalHits>\t<totalTimeMs>\n");
	}
}
