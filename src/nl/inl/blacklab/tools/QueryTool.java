/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.indexers.alto.AltoUtils;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.TokenMgrError;
import nl.inl.blacklab.queryParser.lucene.LuceneQueryParser;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.IndexStructure;
import nl.inl.blacklab.search.IndexStructure.ComplexFieldDesc;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TokenFrequency;
import nl.inl.blacklab.search.TokenFrequencyList;
import nl.inl.blacklab.search.grouping.GroupProperty;
import nl.inl.blacklab.search.grouping.GroupPropertyIdentity;
import nl.inl.blacklab.search.grouping.GroupPropertySize;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyDocumentId;
import nl.inl.blacklab.search.grouping.HitPropertyHitText;
import nl.inl.blacklab.search.grouping.HitPropertyLeftContext;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;
import nl.inl.blacklab.search.grouping.HitPropertyRightContext;
import nl.inl.blacklab.search.grouping.HitPropertyWordLeft;
import nl.inl.blacklab.search.grouping.HitPropertyWordRight;
import nl.inl.blacklab.search.grouping.RandomAccessGroup;
import nl.inl.blacklab.search.grouping.ResultsGrouper;
import nl.inl.util.FileUtil;
import nl.inl.util.IoUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.Timer;
import nl.inl.util.XmlUtil;

import org.apache.log4j.BasicConfigurator;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.util.Version;

/**
 * Simple command-line querying tool for BlackLab indices.
 */
public class QueryTool {
	private static boolean IS_ALTO = false; // DEBUG

	/**
	 * Our output stream; System.out wrapped in a PrintStream to output Latin-1
	 */
	static PrintStream out = System.out;

	static boolean batchMode = false;

	public static void outprintln(String str) {
		if (!batchMode)
			out.println(str);
	}

	public static void outprint(String str) {
		if (!batchMode)
			out.print(str);
	}

	public static void outprintf(String str, Object... args) {
		if (!batchMode)
			out.printf(str, args);
	}

	public static void errprintln(String str) {
		System.err.println(str);
	}

	public static void statprintln(String str) {
		if (batchMode)
			out.println(str);
	}

	/**
	 * Our BlackLab Searcher object.
	 */
	private Searcher searcher;

	/**
	 * The hits that are the result of our query.
	 */
	private Hits hits = null;

	/**
	 * The groups, or null if we haven't grouped our results.
	 */
	private ResultsGrouper groups = null;

	/**
	 * The collocations, or null if we're not looking at collocations.
	 */
	private TokenFrequencyList collocations = null;

	/**
	 * What property to use for collocations (
	 */
	private String collocProperty = null;

	/**
	 * The first hit or group to show on the current results page.
	 */
	private int firstResult;

	/**
	 * Number of hits or groups to show per results page.
	 */
	private int resultsPerPage = 20;

	/** Show document titles between hits? */
	private boolean showDocTitle = false;

	/** Show concordances or not? (if not, just shows number of hits) */
	private boolean showConc = true;

	/** Show extra information about query being processed? */
	private boolean verbose = false;

	/** Show total number of hits (takes extra time for large sets) */
	private boolean determineTotalNumberOfHits = true;

	/** If true, describes time in minutes and seconds.
	 *  If false, just gives the number of milliseconds. */
	boolean timeDisplayHumanFriendly = false;

	/**
	 * The filter query, if any.
	 */
	private Query filterQuery = null;

	/**
	 * What results view do we want to see?
	 */
	enum ShowSetting {
		HITS, GROUPS, COLLOC
	}

	/**
	 * What results view do we want to see? (hits, groups or collocations)
	 */
	private ShowSetting showSetting = ShowSetting.HITS;

	/**
	 * If we're looking at hits in one group, this is the index of the group number. Otherwise, this
	 * is -1.
	 */
	private int showWhichGroup = -1;

	static boolean isContextQlAvailable = false;

	/** Thrown when an error occurs during parsing */
	class ParseException extends Exception {

		public ParseException() {
			super();
		}

		public ParseException(String message, Throwable cause) {
			super(message, cause);
		}

		public ParseException(String message) {
			super(message);
		}

		public ParseException(Throwable cause) {
			super(cause);
		}
	}

	/** Generic command parser interface */
	interface Parser {
		String getPrompt();

		String getName();

		TextPattern parse(String query) throws ParseException;

		void printHelp();

		Parser nextParser();
	}

	/** Parser for Corpus Query Language */
	class ParserCorpusQl implements Parser {

		@Override
		public String getPrompt() {
			return "CorpusQL";
		}

		@Override
		public String getName() {
			return "Corpus Query Language";
		}

		/**
		 * Parse a Corpus Query Language query to produce a TextPattern
		 *
		 * @param query
		 *            the query
		 * @return the corresponding TextPattern
		 * @throws ParseException
		 */
		@Override
		public TextPattern parse(String query) throws ParseException {
			try {
				CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser(new StringReader(
						query));
				// parser.setAllowSingleQuotes(true);
				return parser.query();
			} catch (nl.inl.blacklab.queryParser.corpusql.ParseException e) {
				throw new ParseException(e.getMessage());
			} catch (Exception e) {
				throw new ParseException("Fatale fout tijdens parsen: " + e.getMessage());
			}
		}

