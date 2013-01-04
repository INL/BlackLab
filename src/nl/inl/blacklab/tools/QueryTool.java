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
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.indexers.alto.AltoUtils;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.TokenMgrError;
import nl.inl.blacklab.queryParser.lucene.LuceneQueryParser;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.grouping.GroupProperty;
import nl.inl.blacklab.search.grouping.GroupPropertyIdentity;
import nl.inl.blacklab.search.grouping.GroupPropertySize;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyDocumentId;
import nl.inl.blacklab.search.grouping.HitPropertyHitText;
import nl.inl.blacklab.search.grouping.HitPropertyLeftContext;
import nl.inl.blacklab.search.grouping.HitPropertyRightContext;
import nl.inl.blacklab.search.grouping.HitPropertyWordLeft;
import nl.inl.blacklab.search.grouping.HitPropertyWordRight;
import nl.inl.blacklab.search.grouping.RandomAccessGroup;
import nl.inl.blacklab.search.grouping.ResultsGrouper;
import nl.inl.util.Timer;
import nl.inl.util.XmlUtil;

import org.apache.log4j.BasicConfigurator;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.search.Filter;
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

	/**
	 * Our BlackLab Searcher object.
	 */
	private Searcher searcher;

	/**
	 * Standard input.
	 */
	private BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

	/**
	 * The hits that are the result of our query.
	 */
	private Hits hits = null;

	/**
	 * The first hit or group to show on the current results page.
	 */
	private int firstResult;

	/**
	 * Number of hits or groups to show per results page.
	 */
	private int resultsPerPage = 20;

	/**
	 * The groups, or null if we haven't grouped our results.
	 */
	private ResultsGrouper groups = null;

	/**
	 * If true, we're showing groups. If false, we're showing hits.
	 */
	private boolean showGroups = false;

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
				return parser.query();
			} catch (Exception e) {
				throw new ParseException(e);
			}
		}

		@Override
		public void printHelp() {
			out.println("Corpus Query Language examples:");
			out.println("  \"stad\" | \"dorp\"                  # Find the word \"stad\" or the word \"dorp\"");
			out.println("  \"de\" \"sta.*\"                     # Find \"de\" followed by a word starting with \"sta\"");
			out.println("  [hw=\"zijn\" & pos=\"d.*\"]          # Find forms of the headword \"zijn\" as a posessive pronoun");
			out.println("  [hw=\"zijn\"] [hw=\"blijven\"]       # Find a form of \"zijn\" followed by a form of \"blijven\"");
			out.println("  \"der.*\"{2,}                      # Find two or more successive words starting with \"der\"");
			out.println("  [pos=\"a.*\"]+ \"man\"               # Find adjectives applied to \"man\"");
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
				System.err.println("method not found");
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				throw new RuntimeException(e);
			}

			/*
			 * try { // TODO: simplify this, and/or abstract gritty details into a convenience
			 * method CQLParser parser = new CQLParser(); CQLNode cqlroot = parser.parse(query);
			 * String xml = cqlroot.toXCQL(0); DocumentBuilderFactory f =
			 * DocumentBuilderFactory.newInstance(); f.setIgnoringElementContentWhitespace(true);
			 * DocumentBuilder b = f.newDocumentBuilder(); Document d = b.parse(new InputSource(new
			 * StringReader(xml))); XCQLParserNode root = new XCQLParserNode(); root.initialise(d);
			 * return root.getQuery(); } catch (CQLParseException e) { // TODO: Streamline our
			 * exceptions throw new ParseException(e.getMessage()); } catch (IOException e) { throw
			 * new RuntimeException(e); } catch (ParserConfigurationException e) { throw new
			 * RuntimeException(e); } catch (SAXException e) { throw new RuntimeException(e); }
			 */
		}

		@Override
		public void printHelp() {
			out.println("Contextual Query Language examples:");
			out.println("  stad or dorp            # Find the word \"stad\" or the word \"dorp\"");
			out.println("  \"de sta*\"               # Find \"de\" followed by a word starting with \"sta\"");
			out.println("  hw=zijn and pos=d*      # Find forms of the headword \"zijn\" as a posessive pronoun");
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
				LuceneQueryParser parser = new LuceneQueryParser(Version.LUCENE_36, "contents",
						new WhitespaceAnalyzer(Version.LUCENE_36));
				return parser.parse(query);
			} catch (Exception e) {
				throw new ParseException(e);
			}
		}

		@Override
		public void printHelp() {
			out.println("Lucene Query Language examples:");
			out.println("  stad dorp                  # Find the word \"stad\" or the word \"dorp\"");
			out.println("  \"de stad\"                  # Find the word \"de\" followed by the word \"stad\"");
			out.println("  +stad -dorp                # Find documents containing \"stad\" but not \"dorp\"");
		}

		@Override
		public Parser nextParser() {
			return new ParserCorpusQl();
		}

	}

	/** The current command parser object */
	private Parser currentParser = new ParserCorpusQl();

	/**
	 * The main program.
	 *
	 * @param args
	 *            commandline arguments
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 1 || args.length > 2) {
			out.println("Usage: " + QueryTool.class.getName() + " <indexDir> [<encoding>]");
			return;
		}
		File indexDir = new File(args[0]);
		if (!indexDir.exists()) {
			out.println("Index dir " + indexDir.getPath() + " doesn't exist.");
			return;
		}

		// See if we can offer ContextQL or not (if the class is on the classpath)
		determineContextQlAvailable();

		// Special case for ALTO (to be generalized)
		String propIsAlto = System.getProperty("IS_ALTO");
		IS_ALTO = propIsAlto == null ? false : propIsAlto.equalsIgnoreCase("true");

		BasicConfigurator.configure(); //new NullAppender()); // ignore logging for now

		// Change output encoding?
		if (args.length == 2) {
			try {
				// Yes
				out = new PrintStream(System.out, true, args[1]);
				System.out.println("Using output encoding " + args[1] + "\n");
			} catch (UnsupportedEncodingException e) {
				// Nope; fall back to default
				System.err.println("Unknown encoding " + args[1] + "; using default");
				out = System.out;
			}
		}

		QueryTool c = new QueryTool(indexDir);
		c.commandProcessor();
	}

	/**
	 * Construct the query tool object.
	 *
	 * @param indexDir
	 *            directory our index is in
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public QueryTool(File indexDir) throws CorruptIndexException, IOException {
		printProgramHead();
		System.out.print("Opening index " + indexDir + "... ");

		// Create the BlackLab searcher object
		searcher = new Searcher(indexDir);
		System.out.println("Done.\n");
	}

	/**
	 * Parse and execute commands and queries.
	 */
	public void commandProcessor() {
		printHelp();

		while (true) {
			String prompt = currentParser.getPrompt() + "> ";
			String expr;
			try {
				expr = readCommand(prompt);
			} catch (IOException e1) {
				throw new RuntimeException(e1);
			}
			if (expr == null || expr.trim().equals("exit")) {
				break;
			}
			expr = expr.trim().toLowerCase();
			if (expr.length() == 0)
				continue;
			if (expr.equals("prev") || expr.equals("p")) {
				prevPage();
			} else if (expr.equals("next") || expr.equals("n")) {
				nextPage();
			} else if (expr.startsWith("pagesize ")) {
				resultsPerPage = Integer.parseInt(expr.substring(9));
				firstResult = 0;
				showResultsPage();
			} else if (expr.startsWith("sort by ")) {
				sortBy(expr.substring(8));
			} else if (expr.startsWith("sort ")) {
				sortBy(expr.substring(5));
			} else if (expr.startsWith("group by ")) {
				String[] parts = expr.substring(9).split("\\s+", 2);
				groupBy(parts[0], parts.length > 1 ? parts[1] : null);
			} else if (expr.startsWith("group ")) {
				if (expr.substring(6).matches("\\d+"))
					changeShowSettings(expr);
				else {
					String[] parts = expr.substring(6).split("\\s+", 2);
					groupBy(parts[0], parts.length > 1 ? parts[1] : null);
				}
			} else if (expr.equals("groups") || expr.equals("hits") || expr.startsWith("group ")) {
				changeShowSettings(expr);
			} else if (expr.equals("switch") || expr.equals("sw")) {
				currentParser = currentParser.nextParser();
				out.println("Switching to " + currentParser.getName() + ".\n");
				printQueryHelp();
			} else if (expr.equals("help") || expr.equals("?")) {
				printHelp();
			} else {
				// Not a command; assume it's a query
				parseAndExecute(expr);
			}
			try {
				Thread.sleep(100); // Give Eclipse console time to show stderr output
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		cleanup();
	}

	/** If JLine is available, this holds the ConsoleReader object */
	Object jlineConsoleReader;

	/** If JLine is available, this holds the readLine() method */
	Method jlineReadLineMethod;

	/** Did we check if JLine is available? */
	boolean jlineChecked = false;

	private String readCommand(String prompt) throws IOException {
		if (jlineConsoleReader == null && !jlineChecked) {
			jlineChecked = true;
			try {
				Class<?> c = Class.forName("jline.ConsoleReader");
				jlineConsoleReader = c.newInstance();

				// Disable bell
				c.getMethod("setBellEnabled", boolean.class).invoke(jlineConsoleReader, false);

				// Fetch and store the readLine method
				jlineReadLineMethod = c.getMethod("readLine", String.class);

				System.err.println("Command line editing enabled.");
			} catch (ClassNotFoundException e) {
				// Can't init JLine; too bad, fall back to stdin
				System.err.println("Command line editing not available; to enable, place jline jar in classpath.");
			} catch (Exception e) {
				throw new RuntimeException("Could not init JLine console reader", e);
			}
		}

		if (jlineConsoleReader != null) {
			try {
				return (String)jlineReadLineMethod.invoke(jlineConsoleReader, prompt);
			} catch (Exception e) {
				throw new RuntimeException("Could not invoke JLine ConsoleReader.readLine()", e);
			}
		}

		out.print(prompt);
		out.flush();
		return stdin.readLine();
	}

	/**
	 * Print command and query help.
	 */
	private void printHelp() {
		String langAvail = "Lucene, CorpusQL" + (isContextQlAvailable ? ", ContextQL" : "");

		out.println("Control commands:");
		out.println("  sw(itch)                          # Switch languages (" + langAvail + ")");
		out.println("  p(rev) / n(ext) / pagesize <n>    # Page through results");
		out.println("  sort {match|left|right} [prop]    # Sort query results  (left = left context, etc.)");
		out.println("  group {match|left|right} [prop]   # Group query results (prop = e.g. 'word', 'lemma', 'pos')");
		out.println("  hits / groups / group <n>         # Switch between results modes");
		out.println("  help                              # This message");
		out.println("  exit                              # Exit program");
		out.println("");

		printQueryHelp();
	}

	/**
	 * Print some examples of the currently selected query language.
	 */
	private void printQueryHelp() {
		currentParser.printHelp();
		out.println("");
	}

	/**
	 * Show the program head.
	 */
	private void printProgramHead() {
		out.println("BlackLab Query Tool");
		out.println("===================");
	}

	/**
	 * Parse and execute a query in the current query format.
	 *
	 * @param query
	 *            the query
	 */
	private void parseAndExecute(String query) {
		Timer t = new Timer();
		try {
			TextPattern pattern = currentParser.parse(query);

			// Execute search
			Filter filter = null; // TODO: metadata search!
			hits = searcher.find("contents", pattern, filter);
			groups = null;
			showWhichGroup = -1;
			showGroups = false;
			firstResult = 0;
			reportTime(t);
			showResultsPage();
		} catch (TokenMgrError e) {
			// Lexical error
			System.err.println("Invalid query: " + e.getMessage());
			System.err.println("(Type 'help' for examples or see accompanying documents)");
		} catch (ParseException e) {
			// Parse error
			System.err.println("Invalid query: " + e.getMessage());
			System.err.println("(Type 'help' for examples or see accompanying documents)");
		} catch (UnsupportedOperationException e) {
			// Unimplemented part of query language used
			System.err.println("Cannot execute query; " + e.getMessage());
			System.err.println("(Type 'help' for examples or see accompanying documents)");
		}
	}

	/**
	 * Show the next page of results.
	 */
	private void nextPage() {
		if (hits != null) {
			int max = hits.size();
			if (showGroups)
				max = groups.numberOfGroups();

			// Next page
			firstResult += resultsPerPage;
			if (firstResult >= max)
				firstResult = 0;
			showResultsPage();
		}
	}

	/**
	 * Show the previous page of results.
	 */
	private void prevPage() {
		if (hits != null) {
			// Previous page
			firstResult -= resultsPerPage;
			if (firstResult < 0)
				firstResult = 0;
			showResultsPage();
		}
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

		if (showGroups) {
			sortGroups(sortBy);
		} else {
			String[] parts = sortBy.split("\\s+", 2);
			sortHits(parts[0], parts.length > 1 ? parts[1] : null);
		}
	}

	final String CONTEXT_FIELD = "contents";

	/**
	 * Sort hits by the specified property.
	 *
	 * @param sortBy
	 *            hit property to sort by
	 * @param property
	 * 			  (optional) if sortBy is a context property (say, hit text), this gives the token property to use for the context.
	 *            Example: if this is "lemma", will look at the lemma(ta) of the hit text. If this is null, uses the
	 *            "main property" (word form, usually).
	 */
	private void sortHits(String sortBy, String property) {
		Timer t = new Timer();

		Hits hitsToSort = getCurrentHitSet();

		HitProperty crit = null;
		if (sortBy.equals("doc"))
			crit = new HitPropertyDocumentId();
		else {
			if (property != null && property.equals("word"))
				property = null; // default property
			String fieldName = ComplexFieldUtil.fieldName(CONTEXT_FIELD, property);
			if (sortBy.equals("match") || sortBy.equals("word"))
				crit = new HitPropertyHitText(searcher, fieldName);
			else if (sortBy.startsWith("left"))
				crit = new HitPropertyLeftContext(searcher, fieldName);
			else if (sortBy.startsWith("right"))
				crit = new HitPropertyRightContext(searcher, fieldName);
		}
		if (crit == null) {
			out.println("Invalid hit sort criterium: " + sortBy
					+ " (valid are: match, left, right)");
		} else {
			hitsToSort.sort(crit);
			firstResult = 0;
			reportTime(t);
			showResultsPage();
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
			out.println("Invalid group sort criterium: " + sortBy
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
	 * 			  (optional) if groupBy is a context property (say, hit text), this gives the token property to use for the context.
	 *            Example: if this is "lemma", will look at the lemma(ta) of the hit text. If this is null, uses the
	 *            "main property" (word form, usually).
	 */
	private void groupBy(String groupBy, String property) {
		if (hits == null)
			return;

		Timer t = new Timer();

		// Group results
		HitProperty crit = null;
		if (property != null && property.equals("word"))
			property = null; // default property
		String fieldName = ComplexFieldUtil.fieldName(CONTEXT_FIELD, property);
		if (groupBy.equals("word") || groupBy.equals("match"))
			crit = new HitPropertyHitText(searcher, fieldName);
		else if (groupBy.startsWith("left"))
			crit = new HitPropertyWordLeft(searcher, fieldName);
		else if (groupBy.startsWith("right"))
			crit = new HitPropertyWordRight(searcher, fieldName);
		if (crit == null) {
			out.println("Unknown criterium: " + groupBy);
			return;
		}
		groups = new ResultsGrouper(hits, crit);
		showGroups = true;
		reportTime(t);
		sortGroups("size");
	}

	/**
	 * Switch between showing all hits, groups, and the hits in one group.
	 */
	private void changeShowSettings(String showWhat) {
		if (showWhat.equals("hits")) {
			showGroups = false;
			showWhichGroup = -1;
		} else if (showWhat.equals("groups") && groups != null)
			showGroups = true;
		else if (showWhat.startsWith("group ") && groups != null) {
			showWhichGroup = Integer.parseInt(showWhat.substring(6)) - 1;
			if (showWhichGroup < 0 || showWhichGroup >= groups.numberOfGroups()) {
				System.err.println("Group doesn't exist");
				showWhichGroup = -1;
			} else
				showGroups = false; // Show hits in group, not all the groups
		}
		showResultsPage();
	}

	/**
	 * If an operation took longer than 5 seconds, report the time it took.
	 *
	 * @param t
	 *            object keeping the time
	 */
	private void reportTime(Timer t) {
		//if (t.elapsed() > 5000) // don't report short intervals
		out.println(t.elapsedDescription(true) + " elapsed");
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
		if (showGroups) {
			showGroupsPage();
		} else {
			showHitsPage();
		}
	}

	/**
	 * Show the current page of hits.
	 */
	private void showHitsPage() {
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

		// Limit results to the first n
		HitsWindow window = new HitsWindow(hitsToShow, firstResult, resultsPerPage);

		// Compile hits display info and calculate necessary width of left context column
		List<HitToShow> toShow = new ArrayList<HitToShow>();
		int leftContextMaxSize = 10;
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
				left = XmlUtil.xmlToPlainText(conc.left);
				hitText = XmlUtil.xmlToPlainText(conc.hit);
				right = XmlUtil.xmlToPlainText(conc.right);
			}
			toShow.add(new HitToShow(hit.doc, left, hitText, right));
			if (leftContextMaxSize < left.length())
				leftContextMaxSize = left.length();
		}

		// Display hits
		String format = "[doc %05d] %" + leftContextMaxSize + "s[%s]%s\n";
		for (HitToShow hit : toShow) {
			out.printf(format, hit.doc, hit.left, hit.hitText, hit.right);
		}

		// Summarize
		String msg = window.size() + " hits";
		if (window.totalHits() > window.size())
			msg = (window.first() + 1) + "-" + (window.last() + 1) + " of " + window.totalHits()
					+ " hits";
		out.println(msg);
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

	/**
	 * Show the current page of group results.
	 */
	private void showGroupsPage() {
		List<RandomAccessGroup> listGroups = groups.getGroups();
		int i;
		for (i = firstResult; i < groups.numberOfGroups() && i < firstResult + resultsPerPage; i++) {
			RandomAccessGroup g = listGroups.get(i);
			out.println(String.format("%4d %5d %s", i + 1, g.size(), g.getHumanReadableIdentity()));
		}

		// Summarize
		String msg = groups.numberOfGroups() + " groups";
		if (groups.numberOfGroups() > resultsPerPage)
			msg = (firstResult + 1) + "-" + i + " of " + groups.numberOfGroups() + " groups";
		out.println(msg);
	}
}
