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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocResult;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.perdocument.DocResultsWindow;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.CompleteQuery;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.grouping.GroupProperty;
import nl.inl.blacklab.search.grouping.GroupPropertyIdentity;
import nl.inl.blacklab.search.grouping.GroupPropertySize;
import nl.inl.blacklab.search.grouping.HitGroup;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyDocumentId;
import nl.inl.blacklab.search.grouping.HitPropertyDocumentStoredField;
import nl.inl.blacklab.search.grouping.HitPropertyHitText;
import nl.inl.blacklab.search.grouping.HitPropertyLeftContext;
import nl.inl.blacklab.search.grouping.HitPropertyMultiple;
import nl.inl.blacklab.search.grouping.HitPropertyRightContext;
import nl.inl.blacklab.search.grouping.HitPropertyWordLeft;
import nl.inl.blacklab.search.grouping.HitPropertyWordRight;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.TimeUtil;
import nl.inl.util.Timer;
import nl.inl.util.XmlUtil;

/**
 * Simple command-line querying tool for BlackLab indices.
 */
public class QueryTool {

	static final Charset INPUT_FILE_ENCODING = Charset.forName("utf-8");

	/** Our output writer. */
	public PrintWriter out;

	/** Our error writer (if null, output errors to out as well) */
	public PrintWriter err;

	static boolean batchMode = false;

	/** Our BlackLab Searcher object. */
	Searcher searcher;

	/** The hits that are the result of our query. */
	private Hits hits = null;

	/** The docs that are the result of our query. */
	private DocResults docs = null;

	/** The groups, or null if we haven't grouped our results. */
	private HitGroups groups = null;

	/** The collocations, or null if we're not looking at collocations. */
	private TermFrequencyList collocations = null;

	/** What property to use for collocations */
	private String collocProperty = null;

	/** The first hit or group to show on the current results page. */
	private int firstResult;

	/** Number of hits or groups to show per results page. */
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

	/** The filter query, if any. */
	private Query filterQuery = null;

	/** What results view do we want to see? */
	enum ShowSetting {
		HITS, DOCS, GROUPS, COLLOC
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

	/** Lists of words read from file to choose random word from (for batch mode) */
	private Map<String, List<String>> wordLists = new HashMap<>();

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
	abstract class Parser {
		public abstract String getPrompt();

		public abstract String getName();

		public abstract TextPattern parse(String query) throws ParseException;

		/** Get the filter query included in the last query, if any. Only used for ContextQL.
		 * @return the filter query, or null if there was none */
		Query getIncludedFilterQuery() { return null; }

		public abstract void printHelp();
	}

	/** Parser for Corpus Query Language */
	class ParserCorpusQl extends Parser {

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
				return CorpusQueryLanguageParser.parse(query);
			} catch (nl.inl.blacklab.queryParser.corpusql.ParseException e) {
				throw new ParseException(e.getMessage());
			} catch (nl.inl.blacklab.queryParser.corpusql.TokenMgrError e) {
				throw new ParseException(e.getMessage());
			} catch (Exception e) {
				throw new ParseException("Fatale fout tijdens parsen: " + e.getMessage());
			}
		}