		@Override
		public void printHelp() {
			outprintln("Corpus Query Language examples:");
			outprintln("  \"stad\" | \"dorp\"                  # Find the word \"stad\" or the word \"dorp\"");
			outprintln("  \"de\" \"sta.*\"                     # Find \"de\" followed by a word starting with \"sta\"");
			outprintln("  [hw=\"zijn\" & pos=\"d.*\"]          # Find forms of the headword \"zijn\" as a posessive pronoun");
			outprintln("  [hw=\"zijn\"] [hw=\"blijven\"]       # Find a form of \"zijn\" followed by a form of \"blijven\"");
			outprintln("  \"der.*\"{2,}                      # Find two or more successive words starting with \"der\"");
			outprintln("  [pos=\"a.*\"]+ \"man\"               # Find adjectives applied to \"man\"");
			outprintln("  \"stad\" []{2,3} \"dorp\"            # Find \"stad\" followed within 2-3 words by \"dorp\"");
		}

		@Override
		public Parser nextParser() {
			return isContextQlAvailable ? new ParserContextQl() : new ParserLucene();
		}

	}

	public static boolean determineContextQlAvailable() {
		try {
			Class.forName("nl.inl.clarinsd.xcqlparser.XCQLParser");
			isContextQlAvailable = true;
		} catch (ClassNotFoundException e) {
			isContextQlAvailable = false;
		}
		return isContextQlAvailable;
	}

	/** Parser for Contextual Query Language */
	class ParserContextQl implements Parser {

		@Override
		public String getPrompt() {
			return "ContextQL";
		}

		@Override
		public String getName() {
			return "Context Query Language";
		}