		@Override
		public void printHelp() {
			outprintln("Corpus Query Language examples:");
			outprintln("  \"city\" | \"town\"               # the word \"city\" or the word \"town\"");
			outprintln("  \"the\" \"cit.*\"                 # \"the\" followed by word starting with \"cit\"");
			outprintln("  [lemma=\"plan\" & pos=\"N.*\"]    # forms of the word \"plan\" as a noun");
			outprintln("  [lemma=\"be\"] [lemma=\"stay\"]   # form of \"be\" followed by form of \"stay\"");
			outprintln("  [lemma=\"be\"]{2,}              # two or more successive forms of \"to be\"");
			outprintln("  [pos=\"J.*\"]+ \"man\"            # adjectives applied to \"man\"");
			outprintln("  \"town\" []{0,5} \"city\"         # \"city\" after \"town\", up to 5 words in between");
		}

	}

	/** Parser for Contextual Query Language */
	class ParserContextQl extends Parser {

		Query includedFilterQuery = null;

		@Override
		public String getPrompt() {
			return "ContextQL";
		}

		@Override
		public String getName() {
			return "Contextual Query Language";
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
				//outprintln("WARNING: SRU CQL SUPPORT IS EXPERIMENTAL, MAY NOT WORK AS INTENDED");
				CompleteQuery q = ContextualQueryLanguageParser.parse(searcher, query);
				includedFilterQuery = q.getFilterQuery();
				return q.getContentsQuery();
			} catch (nl.inl.blacklab.queryParser.contextql.ParseException e) {
				throw new ParseException(e.getMessage());
			} catch (nl.inl.blacklab.queryParser.contextql.TokenMgrError e) {
				throw new ParseException(e.getMessage());
			}

			/*
			Class<?> classXCQLParser = Class.forName("nl.inl.clarinsd.xcqlparser.XCQLParser");
			Method methodParse = classXCQLParser.getDeclaredMethod("parse", String.class);
			Object returnValue = methodParse.invoke(classXCQLParser, query);
			return (TextPattern) returnValue;
			*/
		}

		@Override
		public Query getIncludedFilterQuery() {
			return includedFilterQuery;
		}

		@Override
		public void printHelp() {
			outprintln("Contextual Query Language examples:");
			outprintln("  city or town                  # Find the word \"city\" or the word \"town\"");
			outprintln("  \"the cit*\"                    # Find \"the\" followed by a word starting with \"cit\"");
			outprintln("  lemma=plan and pos=N*         # Find forms of \"plan\" as a noun");
			outprintln("  lemma=\"be stay\"               # form of \"be\" followed by form of \"stay\"");
			outprintln("  town prox//5//ordered city    # (NOTE: this is not supported yet!)");
			outprintln("                                # \"city\" after \"town\", up to 5 words in between");
			outprintln("\nWARNING: THIS PARSER IS STILL VERY MUCH EXPERIMENTAL. NOT SUITABLE FOR PRODUCTION.");
		}

	}

	private List<Parser> parsers = Arrays.asList(new ParserCorpusQl(), new ParserContextQl());

	private int currentParserIndex = 0;

	/** Where to read commands from */
	private BufferedReader in;

	/** For stats output (batch mode), extra info (such as # hits) */
	private String statInfo;

	/** If false, command was not a query, prefix stats line with # */
	private boolean commandWasQuery;

	/** Size of larger snippet */
	private int snippetSize = 50;

	/** Don't allow file operations in web mode */
	private boolean webSafeOperationOnly = false;

	/** Strip XML tags when displaying concordances? */
	private boolean stripXML = true;

	private String contentsField;

	/**
	 * The main program.
	 *
	 * @param args
	 *            commandline arguments
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		// Parse command line
		File indexDir = null;
		File inputFile = null;
		String encoding = Charset.defaultCharset().name();
		for (int i = 0; i < args.length; i++)  {
			String arg = args[i].trim();
			if (arg.length() > 0 && arg.charAt(0) == '-') {
				if (arg.equals("-e")) {
					if (i + 1 == args.length) {
						System.err.println("-e option needs argument");
						usage();
						return;
					}
					encoding = args[i + 1];
					i++;
				} else if (arg.equals("-f")) {
					if (i + 1 == args.length) {
						System.err.println("-f option needs argument");
						usage();
						return;
					}
					inputFile = new File(args[i + 1]);
					i++;
					System.err.println("Batch mode; reading commands from " + inputFile);
				} else {
					System.err.println("Unknown option: " + arg);
					usage();
					return;
				}
			} else {
				if (indexDir != null) {
					System.err.println("Can only specify 1 index directory");
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

		LogUtil.initLog4jIfNotAlready();
		run(indexDir, inputFile, encoding);
	}

	/**
	 * Run the QueryTool in batch mode
	 * @param indexDir the index to search
	 * @param commandFile the command file to execute
	 * @param encoding the output encoding
	 * @throws UnsupportedEncodingException
	 * @throws CorruptIndexException
	 */
	public static void runBatch(File indexDir, File commandFile, String encoding) throws UnsupportedEncodingException, CorruptIndexException {
		run(indexDir, commandFile, encoding);
	}

	/**
	 * Run the QueryTool in batch mode
	 * @param indexDir the index to search
	 * @param commandFile the command file to execute
	 * @throws CorruptIndexException
	 */
	public static void runBatch(File indexDir, File commandFile) throws CorruptIndexException {
		try {
			run(indexDir, commandFile, Charset.defaultCharset().name());
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Run the QueryTool in interactive mode
	 * @param indexDir the index to search
	 * @param encoding the output encoding
	 * @throws UnsupportedEncodingException
	 * @throws CorruptIndexException
	 */
	public static void runInteractive(File indexDir, String encoding) throws UnsupportedEncodingException, CorruptIndexException {
		run(indexDir, null, encoding);
	}

	/**
	 * Run the QueryTool in interactive mode
	 * @param indexDir the index to search
	 * @throws CorruptIndexException
	 */
	public static void runInteractive(File indexDir) throws CorruptIndexException {
		try {
			run(indexDir, null, Charset.defaultCharset().name());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Run the query tool.
	 * @param indexDir the index to query
	 * @param inputFile if specified, run in batch mode. If null, run in interactive mode
	 * @param encoding the output encoding to use
	 * @throws UnsupportedEncodingException
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	private static void run(File indexDir, File inputFile, String encoding)
			throws UnsupportedEncodingException, CorruptIndexException {
		if (!indexDir.exists() || !indexDir.isDirectory()) {
			System.err.println("Index dir " + indexDir.getPath() + " doesn't exist.");
			return;
		}

		// Use correct output encoding
		PrintWriter out, err;
		try {
			// Yes
			out = new PrintWriter(new OutputStreamWriter(System.out, encoding), true);
			err = new PrintWriter(new OutputStreamWriter(System.err, encoding), true);
			out.println("Using output encoding " + encoding + "\n");
		} catch (UnsupportedEncodingException e) {
			// Nope; fall back to default
			System.err.println("Unknown encoding " + encoding + "; using default");
			out = new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true);
			err = new PrintWriter(new OutputStreamWriter(System.err, Charset.defaultCharset()), true);
		}

		BufferedReader in;
		if (inputFile == null) {
			// No input file specified; use stdin
			in = new BufferedReader(new InputStreamReader(System.in, encoding));
		} else {
			// Open input file
			in = FileUtil.openForReading(inputFile, INPUT_FILE_ENCODING);
			batchMode = true;
		}
		try {
			QueryTool c = new QueryTool(indexDir, in, out, err);
			c.commandProcessor();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static void usage() {
		System.err.println(
			"Usage: " + QueryTool.class.getName() + " [options] <indexDir>\n" +
			"\n" +
			"Options:\n" +
			"-e <encoding>   Specify what output encoding to use\n" +
			"-f <file>       Execute batch commands from file, print performance\n" +
			"                info and exit\n" +
			"\n" +
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
	 * @param searcher the searcher object (our index)
	 * @param in where to read commands from
	 * @param out where to write output to
	 * @param err where to write errors to
	 * @throws CorruptIndexException
	 */
	public QueryTool(Searcher searcher, BufferedReader in, PrintWriter out, PrintWriter err) throws CorruptIndexException {
		this.searcher = searcher;
		this.contentsField = searcher.getMainContentsFieldName();
		shouldCloseSearcher = false; // caller is responsible

		this.in = in;
		this.out = out;
		this.err = err;

		if (in == null) {
			webSafeOperationOnly = true; // don't allow file operations in web mode
			this.err = out; // send errors to the same output writer in web mode
		} else {
			printProgramHead();
		}

		contextSize = searcher.hitsSettings().contextSize();

		wordLists.put("test", Arrays.asList("de", "het", "een", "over", "aan"));
	}

	/**
	 * Construct the query tool object.
	 *
	 * @param indexDir directory our index is in
	 * @param in where to read commands from
	 * @param out where to write output to
	 * @param err where to write errors to
	 * @throws CorruptIndexException
	 */
	public QueryTool(File indexDir, BufferedReader in, PrintWriter out, PrintWriter err) throws CorruptIndexException {
		this.in = in;
		this.out = out;
		this.err = err;

		if (in != null) {
			printProgramHead();
			try {
				outprintln("Opening index " + indexDir.getCanonicalPath() + "...");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		// Create the BlackLab searcher object
		try {
			searcher = Searcher.open(indexDir);
			contentsField = searcher.getMainContentsFieldName();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (in == null) {
			webSafeOperationOnly = true; // don't allow file operations in web mode
			this.err = out; // send errors to the same output writer in web mode
		}

		contextSize = searcher.hitsSettings().contextSize();

		wordLists.put("test", Arrays.asList("de", "het", "een", "over", "aan"));
	}

	/**
	 * Construct the query tool object.
	 *
	 * @param indexDir directory our index is in
	 * @param out the output writer to use
	 * @param err where to write errors to
	 * @throws CorruptIndexException
	 */
	public QueryTool(File indexDir, PrintWriter out, PrintWriter err) throws CorruptIndexException {
		this(indexDir, null, out, err);
	}

	/**
	 * Switch to a different Searcher.
	 * @param searcher the new Searcher to use
	 */
	public void setSearcher(Searcher searcher) {
		if (shouldCloseSearcher)
			searcher.close();
		this.searcher = searcher;
		contentsField = searcher.getMainContentsFieldName();
		shouldCloseSearcher = false; // caller is responsible

		// Reset results
		hits = null;
		groups = null;
		collocations = null;
	}

	/**
	 * Parse and execute commands and queries.
	 */
	public void commandProcessor() {
		printHelp();

		while (true) {
			Parser parser = parsers.get(currentParserIndex);
			String prompt = parser.getPrompt() + "> ";
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

	int parseInt(String str, int min) {
		try {
			int n = Integer.parseInt(str);
			if (min >= 0 && n < min)
				return min;
			return n;
		} catch (NumberFormatException e) {
			return min;
		}
	}

	public void processCommand(String fullCmd) {
		fullCmd = fullCmd.trim();
		if (fullCmd.length() > 0 && fullCmd.charAt(0) == '#') // comment (batch mode)
			return;

		// See if we want to loop a command
		if (!webSafeOperationOnly && fullCmd.startsWith("repeat ")) {
			fullCmd = fullCmd.substring(7);
			Pattern p = Pattern.compile("^\\d+\\s");
			Matcher m = p.matcher(fullCmd);
			if (m.find()) {
				String strNum = m.group();
				fullCmd = fullCmd.substring(strNum.length());
				int repCount = parseInt(strNum.trim(), 1);
				outprint("Repeating " + repCount + " times: " + fullCmd);
				for (int i = 0; i < repCount; i++) {
					processCommand(fullCmd);
				}
			} else {
				errprintln("Repeat command should have a repetition count.");
			}
			return;
		}

		// In batch mode, we can use the chain operator (&&) to
		// time several commands together. See if we're chaining
		// commands here.
		String cmd, restCommand = null;
		int commandSeparatorIndex = fullCmd.indexOf("&&");
		if (commandSeparatorIndex >= 0) {
			cmd = fullCmd.substring(0, commandSeparatorIndex).trim();
			restCommand = fullCmd.substring(commandSeparatorIndex + 2).trim();
		} else {
			cmd = fullCmd;
		}

		String lcased = cmd.toLowerCase();
		if (lcased.length() > 0) {
			if (lcased.equals("clear") || lcased.equals("reset")) {
				hits = null;
				docs = null;
				groups = null;
				collocations = null;
				filterQuery = null;
				showSetting = ShowSetting.HITS;
				outprintln("Query and results cleared.");
			} else if (lcased.equals("prev") || lcased.equals("p")) {
				prevPage();
			} else if (lcased.equals("next") || lcased.equals("n")) {
				nextPage();
			} else if (lcased.startsWith("page ")) {
				showPage(parseInt(lcased.substring(5), 1) - 1);
			} else if (lcased.startsWith("pagesize ")) {
				resultsPerPage = parseInt(lcased.substring(9), 1);
				firstResult = 0;
				showResultsPage();
			} else if (lcased.startsWith("context ")) {
				contextSize = parseInt(lcased.substring(8), 0);
				if (hits != null && hits.settings().contextSize() != contextSize) {
					hits.settings().setContextSize(contextSize);
					collocations = null;
				}
				showResultsPage();
			} else if (lcased.startsWith("snippet ")) {
				int hitId = parseInt(lcased.substring(8), 1) - 1;
				Hits currentHitSet = getCurrentHitSet();
				if (hitId >= currentHitSet.size()) {
					errprintln("Hit number out of range.");
				} else {
					Hit h = currentHitSet.get(hitId);
					Concordance conc = hits.getConcordance(h, snippetSize);
					String[] concParts;
					if (stripXML)
						concParts = conc.partsNoXml();
					else
						concParts = conc.parts();
					outprintln("\n" + StringUtil.wrapToString(concParts[0] + "[" + concParts[1] + "]" + concParts[2], 80));
				}
			} else if (lcased.startsWith("highlight ")) {
				int hitId = parseInt(lcased.substring(8), 1) - 1;
				Hits currentHitSet = getCurrentHitSet();
				if (currentHitSet == null || hitId >= currentHitSet.size()) {
					errprintln("Hit number out of range.");
				} else {
					int docid = currentHitSet.get(hitId).doc;
					Hits hitsInDoc = hits.getHitsInDoc(docid);
					outprintln(StringUtil.wrapToString(searcher.highlightContent(docid, hitsInDoc), 80));
				}
			} else if (lcased.startsWith("snippetsize ")) {
				snippetSize = parseInt(lcased.substring(12), 0);
				outprintln("Snippets will show " + snippetSize + " words of context.");
			} else if (lcased.startsWith("doc ")) {
				int docId = parseInt(lcased.substring(4), 0);
				showMetadata(docId);
			} else if (lcased.startsWith("filter ") || lcased.equals("filter")) {
				if (cmd.length() <= 7) {
					filterQuery = null; // clear filter
					outprintln("Filter cleared.");
				} else {
					String filterExpr = cmd.substring(7);
					try {
						filterQuery = LuceneUtil.parseLuceneQuery(filterExpr, searcher.getAnalyzer(), "title");
						outprintln("Filter created: " + filterQuery);
						if (verbose)
							outprintln(filterQuery.getClass().getName());
					} catch (org.apache.lucene.queryparser.classic.ParseException e) {
						errprintln("Error parsing filter query: " + e.getMessage());
					}
				}
				docs = null;
			} else if (lcased.startsWith("concfi ")) {
				String v = lcased.substring(7);
				boolean b = false;
				if (v.equals("on") || v.equals("yes") || v.equals("true"))
					b = true;
				searcher.hitsSettings().setConcordanceType(b ? ConcordanceType.FORWARD_INDEX : ConcordanceType.CONTENT_STORE);
			} else if (lcased.startsWith("stripxml ")) {
				String v = lcased.substring(9);
				boolean b = false;
				if (v.equals("on") || v.equals("yes") || v.equals("true"))
					b = true;
				stripXML = b;
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
				outprintln("Search defaults to "
						+ (caseSensitive ? "case-sensitive" : "case-insensitive") + " and "
						+ (diacSensitive ? "diacritics-sensitive" : "diacritics-insensitive"));
			} else if (lcased.startsWith("doctitle ")) {
				String v = lcased.substring(9);
				showDocTitle = v.equals("on") || v.equals("yes") || v.equals("true");
				System.out.println("Show document titles: " + (showDocTitle ? "ON" : "OFF"));
			} else if (lcased.equals("struct") || lcased.equals("structure")) {
				showIndexStructure();
			} else if (lcased.startsWith("sort by ")) {
				sortBy(cmd.substring(8));
			} else if (lcased.startsWith("sort ")) {
				sortBy(cmd.substring(5));
			} else if (lcased.startsWith("group by ")) {
				String[] parts = lcased.substring(9).split("\\s+", 2);
				groupBy(parts[0], parts.length > 1 ? parts[1] : null);
			} else if (lcased.startsWith("group ")) {
				if (lcased.substring(6).matches("\\d+")) {
					firstResult = 0; // reset for paging through group
					changeShowSettings(lcased);
				}
				else {
					String[] parts = lcased.substring(6).split("\\s+", 2);
					groupBy(parts[0], parts.length > 1 ? parts[1] : null);
				}
			} else if (lcased.equals("groups") || lcased.equals("hits") || lcased.equals("docs")
					|| lcased.startsWith("colloc") || lcased.startsWith("group ")) {
				changeShowSettings(cmd);
			} else if (lcased.equals("switch") || lcased.equals("sw")) {
				currentParserIndex++;
				if (currentParserIndex >= parsers.size())
					currentParserIndex = 0;
				outprintln("Switching to " + parsers.get(currentParserIndex).getName() + ".\n");
				printQueryHelp();
			} else if (lcased.equals("help") || lcased.equals("?")) {
				printHelp();
			} else if (!webSafeOperationOnly && lcased.startsWith("sleep")) {
				try {
					Thread.sleep((int)(Float.parseFloat(lcased.substring(6)) * 1000));
				} catch (NumberFormatException e1) {
					errprintln("Sleep takes a float, the number of seconds to sleep");
				} catch (InterruptedException e) {
					// OK
				}
			} else if (!webSafeOperationOnly && lcased.startsWith("wordlist")) {
				if (cmd.length() == 8) {
					// Show loaded wordlists
					outprintln("Available word lists:");
					for (String listName: wordLists.keySet()) {
						outprintln(" " + listName);
					}
				} else {
					// Load new wordlist or display existing wordlist
					String[] parts = cmd.substring(9).trim().split("\\s+", 2);
					String name = "word", fn = parts[0];
					if (parts.length == 2) {
						name = parts[1];
					}
					File f = new File(fn);
					if (f.exists()) {
						// Second arg is a file
						wordLists.put(name, FileUtil.readLines(f));
						outprintln("Loaded word list '" + name + "'");
					} else {
						if (wordLists.containsKey(fn)) {
							// Display existing wordlist
							for (String word: wordLists.get(fn)) {
								outprintln(" " + word);
							}
						} else {
							errprintln("File " + fn + " not found.");
						}
					}
				}
			} else if (lcased.equals("warmup")) {
				errprintln("Warming up the forward indices is deprecated (done automatically at startup)");
			} else if (lcased.startsWith("showconc ")) {
				String v = lcased.substring(9);
				showConc = v.equals("on") || v.equals("yes") || v.equals("true");
				System.out.println("Show concordances: " + (showConc ? "ON" : "OFF"));
			} else if (lcased.startsWith("verbose ")) {
				String v = lcased.substring(8);
				verbose = v.equals("on") || v.equals("yes") || v.equals("true");
				outprintln("Verbose: " + (verbose ? "ON" : "OFF"));
			} else if (lcased.startsWith("total ")) {
				String v = lcased.substring(6);
				determineTotalNumberOfHits = v.equals("on") || v.equals("yes") || v.equals("true");
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
		if (docId >= searcher.maxDoc()) {
			outprintln("Document " + docId + " doesn't exist.");
			return;
		}
		if (searcher.isDeleted(docId)) {
			outprintln("Document " + docId + " was deleted.");
			return;
		}
		Document doc = searcher.document(docId);
		Map<String, String> metadata = new TreeMap<>(); // sort by key
		for (IndexableField f: doc.getFields()) {
			metadata.put(f.name(), f.stringValue());
		}
		for (Map.Entry<String, String> e: metadata.entrySet()) {
			outprintln(e.getKey() + ": " + e.getValue());
		}
	}

	private void showIndexStructure() {
		IndexStructure s = searcher.getIndexStructure();
		outprintln("INDEX STRUCTURE FOR INDEX " + searcher.getIndexName() + "\n");
		s.print(out);
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

				outprintln("Command line editing enabled.");
			} catch (ClassNotFoundException e) {
				// Can't init JLine; too bad, fall back to stdin
				outprintln("Command line editing not available; to enable, place jline jar in classpath.");
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
		String langAvail = "CorpusQL, Lucene, ContextQL (EXPERIMENTAL)";

		outprintln("Control commands:");
		outprintln("  sw(itch)                           # Switch languages");
		outprintln("                                     # (" + langAvail + ")");
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
		outprintln("  struct                             # Show index structure");
		outprintln("  help                               # This message");

		if (!webSafeOperationOnly) {
			outprintln("  exit                               # Exit program");

			outprintln("\nBatch testing commands (start in batch mode with -f <commandfile>):");
			outprintln("  wordlist <file> <listname>         # Load a list of words");
			outprintln("  @@<listname>                       # Substitute a random word from list (use in query)");
			outprintln("  repeat <n> <query>                 # Repeat a query n times (with different random words)");
			outprintln("  sleep <f>                          # Sleep a number of seconds");
		}
		outprintln("");

		printQueryHelp();
	}

	/**
	 * Print some examples of the currently selected query language.
	 */
	private void printQueryHelp() {
		parsers.get(currentParserIndex).printHelp();
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

			// See if we want to choose any random words
			if (query.contains("@@")) {
				StringBuffer resultString = new StringBuffer();
				Pattern regex = Pattern.compile("@@[A-Za-z0-9_\\-]+");
				Matcher regexMatcher = regex.matcher(query);
				while (regexMatcher.find()) {
					// You can vary the replacement text for each match on-the-fly
					String wordListName = regexMatcher.group().substring(2);
					List<String> list = wordLists.get(wordListName);
					if (list == null) {
						errprintln("Word list '" + wordListName + "' not found!");
						return;
					}
					int randomIndex = (int)(Math.random() * list.size());
					regexMatcher.appendReplacement(resultString, list.get(randomIndex));
				}
				regexMatcher.appendTail(resultString);
				query = resultString.toString();
			}

			Parser parser = parsers.get(currentParserIndex);
			TextPattern pattern = parser.parse(query);
			if (pattern == null) {
				errprintln("No query to execute.");
				return;
			}
			//pattern = pattern.rewrite();
			if (verbose)
				outprintln("TextPattern: " + pattern.toString());

			// If the query included filter clauses, use those. Otherwise use the global filter, if any.
			Query filterForThisQuery = parser.getIncludedFilterQuery();
			if (filterForThisQuery == null)
				filterForThisQuery = filterQuery;
			Query filter = filterForThisQuery == null ? null : filterForThisQuery;

			// Execute search
			BLSpanQuery spanQuery = searcher.createSpanQuery(pattern, contentsField, filter);
			if (verbose)
				outprintln("SpanQuery: " + spanQuery.toString(contentsField));
			hits = searcher.find(spanQuery);
			docs = null;
			groups = null;
			collocations = null;
			showWhichGroup = -1;
			showSetting = ShowSetting.HITS;
			firstResult = 0;
			showResultsPage();
			reportTime(t.elapsed());
			if (determineTotalNumberOfHits)
				statInfo = "" + hits.size();
			else
				statInfo = "?";
			commandWasQuery = true;
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
			errprintln("Sorting collocations not supported");
			break;
		case GROUPS:
			sortGroups(sortBy.toLowerCase());
			break;
		default:
			String[] parts = sortBy.split("\\s+", 2);
			String sortByPart = parts[0];
			String propPart = parts.length > 1 ? parts[1] : null;
			sortHits(sortByPart, propPart);
			break;
		}
	}

	/** Desired context size */
	private int contextSize;

	/** Are we responsible for closing the Searcher? */
	private boolean shouldCloseSearcher = true;

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
		if (sortBy.equalsIgnoreCase("doc"))
			crit = new HitPropertyDocumentId(hitsToSort);
		else {
			if (sortBy.equalsIgnoreCase("match") || sortBy.equalsIgnoreCase("word"))
				crit = new HitPropertyHitText(hitsToSort, contentsField, property);
			else if (sortBy.equalsIgnoreCase("left"))
				crit = new HitPropertyLeftContext(hitsToSort, contentsField, property);
			else if (sortBy.equalsIgnoreCase("right"))
				crit = new HitPropertyRightContext(hitsToSort, contentsField, property);
			else if (sortBy.equalsIgnoreCase("lempos")) {
				HitProperty p1 = new HitPropertyHitText(hitsToSort, contentsField, "lemma");
				HitProperty p2 = new HitPropertyHitText(hitsToSort, contentsField, "pos");
				crit = new HitPropertyMultiple(p1, p2);
			} else if (searcher.getIndexStructure().getMetadataFields().contains(sortBy)) {
				crit = new HitPropertyDocumentStoredField(hitsToSort, sortBy);
			}

		}
		if (crit == null) {
			errprintln("Invalid hit sort criterium: " + sortBy
					+ " (valid are: match, left, right, doc, <metadatafield>)");
		} else {
			hitsToSort = hitsToSort.sortedBy(crit);
			firstResult = 0;
			showResultsPage();
			if (property == null)
				property = "(default)";
			reportTime(t.elapsed());
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

		if (!groupBy.equals("hit") && !groupBy.equals("word") && !groupBy.equals("match") && !groupBy.equals("left") && !groupBy.equals("right")) {
			// Assume we want to group by matched text if we don't specify it explicitly.
			property = groupBy;
			groupBy = "match";
		}

		// Group results
		HitProperty crit = null;
		try {
			if (groupBy.equals("word") || groupBy.equals("match") || groupBy.equals("hit"))
				crit = new HitPropertyHitText(hits, contentsField, property);
			else if (groupBy.startsWith("left"))
				crit = new HitPropertyWordLeft(hits, contentsField, property);
			else if (groupBy.startsWith("right"))
				crit = new HitPropertyWordRight(hits, contentsField, property);
			else if (groupBy.equals("test")) {
				HitProperty p1 = new HitPropertyHitText(hits, contentsField, "lemma");
				HitProperty p2 = new HitPropertyHitText(hits, contentsField, "type");
				crit = new HitPropertyMultiple(p1, p2);
			}
		} catch (Exception e) {
			errprintln("Unknown property: " + property);
			return;
		}
		if (crit == null) {
			errprintln("Unknown criterium: " + groupBy);
			return;
		}
		groups = hits.groupedBy(crit);
		showSetting = ShowSetting.GROUPS;
		sortGroups("size");
		if (property == null)
			property = "(default)";
		reportTime(t.elapsed());
	}

	/**
	 * Switch between showing all hits, groups, and the hits in one group.
	 * @param showWhat what type of results to show
	 */
	private void changeShowSettings(String showWhat) {
		if (showWhat.equals("hits")) {
			showSetting = ShowSetting.HITS;
			showWhichGroup = -1;
		} else if (showWhat.equals("docs")) {
			showSetting = ShowSetting.DOCS;
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
			showWhichGroup = parseInt(showWhat.substring(6), 1) - 1;
			if (showWhichGroup < 0 || showWhichGroup >= groups.numberOfGroups()) {
				errprintln("Group doesn't exist");
				showWhichGroup = -1;
			} else
				showSetting = ShowSetting.HITS; // Show hits in group, not all the groups
		}
		showResultsPage();
	}

	/**
	 * Report how long an operation took
	 * @param time time to report
	 */
	private void reportTime(long time) {
		outprintln(describeInterval(time) + " elapsed");
	}

	private String describeInterval(long time1) {
		if (timeDisplayHumanFriendly)
			return TimeUtil.describeInterval(time1);
		return time1 + " ms";
	}

	/**
	 * Close the Searcher object.
	 */
	private void cleanup() {
		if (shouldCloseSearcher)
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
		case DOCS:
			showDocsPage();
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
			String fieldName = hits.settings().concordanceField();
			if (collocProperty == null) {
				ComplexFieldDesc cf = searcher.getIndexStructure().getComplexFieldDesc(fieldName);
				collocProperty = cf.getMainProperty().getName();
			}

			collocations = hits.getCollocations(collocProperty,
					searcher.getDefaultExecutionContext(fieldName));
			collocations.sort();
		}

		int i = 0;
		for (TermFrequency coll : collocations) {
			if (i >= firstResult && i < firstResult + resultsPerPage) {
				int j = i - firstResult + 1;
				outprintln(String.format("%4d %7d %s", j, coll.frequency, coll.term));
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
		List<HitGroup> listGroups = groups.getGroups();
		int i;
		for (i = firstResult; i < groups.numberOfGroups() && i < firstResult + resultsPerPage; i++) {
			HitGroup g = listGroups.get(i);
			outprintln(String.format("%4d. %5d %s", i + 1, g.size(), g.getIdentity().toString()));
		}

		// Summarize
		String msg = groups.numberOfGroups() + " groups";
		outprintln(msg);
	}

	private void showDocsPage() {
		if (docs == null) {
			Hits currentHitSet = getCurrentHitSet();
			if (currentHitSet != null)
				docs = currentHitSet.perDocResults();
			else if (filterQuery != null) {
				docs = searcher.queryDocuments(filterQuery);
			} else {
				System.out.println("No documents to show (set filterquery or search for hits first)");
				return;
			}
		}

		// Limit results to the current page
		DocResultsWindow window = docs.window(firstResult, resultsPerPage);

		// Compile hits display info and calculate necessary width of left context column
		String titleField = searcher.getIndexStructure().titleField();
		int hitNr = window.first() + 1;
		for (DocResult result: window) {
			int id = result.getDocId();
			Document d = searcher.document(id);
			String title = d.get(titleField);
			if (title == null)
				title = "(doc #" + id + ", no " + titleField + " given)";
			else
				title = title + " (doc #" + id + ")";
			outprintf("%4d. %s\n", hitNr, title);
			hitNr++;
		}

		// Summarize
		String msg;
		if (determineTotalNumberOfHits) {
			msg = docs.totalSize() + " docs";
		} else {
			msg = docs.size() + " docs";
		}
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

			public Map<String, Span> capturedGroups;

			public HitToShow(int doc, String left, String hitText, String right, Map<String, Span> capturedGroups) {
				super();
				this.doc = doc;
				this.left = left;
				this.hitText = hitText;
				this.right = right;
				this.capturedGroups = capturedGroups;
			}
		}

		Hits hitsToShow = getCurrentHitSet();
		if (hitsToShow == null)
			return; // nothing to show

		// Limit results to the current page
		HitsWindow window = hitsToShow.window(firstResult, resultsPerPage);

		// Compile hits display info and calculate necessary width of left context column
		List<HitToShow> toShow = new ArrayList<>();
		window.settings().setContextSize(contextSize); // number of words around hit
		int leftContextMaxSize = 10; // number of characters to reserve on screen for left context
		for (Hit hit : window) {
			Concordance conc = window.getConcordance(hit);

			// Filter out the XML tags
			String left, hitText, right;
			left = stripXML ? XmlUtil.xmlToPlainText(conc.left()) : conc.left();
			hitText = stripXML ? XmlUtil.xmlToPlainText(conc.match()) : conc.match();
			right = stripXML ? XmlUtil.xmlToPlainText(conc.right()) : conc.right();

			toShow.add(new HitToShow(hit.doc, left, hitText, right, window.getCapturedGroupMap(hit)));
			if (leftContextMaxSize < left.length())
				leftContextMaxSize = left.length();
		}

		// Display hits
		String format = "%4d. [%04d] %" + leftContextMaxSize + "s[%s]%s\n";
		if (showDocTitle)
			format = "%4d. %" + leftContextMaxSize + "s[%s]%s\n";
		int currentDoc = -1;
		String titleField = searcher.getIndexStructure().titleField();
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
			if (hit.capturedGroups != null)
				outprintln("CAP: " + hit.capturedGroups.toString());
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
					msg = hitsInDocs + " retrieved, more than " + hitsToShow.totalSize() + " (" + hitsToShow.totalNumberOfDocs() + " docs) total";
				} else {
					msg = hitsInDocs + " retrieved, " + hitsToShow.totalSize() + " (" + hitsToShow.totalNumberOfDocs() + " docs) total";
				}
			} else {
				msg = hitsInDocs;
			}
		}
		outprintln(msg);
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
			HitGroup g = groups.getGroups().get(showWhichGroup);
			hitsToShow = g.getHits();
		}
		return hitsToShow;
	}

	public void outprintln(String str) {
		if (!batchMode)
			out.println(str);
	}

	public void outprint(String str) {
		if (!batchMode)
			out.print(str);
	}

	public void outprintf(String str, Object... args) {
		if (!batchMode)
			out.printf(str, args);
	}

	public void errprintln(String str) {
		if (err == null)
			out.println(str);
		else
			err.println(str);
	}

	public void statprintln(String str) {
		if (batchMode)
			out.println(str);
	}

}