		/**
		 * Parse a Contextual Query Language query to produce a TextPattern
		 *
		 * @param query
		 *            the query
		 * @return the corresponding TextPattern
		 * @throws ParseException
		 */
		@Override
		public TextPattern parse(String query) throws ParseException {

			try {
				Class<?> classXCQLParser = Class.forName("nl.inl.clarinsd.xcqlparser.XCQLParser");
				Method methodParse = classXCQLParser.getDeclaredMethod("parse", String.class);
				Object returnValue = methodParse.invoke(classXCQLParser, query);
				return (TextPattern) returnValue;
			} catch (ClassNotFoundException e) {
				throw new UnsupportedOperationException(e);
			} catch (NoSuchMethodException e) {
				errprintln("method not found");
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void printHelp() {
			outprintln("Contextual Query Language examples:");
			outprintln("  stad or dorp            # Find the word \"stad\" or the word \"dorp\"");
			outprintln("  \"de sta*\"               # Find \"de\" followed by a word starting with \"sta\"");
			outprintln("  hw=zijn and pos=d*      # Find forms of the headword \"zijn\" as a posessive pronoun");
		}

		@Override
		public Parser nextParser() {
			return new ParserLucene();
		}

	}

	/** Parser for Lucene Query Language */
	class ParserLucene implements Parser {

		@Override
		public String getPrompt() {
			return "Lucene";
		}

		@Override
		public String getName() {
			return "Lucene Query Language";
		}

		/**
		 * Parse a Corpus Query Language query to produce a TextPattern
		 *
		 * @param query
		 *            the query
		 * @return the corresponding TextPattern
		 * @throws ParseException
		 */
		@Override
		public TextPattern parse(String query) throws ParseException {
			try {
				LuceneQueryParser parser = new LuceneQueryParser(Version.LUCENE_36, CONTENTS_FIELD,
						new WhitespaceAnalyzer(Version.LUCENE_36));
				return parser.parse(query);
			} catch (nl.inl.blacklab.queryParser.lucene.ParseException e) {
				throw new ParseException(e.getMessage());
			} catch (Exception e) {
				throw new ParseException("Fatale fout tijdens parsen: " + e.getMessage());
			}
		}

		@Override
		public void printHelp() {
			outprintln("Lucene Query Language examples:");
			outprintln("  stad dorp                  # Find the word \"stad\" or the word \"dorp\"");
			outprintln("  \"de stad\"                  # Find the word \"de\" followed by the word \"stad\"");
			outprintln("  +stad -dorp                # Find documents containing \"stad\" but not \"dorp\"");
		}

		@Override
		public Parser nextParser() {
			return new ParserCorpusQl();
		}

	}

	/** The current command parser object */
	private Parser currentParser = new ParserCorpusQl();

	/** Where to read commands from */
	private BufferedReader in;

	/** For stats output (batch mode), extra info (such as # hits) */
	private String statInfo;

	/** If false, command was not a query, prefix stats line with # */
	private boolean commandWasQuery;

	/** Size of larger snippet */
	private int snippetSize = 50;

	/**
	 * The main program.
	 *
	 * @param args
	 *            commandline arguments
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		File indexDir = null;
		File inputFile = null;
		String encoding = Charset.defaultCharset().name();
		for (int i = 0; i < args.length; i++)  {
			String arg = args[i].trim();
			if (arg.charAt(0) == '-') {
				if (arg.equals("-e")) {
					if (i + 1 == args.length) {
						errprintln("-e option needs argument");
						usage();
						return;
					}
					encoding = args[i + 1];
					i++;
				} else if (arg.equals("-f")) {
					if (i + 1 == args.length) {
						errprintln("-f option needs argument");
						usage();
						return;
					}
					inputFile = new File(args[i + 1]);
					i++;
					System.err.println("Batch mode; reading commands from " + inputFile);
				} else {
					errprintln("Unknown option: " + arg);
					usage();
					return;
				}
			} else {
				if (indexDir != null) {
					errprintln("Can only specify 1 index directory");
					usage();
					return;
				}
				indexDir = new File(arg);
			}
		}
		if (indexDir == null) {
			usage();
			return;
		}

		if (!indexDir.exists() || !indexDir.isDirectory()) {
			outprintln("Index dir " + indexDir.getPath() + " doesn't exist.");
			return;
		}

		// See if we can offer ContextQL or not (if the class is on the classpath)
		determineContextQlAvailable();

		// Special case for ALTO (to be generalized)
		String propIsAlto = System.getProperty("IS_ALTO");
		IS_ALTO = propIsAlto == null ? false : propIsAlto.equalsIgnoreCase("true");

		BasicConfigurator.configure();
		//BasicConfigurator.configure(new NullAppender()); // ignore logging for now

		// Change output encoding?
		if (args.length == 2) {
			try {
				// Yes
				out = new PrintStream(System.out, true, encoding);
				outprintln("Using output encoding " + encoding + "\n");
			} catch (UnsupportedEncodingException e) {
				// Nope; fall back to default
				errprintln("Unknown encoding " + encoding + "; using default");
				out = System.out;
			}
		}

		BufferedReader in;
		if (inputFile == null) {
			// No input file specified; use stdin
			in = IoUtil.makeBuffered(new InputStreamReader(System.in, encoding));
		}
		else {
			// Open input file
			in = FileUtil.openForReading(inputFile, "utf-8");
			batchMode = true;
		}

		try {
			QueryTool c = new QueryTool(indexDir, in);
			c.commandProcessor();
		} finally {
			in.close();
		}
	}

	private static void usage() {
		errprintln("Usage: " + QueryTool.class.getName() + " [options] <indexDir>");
		errprintln("");
		errprintln("Options:");
		errprintln("-e <encoding>   Specify what output encoding to use");
		errprintln(
			"-f <file>       Execute batch commands from file, print performance\n" +
			"                info and exit");
		errprintln("");
		errprintln(
			"In batch mode, for every command executed, the command is printed\n" +
			"to stdout with the elapsed time and (if applicable) the number of\n" +
			"hits found (tab-separated). Non-query commands are preceded by @.\n" +
			"\n" +
			"Batch command files should contain one command per line, or multiple\n" +
			"commands on a single line separated by && (use this e.g. to time\n" +
			"querying and sorting together). Lines starting with # are comments.\n" +
			"Comments are printed on stdout as well. Lines starting with - will\n" +
			"not be reported. Start a line with -# for an unreported comment.");
	}

	/**
	 * Construct the query tool object.
	 *
	 * @param indexDir
	 *            directory our index is in
	 * @param in
	 *      where to read commands from
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public QueryTool(File indexDir, BufferedReader in) throws CorruptIndexException, IOException {
		printProgramHead();
		outprintln("Opening index " + indexDir + "...");

		// Create the BlackLab searcher object
		searcher = new Searcher(indexDir);

		this.in = in;

		contextSize = searcher.getDefaultContextSize();
	}

	/**
	 * Parse and execute commands and queries.
	 */
	public void commandProcessor() {
		printHelp();

		while (true) {
			String prompt = currentParser.getPrompt() + "> ";
			String cmd;
			try {
				cmd = readCommand(prompt);
			} catch (IOException e1) {
				throw new RuntimeException(e1);
			}
			if (cmd == null || cmd.trim().equals("exit")) {
				break;
			}

			boolean printStat = true;
			if (cmd.length() > 0 && cmd.charAt(0) == '-') {
				// Command preceded by "-": silent, don't output stats
				printStat = false;
				cmd = cmd.substring(1).trim();
			}

			if (cmd.length() > 0 && cmd.charAt(0) == '#') {
				// Line starting with "#": comment
				if (printStat)
					statprintln(cmd);
				continue;
			}

			// Comment after command? Strip.
			cmd = cmd.replaceAll("#.+$", "").trim();
			if (cmd.length() == 0) {
				statprintln(""); // output empty lines in stats
				continue; // no actual command on line, skip
			}

			Timer t = new Timer();
			statInfo = "";
			commandWasQuery = false;
			processCommand(cmd);
			if (printStat)
				statprintln((commandWasQuery ? "" : "@ ") + cmd + "\t" + t.elapsed() + "\t" + statInfo);

			try {
				Thread.sleep(100); // Give Eclipse console time to show stderr output
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		cleanup();
	}

	private void processCommand(String fullCmd) {
		fullCmd = fullCmd.trim();
		if (fullCmd.charAt(0) == '#')
			return;

		String cmd = null, restCommand = null;
		int commandSeparatorIndex = fullCmd.indexOf("&&");
		if (commandSeparatorIndex >= 0) {
			cmd = fullCmd.substring(0, commandSeparatorIndex).trim();
			restCommand = fullCmd.substring(commandSeparatorIndex + 2).trim();
		} else {
			cmd = fullCmd;
		}

		String lcased = cmd.toLowerCase();
		if (lcased.length() > 0) {
			if (lcased.equals("prev") || lcased.equals("p")) {
				prevPage();
			} else if (lcased.equals("next") || lcased.equals("n")) {
				nextPage();
			} else if (lcased.startsWith("page ")) {
				showPage(Integer.parseInt(lcased.substring(5)) - 1);
			} else if (lcased.startsWith("pagesize ")) {
				resultsPerPage = Integer.parseInt(lcased.substring(9));
				firstResult = 0;
				//statprintln("# pagesize\t" + resultsPerPage);
				showResultsPage();
			} else if (lcased.startsWith("context ")) {
				contextSize = Integer.parseInt(lcased.substring(8));
				if (hits != null && hits.getContextSize() != contextSize) {
					hits.setContextSize(contextSize);
					collocations = null;
				}
				//statprintln("# context\t" + contextSize);
				showResultsPage();
			} else if (lcased.startsWith("snippet ")) {
				int hitId = Integer.parseInt(lcased.substring(8)) - 1;
				Hit h = getCurrentHitSet().get(hitId);
				Concordance conc = hits.getConcordance(h, snippetSize);
				String left = prepConcForDisplay(conc.left);
				String middle = prepConcForDisplay(conc.hit);
				String right = prepConcForDisplay(conc.right);
				outprintln("\n" + StringUtil.wrapText(left + "[" + middle + "]" + right, 80));
			} else if (lcased.startsWith("doc ")) {
				int docId = Integer.parseInt(lcased.substring(4));
				showMetadata(docId);
			} else if (lcased.startsWith("snippetsize ")) {
				snippetSize = Integer.parseInt(lcased.substring(12));
				outprintln("Snippets will show " + snippetSize + " words of context.");
			} else if (lcased.startsWith("filter ") || lcased.equals("filter")) {
				//statprintln("# filter\t" + cmd);
				if (cmd.length() <= 7) {
					filterQuery = null; // clear filter
					outprintln("Filter cleared.");
				} else {
					String filterExpr = cmd.substring(7);
					QueryParser qp = new QueryParser(Version.LUCENE_36, "title",
							new StandardAnalyzer(Version.LUCENE_36));
					try {
						filterQuery = qp.parse(filterExpr);
					} catch (org.apache.lucene.queryParser.ParseException e) {
						errprintln("Error parsing filter query.");
					}
					outprintln("Filter created: " + filterQuery);
				}
			} else if (lcased.startsWith("sensitive ")) {
				String v = lcased.substring(10);
				boolean caseSensitive = false, diacSensitive = false;
				if (v.equals("on") || v.equals("yes") || v.equals("true")) {
					caseSensitive = diacSensitive = true;
				} else if (v.equals("off") || v.equals("no") || v.equals("false")) {
					// nothing to do
				} else if (v.equals("case")) {
					caseSensitive = true;
				} else if (v.equals("diac") || v.equals("diacritics")) {
					diacSensitive = true;
				}
				searcher.setDefaultSearchSensitive(caseSensitive, diacSensitive);
				//statprintln("# sensitive\tcase: " + (caseSensitive ? "yed" : "no") + "\tdiac: " + (diacSensitive ? "yes" : "no"));
				outprintln("Search defaults to "
						+ (caseSensitive ? "case-sensitive" : "case-insensitive") + " and "
						+ (diacSensitive ? "diacritics-sensitive" : "diacritics-insensitive"));
			} else if (lcased.startsWith("doctitle ")) {
				String v = lcased.substring(9);
				showDocTitle = v.equals("on") || v.equals("yes") || v.equals("true");
				//statprintln("# doctitle\t" + (showDocTitle ? "on" : "off"));
				System.out.println("Show document titles: " + (showDocTitle ? "ON" : "OFF"));
			} else if (lcased.equals("struct") || lcased.equals("structure")) {
				showIndexStructure();
			} else if (lcased.startsWith("sort by ")) {
				sortBy(lcased.substring(8));
			} else if (lcased.startsWith("sort ")) {
				sortBy(lcased.substring(5));
			} else if (lcased.startsWith("group by ")) {
				String[] parts = lcased.substring(9).split("\\s+", 2);
				groupBy(parts[0], parts.length > 1 ? parts[1] : null);
			} else if (lcased.startsWith("group ")) {
				if (lcased.substring(6).matches("\\d+"))
					changeShowSettings(lcased);
				else {
					String[] parts = lcased.substring(6).split("\\s+", 2);
					groupBy(parts[0], parts.length > 1 ? parts[1] : null);
				}
			} else if (lcased.equals("groups") || lcased.equals("hits")
					|| lcased.startsWith("colloc") || lcased.startsWith("group ")) {
				changeShowSettings(cmd);
			} else if (lcased.equals("switch") || lcased.equals("sw")) {
				currentParser = currentParser.nextParser();
				//statprintln("# parser\t" + currentParser.getName());
				outprintln("Switching to " + currentParser.getName() + ".\n");
				printQueryHelp();
			} else if (lcased.equals("help") || lcased.equals("?")) {
				printHelp();
			} else if (lcased.equals("warmup")) {
				outprintln("Warming up the forward indices. This may take a while...");
				searcher.warmUpForwardIndices();
			} else if (lcased.startsWith("showconc ")) {
				String v = lcased.substring(9);
				showConc = v.equals("on") || v.equals("yes") || v.equals("true");
				//statprintln("# showconc\t" + (showConc ? "on" : "off"));
				System.out.println("Show concordances: " + (showConc ? "ON" : "OFF"));
			} else if (lcased.startsWith("verbose ")) {
				String v = lcased.substring(8);
				verbose = v.equals("on") || v.equals("yes") || v.equals("true");
				//statprintln("# verbose\t" + (verbose ? "on" : "off"));
				outprintln("Verbose: " + (verbose ? "ON" : "OFF"));
			} else if (lcased.startsWith("total ")) {
				String v = lcased.substring(6);
				determineTotalNumberOfHits = v.equals("on") || v.equals("yes") || v.equals("true");
				//statprintln("# total\t" + (determineTotalNumberOfHits ? "on" : "off"));
				outprintln("Determine total number of hits: " + (determineTotalNumberOfHits ? "ON" : "OFF"));
			} else {
				// Not a command; assume it's a query
				parseAndExecuteQuery(cmd);
			}
		}

		if (restCommand != null)
			processCommand(restCommand);
	}

	private void showMetadata(int docId) {
		Document doc = searcher.document(docId);
		Map<String, String> metadata = new TreeMap<String, String>(); // sort by key
		for (Fieldable f: doc.getFields()) {
			metadata.put(f.name(), f.stringValue());
		}
		for (Map.Entry<String, String> e: metadata.entrySet()) {
			outprintln(e.getKey() + ": " + e.getValue());
		}
	}

	private void showIndexStructure() {
		IndexStructure s = searcher.getIndexStructure();
		outprintln("INDEX STRUCTURE FOR INDEX " + searcher.getIndexName() + "\n");
		s.print(System.out);
	}

	/** If JLine is available, this holds the ConsoleReader object */
	Object jlineConsoleReader;

	/** If JLine is available, this holds the readLine() method */
	Method jlineReadLineMethod;

	/** Did we check if JLine is available? */
	boolean jlineChecked = false;

	private String readCommand(String prompt) throws IOException {
		if (!batchMode && jlineConsoleReader == null && !jlineChecked) {
			jlineChecked = true;
			try {
				Class<?> c = Class.forName("jline.ConsoleReader");
				jlineConsoleReader = c.newInstance();

				// Disable bell
				c.getMethod("setBellEnabled", boolean.class).invoke(jlineConsoleReader, false);

				// Fetch and store the readLine method
				jlineReadLineMethod = c.getMethod("readLine", String.class);

				errprintln("Command line editing enabled.");
			} catch (ClassNotFoundException e) {
				// Can't init JLine; too bad, fall back to stdin
				errprintln("Command line editing not available; to enable, place jline jar in classpath.");
			} catch (Exception e) {
				throw new RuntimeException("Could not init JLine console reader", e);
			}
		}

		if (jlineConsoleReader != null) {
			try {
				return (String) jlineReadLineMethod.invoke(jlineConsoleReader, prompt);
			} catch (Exception e) {
				throw new RuntimeException("Could not invoke JLine ConsoleReader.readLine()", e);
			}
		}

		outprint(prompt);
		out.flush();
		return in.readLine();
	}

	/**
	 * Print command and query help.
	 */
	private void printHelp() {
		String langAvail = "Lucene, CorpusQL" + (isContextQlAvailable ? ", ContextQL" : "");

		outprintln("Control commands:");
		outprintln("  sw(itch)                           # Switch languages (" + langAvail + ")");
		outprintln("  p(rev) / n(ext) / page <n>         # Page through results");
		outprintln("  sort {match|left|right} [prop]     # Sort query results  (left = left context, etc.)");
		outprintln("  group {match|left|right} [prop]    # Group query results (prop = e.g. 'word', 'lemma', 'pos')");
		outprintln("  hits / groups / group <n> / colloc # Switch between results modes");
		outprintln("  context <n>                        # Set number of words to show around hits");
		outprintln("  pagesize <n>                       # Set number of hits to show per page");
		outprintln("  snippet <x>                        # Show longer snippet around hit x");
		outprintln("  doc <id>                           # Show metadata for doc id");
		outprintln("  snippetsize <n>                    # Words to show around hit in longer snippet");
		outprintln("  sensitive {on|off|case|diac}       # Set case-/diacritics-sensitivity");
		outprintln("  filter <luceneQuery>               # Set document filter, e.g. title:\"Smith\"");
		outprintln("  doctitle {on|off}                  # Show document titles between hits?");
		outprintln("  warmup                             # Warm up the forward indices");
		outprintln("  struct                             # Show index structure");
		outprintln("  help                               # This message");
		outprintln("  exit                               # Exit program");
		outprintln("");

		printQueryHelp();
	}

	/**
	 * Print some examples of the currently selected query language.
	 */
	private void printQueryHelp() {
		currentParser.printHelp();
		outprintln("");
	}

	/**
	 * Show the program head.
	 */
	private void printProgramHead() {
		outprintln("BlackLab Query Tool");
		outprintln("===================");
	}

	/**
	 * Parse and execute a query in the current query format.
	 *
	 * @param query
	 *            the query
	 */
	private void parseAndExecuteQuery(String query) {
		Timer t = new Timer();
		try {
			TextPattern pattern = currentParser.parse(query);
			pattern = pattern.rewrite();
			if (verbose)
				outprintln("TextPattern: " + pattern.toString(searcher, CONTENTS_FIELD));

			// Execute search
			// Filter filter = null; // TODO: metadata search!
			Filter filter = filterQuery == null ? null : new QueryWrapperFilter(filterQuery);
			SpanQuery spanQuery = searcher.createSpanQuery(pattern, filter);
			if (verbose)
				outprintln("SpanQuery: " + spanQuery.toString(CONTENTS_FIELD));
			hits = searcher.find(spanQuery);
			groups = null;
			collocations = null;
			showWhichGroup = -1;
			showSetting = ShowSetting.HITS;
			firstResult = 0;
			long searchTime = t.elapsed();
			showResultsPage();
			reportTime("search", searchTime, "display", t.elapsed() - searchTime);
			if (determineTotalNumberOfHits)
				statInfo = "" + hits.size();
			else
				statInfo = "?";
			commandWasQuery = true;
			//statprintln(query + "\t" + hits.size() + "\t" + t.elapsed());
		} catch (TokenMgrError e) {
			// Lexical error
			errprintln("Invalid query: " + e.getMessage());
			errprintln("(Type 'help' for examples or see accompanying documents)");
		} catch (ParseException e) {
			// Parse error
			errprintln("Invalid query: " + e.getMessage());
			errprintln("(Type 'help' for examples or see accompanying documents)");
		} catch (UnsupportedOperationException e) {
			// Unimplemented part of query language used
			errprintln("Cannot execute query; " + e.getMessage());
			errprintln("(Type 'help' for examples or see accompanying documents)");
		}
	}

	/**
	 * Show the a specific page of results.
	 * @param pageNumber which page to show
	 */
	private void showPage(int pageNumber) {
		if (hits != null) {

			if (determineTotalNumberOfHits) {
				// Clamp page number of total number of hits
				int totalResults;
				switch (showSetting) {
				case COLLOC:
					totalResults = collocations.size();
					break;
				case GROUPS:
					totalResults = groups.numberOfGroups();
					break;
				default:
					totalResults = hits.size();
					break;
				}

				int totalPages = (totalResults + resultsPerPage - 1) / resultsPerPage;
				if (pageNumber < 0)
					pageNumber = totalPages - 1;
				if (pageNumber >= totalPages)
					pageNumber = 0;
			}

			// Next page
			firstResult = pageNumber * resultsPerPage;
			//statprintln("# page\t" + pageNumber);
			showResultsPage();
		}
	}

	/**
	 * Show the next page of results.
	 */
	private void nextPage() {
		showPage(firstResult / resultsPerPage + 1);
	}

	/**
	 * Show the previous page of results.
	 */
	private void prevPage() {
		showPage(firstResult / resultsPerPage - 1);
	}

	/**
	 * Sort either hits or groups by the specified property.
	 *
	 * @param sortBy
	 *            property to sort by
	 */
	private void sortBy(String sortBy) {
		if (hits == null)
			return;

		switch (showSetting) {
		case COLLOC:
			throw new UnsupportedOperationException();
		case GROUPS:
			sortGroups(sortBy);
			break;
		default:
			String[] parts = sortBy.split("\\s+", 2);
			sortHits(parts[0], parts.length > 1 ? parts[1] : null);
			break;
		}
	}

	final String CONTENTS_FIELD = Searcher.DEFAULT_CONTENTS_FIELD_NAME;

	/**
	 * Desired context size
	 */
	private int contextSize;

	/**
	 * Sort hits by the specified property.
	 *
	 * @param sortBy
	 *            hit property to sort by
	 * @param property
	 *            (optional) if sortBy is a context property (say, hit text), this gives the token
	 *            property to use for the context. Example: if this is "lemma", will look at the
	 *            lemma(ta) of the hit text. If this is null, uses the "main property" (word form,
	 *            usually).
	 */
	private void sortHits(String sortBy, String property) {
		Timer t = new Timer();

		Hits hitsToSort = getCurrentHitSet();

		HitProperty crit = null;
		if (sortBy.equals("doc"))
			crit = new HitPropertyDocumentId();
		else {
			if (sortBy.equals("match") || sortBy.equals("word"))
				crit = new HitPropertyHitText(searcher, CONTENTS_FIELD, property);
			else if (sortBy.startsWith("left"))
				crit = new HitPropertyLeftContext(searcher, CONTENTS_FIELD, property);
			else if (sortBy.startsWith("right"))
				crit = new HitPropertyRightContext(searcher, CONTENTS_FIELD, property);
			else if (sortBy.equals("test")) {
				HitProperty p1 = new HitPropertyHitText(searcher, CONTENTS_FIELD, "lemma");
				HitProperty p2 = new HitPropertyHitText(searcher, CONTENTS_FIELD, "type");
				crit = new HitPropertyMultiple(p1, p2);
			}
		}
		if (crit == null) {
			errprintln("Invalid hit sort criterium: " + sortBy
					+ " (valid are: match, left, right)");
		} else {
			hitsToSort.sort(crit);
			firstResult = 0;
			long sortTime = t.elapsed();
			showResultsPage();
			if (property == null)
				property = "(default)";
			//statprintln("# sort\t" + sortBy + "\t" + property + "\t" + t.elapsed());
			reportTime("sort", sortTime, "display", t.elapsed() - sortTime);
		}
	}

	/**
	 * Sort groups by the specified property.
	 *
	 * @param sortBy
	 *            property to sort by
	 */
	private void sortGroups(String sortBy) {
		GroupProperty crit = null;
		if (sortBy.equals("identity") || sortBy.equals("id"))
			crit = new GroupPropertyIdentity();
		else if (sortBy.startsWith("size"))
			crit = new GroupPropertySize();
		if (crit == null) {
			errprintln("Invalid group sort criterium: " + sortBy
					+ " (valid are: id(entity), size)");
		} else {
			groups.sortGroups(crit, false);
			firstResult = 0;
			//statprintln("# sortgroups\t" + sortBy);
			showResultsPage();
		}
	}

	/**
	 * Group hits by the specified property.
	 *
	 * @param groupBy
	 *            hit property to group by
	 * @param property
	 *            (optional) if groupBy is a context property (say, hit text), this gives the token
	 *            property to use for the context. Example: if this is "lemma", will look at the
	 *            lemma(ta) of the hit text. If this is null, uses the "main property" (word form,
	 *            usually).
	 */
	private void groupBy(String groupBy, String property) {
		if (hits == null)
			return;

		Timer t = new Timer();

		// Group results
		HitProperty crit = null;
		if (groupBy.equals("word") || groupBy.equals("match"))
			crit = new HitPropertyHitText(searcher, CONTENTS_FIELD, property);
		else if (groupBy.startsWith("left"))
			crit = new HitPropertyWordLeft(searcher, CONTENTS_FIELD, property);
		else if (groupBy.startsWith("right"))
			crit = new HitPropertyWordRight(searcher, CONTENTS_FIELD, property);
		else if (groupBy.equals("test")) {
			HitProperty p1 = new HitPropertyHitText(searcher, CONTENTS_FIELD, "lemma");
			HitProperty p2 = new HitPropertyHitText(searcher, CONTENTS_FIELD, "type");
			crit = new HitPropertyMultiple(p1, p2);
		}
		if (crit == null) {
			errprintln("Unknown criterium: " + groupBy);
			return;
		}
		groups = new ResultsGrouper(hits, crit);
		showSetting = ShowSetting.GROUPS;
		long groupTime = t.elapsed();
		sortGroups("size");
		if (property == null)
			property = "(default)";
		//statprintln("# group\t" + groupBy + "\t" + property + "\t" + t.elapsed());
		reportTime("group", groupTime, "sort/display", t.elapsed() - groupTime);
	}

	/**
	 * Switch between showing all hits, groups, and the hits in one group.
	 * @param showWhat what type of results to show
	 */
	private void changeShowSettings(String showWhat) {
		if (showWhat.equals("hits")) {
			showSetting = ShowSetting.HITS;
			showWhichGroup = -1;
		} else if (showWhat.startsWith("colloc") && hits != null) {
			showSetting = ShowSetting.COLLOC;
			if (showWhat.length() >= 7) {
				String newCollocProp = showWhat.substring(7);
				if (!newCollocProp.equals(collocProperty)) {
					collocProperty = newCollocProp;
					collocations = null;
				}
			}
		} else if (showWhat.equals("groups") && groups != null) {
			showSetting = ShowSetting.GROUPS;
		} else if (showWhat.startsWith("group ") && groups != null) {
			showWhichGroup = Integer.parseInt(showWhat.substring(6)) - 1;
			if (showWhichGroup < 0 || showWhichGroup >= groups.numberOfGroups()) {
				errprintln("Group doesn't exist");
				showWhichGroup = -1;
			} else
				showSetting = ShowSetting.HITS; // Show hits in group, not all the groups
		}
		//statprintln("# show\t" + showWhat);
		showResultsPage();
	}

	/**
	 * If an operation took longer than 5 seconds, report the time it took.
	 *
	 * @param name1 name of the first part of the operation
	 * @param time1 how long the first part took
	 * @param name2 name of the second part of the operation
	 * @param time2 how long the second part took
	 */
	private void reportTime(String name1, long time1, String name2, long time2) {
		if (verbose) {
			outprintln(describeInterval(time1 + time2) + " elapsed ("
					+ describeInterval(time1) + " " + name1 + ", "
					+ describeInterval(time2) + " " + name2 + ")");
			return;
		}

		outprintln(describeInterval(time1 + time2) + " elapsed");
	}

	private String describeInterval(long time1) {
		if (timeDisplayHumanFriendly)
			return Timer.describeInterval(time1);
		return time1 + " ms";
	}

	/**
	 * Close the Searcher object.
	 */
	private void cleanup() {
		searcher.close();
	}

	/**
	 * Show the current results page (either hits or groups).
	 */
	private void showResultsPage() {
		switch (showSetting) {
		case COLLOC:
			showCollocations();
			break;
		case GROUPS:
			showGroupsPage();
			break;
		default:
			showHitsPage();
			break;

		}
	}

	/**
	 * Show the current page of collocations.
	 */
	private void showCollocations() {
		if (collocations == null) {
			// Case-sensitive collocations..?
			String fieldName = hits.getConcordanceFieldName();
			if (collocProperty == null) {
				ComplexFieldDesc cf = searcher.getIndexStructure().getComplexFieldDesc(fieldName);
				collocProperty = cf.getMainProperty().getName();
			}

			collocations = hits.getCollocations(collocProperty,
					searcher.getDefaultExecutionContext(fieldName));
			collocations.sort();
		}

		int i = 0;
		for (TokenFrequency coll : collocations) {
			if (i >= firstResult && i < firstResult + resultsPerPage) {
				int j = i - firstResult + 1;
				outprintln(String.format("%4d %7d %s", j, coll.frequency, coll.token));
			}
			i++;
		}

		// Summarize
		String msg = collocations.size() + " collocations";
		if (collocations.size() > resultsPerPage)
			msg = (firstResult + 1) + "-" + i + " of " + collocations.size() + " collocations";
		outprintln(msg);
	}

	/**
	 * Show the current page of group results.
	 */
	private void showGroupsPage() {
		List<RandomAccessGroup> listGroups = groups.getGroups();
		int i;
		for (i = firstResult; i < groups.numberOfGroups() && i < firstResult + resultsPerPage; i++) {
			RandomAccessGroup g = listGroups.get(i);
			outprintln(String.format("%4d. %5d %s", i + 1, g.size(), g.getIdentity().toString()));
		}

		// Summarize
		String msg = groups.numberOfGroups() + " groups";
		outprintln(msg);
	}

	/**
	 * Show the current page of hits.
	 */
	private void showHitsPage() {

		if (!showConc) {
			if (determineTotalNumberOfHits) {
				// Just show total number of hits, no concordances
				outprintln(getCurrentHitSet().size() + " hits");
			} else {
				Iterator<Hit> it = getCurrentHitSet().iterator();
				int i;
				for (i = 0; it.hasNext() && i < resultsPerPage; i++) {
					it.next();
				}
				outprintln( (i == resultsPerPage ? "At least " : "") + i + " hits (total not determined)");
			}
			return;
		}

		/**
		 * A hit we're about to show.
		 *
		 * We need a separate structure because we filter out XML tags and need to know the longest
		 * left context before displaying.
		 */
		class HitToShow {
			public int doc;

			public String left, hitText, right;

			public HitToShow(int doc, String left, String hitText, String right) {
				super();
				this.doc = doc;
				this.left = left;
				this.hitText = hitText;
				this.right = right;
			}
		}

		Hits hitsToShow = getCurrentHitSet();
		if (hitsToShow == null)
			return; // nothing to show

		// Limit results to the first n
		HitsWindow window = new HitsWindow(hitsToShow, firstResult, resultsPerPage);

		// Compile hits display info and calculate necessary width of left context column
		List<HitToShow> toShow = new ArrayList<HitToShow>();
		window.setContextSize(contextSize); // number of words around hit
		int leftContextMaxSize = 10; // number of characters to reserve on screen for left context
		for (Hit hit : window) {
			Concordance conc = window.getConcordance(hit);
			String left, hitText, right;
			if (IS_ALTO) {
				// Content in CONTENT attribute
				left = AltoUtils.getFromContentAttributes(conc.left) + " ";
				hitText = AltoUtils.getFromContentAttributes(conc.hit);
				right = " " + AltoUtils.getFromContentAttributes(conc.right);
			} else {
				// Content in text nodes
				left = prepConcForDisplay(conc.left);
				hitText = prepConcForDisplay(conc.hit);
				right = prepConcForDisplay(conc.right);
			}
			toShow.add(new HitToShow(hit.doc, left, hitText, right));
			if (leftContextMaxSize < left.length())
				leftContextMaxSize = left.length();
		}

		// Display hits
		String format = "%4d. [%04d] %" + leftContextMaxSize + "s[%s]%s\n";
		if (showDocTitle)
			format = "%4d. %" + leftContextMaxSize + "s[%s]%s\n";
		int currentDoc = -1;
		String titleField = searcher.getIndexStructure().getDocumentTitleField();
		int hitNr = window.first() + 1;
		for (HitToShow hit : toShow) {
			if (showDocTitle && hit.doc != currentDoc) {
				if (currentDoc != -1)
					outprintln("");
				currentDoc = hit.doc;
				Document d = searcher.document(currentDoc);
				String title = d.get(titleField);
				if (title == null)
					title = "(doc #" + currentDoc + ", no " + titleField + " given)";
				else
					title = title + " (doc #" + currentDoc + ")";
				outprintln("--- " + title + " ---");
			}
			if (showDocTitle)
				outprintf(format, hitNr, hit.left, hit.hitText, hit.right);
			else
				outprintf(format, hitNr, hit.doc, hit.left, hit.hitText, hit.right);
			hitNr++;
		}

		// Summarize
		String msg;
		if (!determineTotalNumberOfHits) {
			msg = hitsToShow.totalSize() + " hits counted so far (total not determined)";
		}
		else {
			int numberRetrieved = hitsToShow.size();
			String hitsInDocs = numberRetrieved + " hits in " + hitsToShow.numberOfDocs() + " documents";
			if (hitsToShow.maxHitsRetrieved()) {
				if (hitsToShow.maxHitsCounted()) {
					msg = hitsInDocs + " retrieved, more than " + hitsToShow.totalSize() + " total";
				} else {
					msg = hitsInDocs + " retrieved, " + hitsToShow.totalSize() + " total";
				}
			} else {
				msg = hitsInDocs;
			}
		}
		outprintln(msg);
	}

	String prepConcForDisplay(String input) {
		return XmlUtil.xmlToPlainText(input);
	}

	/**
	 * Returns the hit set we're currently looking at.
	 *
	 * This is either all hits or the hits in one group.
	 *
	 * @return the hit set
	 */
	private Hits getCurrentHitSet() {
		Hits hitsToShow = hits;
		if (showWhichGroup >= 0) {
			RandomAccessGroup g = groups.getGroups().get(showWhichGroup);
			hitsToShow = g.getHits();
		}
		return hitsToShow;
	}
}
