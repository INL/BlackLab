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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitGroupPropertySize;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyAfterHit;
import nl.inl.blacklab.resultproperty.HitPropertyBeforeHit;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.HitPropertyLeftContext;
import nl.inl.blacklab.resultproperty.HitPropertyMultiple;
import nl.inl.blacklab.resultproperty.HitPropertyRightContext;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.DocFragment;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchHits;
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

    static final Charset INPUT_FILE_ENCODING = StandardCharsets.UTF_8;
    public static final String START_MATCH = "[match]";
    public static final String END_MATCH = "[/match]";

    public static final String MATCH = "match";

    /** Our output writer. */
    public final PrintWriter out;

    /** Our error writer (if null, output errors to out as well) */
    public PrintWriter err;

    /** Was a commands file specified (using -f)?  */
    static boolean batchMode = false;

    /** Show output of commands? (useful for correctness tests, not for performance tests; see --mode)  */
    static boolean showOutput = true;

    /** Show output of commands? (useful for performance tests, not for correctness tests; see --mode)  */
    static boolean showStats = true;

    /** Show doc ids in the results? (makes results incomparable between indexes)  */
    static boolean showDocIds = true;

    /** Show match info in the results? */
    static boolean showMatchInfo = false;

    /** How many results to show per page? (default is increased for correctness testing) */
    static int defaultPageSize = 20;

    /** Should results always be sorted? Useful for correctness testing */
    static String alwaysSortBy = null;

    /** Our BlackLab index object. */
    BlackLabIndex index;

    /** The hits that are the result of our query. */
    private Hits hits = null;

    /** The docs that are the result of our query. */
    private DocResults docs = null;

    /** The groups, or null if we haven't grouped our results. */
    private HitGroups groups = null;

    /**
     * If all hits or the current group of hits have been sorted, this contains the
     * sorted hits.
     */
    private Hits sortedHits = null;

    /** The collocations, or null if we're not looking at collocations. */
    private TermFrequencyList collocations = null;

    /** What annotation to use for collocations */
    private Annotation collocAnnotation = null;

    /** The first hit or group to show on the current results page. */
    private long firstResult;

    /** Number of hits or groups to show per results page. */
    private long resultsPerPage = defaultPageSize;

    /** Show document titles between hits? */
    private boolean showDocTitle = false;

    /** Show concordances or not? (if not, just shows number of hits) */
    private boolean showConc = true;

    /** Show extra information about query being processed? */
    private boolean verbose = false;

    /** Show total number of hits (takes extra time for large sets) */
    private boolean determineTotalNumberOfHits = true;

    /**
     * If true, describes time in minutes and seconds. If false, just gives the
     * number of milliseconds.
     */
    final boolean timeDisplayHumanFriendly = false;

    /** The filter query, if any. */
    private Query filterQuery = null;

    /** We record the timings of different parts of the operation here. */
    private QueryTimings timings = new QueryTimings();

    /** What results view do we want to see? */
    enum ShowSetting {
        HITS,
        DOCS,
        GROUPS,
        COLLOC
    }

    /**
     * What results view do we want to see? (hits, groups or collocations)
     */
    private ShowSetting showSetting = ShowSetting.HITS;

    /**
     * If we're looking at hits in one group, this is the index of the group number.
     * Otherwise, this is -1.
     */
    private int showWhichGroup = -1;

    /** Lists of words read from file to choose random word from (for batch mode) */
    private final Map<String, List<String>> wordLists = new HashMap<>();

    /** Generic command parser interface */
    abstract static class Parser {
        public abstract String getPrompt();

        public abstract String getName();

        public abstract TextPattern parse(String query) throws InvalidQuery;

        /**
         * Get the filter query included in the last query, if any. Only used for
         * ContextQL.
         *
         * @return the filter query, or null if there was none
         */
        Query getIncludedFilterQuery() {
            return null;
        }

        public abstract void printHelp();
    }

    /** Parser for Corpus Query Language */
    class ParserCorpusQl extends Parser {

        @Override
        public String getPrompt() {
            return "BCQL";
        }

        @Override
        public String getName() {
            return "BlackLab Corpus Query Language";
        }

        /**
         * Parse a Corpus Query Language query to produce a TextPattern
         *
         * @param query the query
         * @return the corresponding TextPattern
         * @throws InvalidQuery on parse error
         */
        @Override
        public TextPattern parse(String query) throws InvalidQuery {
            return CorpusQueryLanguageParser.parse(query);
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

        Query includedFilterQuery;

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
         * @param query the query
         * @return the corresponding TextPattern
         * @throws InvalidQuery on parse error
         */
        @Override
        public TextPattern parse(String query) throws InvalidQuery {
            //outprintln("WARNING: SRU CQL SUPPORT IS EXPERIMENTAL, MAY NOT WORK AS INTENDED");
            CompleteQuery q = ContextualQueryLanguageParser.parse(index, query);
            includedFilterQuery = q.filter();
            return q.pattern();
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

    private final List<Parser> parsers = Arrays.asList(new ParserCorpusQl(), new ParserContextQl());

    private int currentParserIndex = 0;

    /** Where to read commands from */
    private final BufferedReader in;

    /** For stats output (batch mode), extra info (such as # hits) */
    private String statInfo;

    /** If false, command was not a query, prefix stats line with # */
    private boolean commandWasQuery;

    /** Size of larger snippet */
    private ContextSize snippetSize = ContextSize.get(50, Integer.MAX_VALUE);

    /** Strip XML tags when displaying concordances? */
    private boolean stripXML = true;

    private AnnotatedField contentsField;

    /** Types of concordances we want to show */
    private ConcordanceType concType = ConcordanceType.FORWARD_INDEX;

    /**
     * The main program.
     *
     * @param args commandline arguments
     * @throws ErrorOpeningIndex if index could not be opened
     */
    public static void main(String[] args) throws ErrorOpeningIndex {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        LogUtil.setupBasicLoggingConfig(Level.WARN);

        // Parse command line
        File indexDir = null;
        File inputFile = null;
        String encoding = Charset.defaultCharset().name();
        Boolean showStats = null; // default not overridden (default depends on batch mode or not)
        boolean verbose = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--")) {
                if (arg.equals("--mode")) {
                    if (i + 1 == args.length) {
                        System.err.println("--mode option needs argument");
                        usage();
                        return;
                    }
                    String mode = args[i + 1].toLowerCase();
                    if (mode.matches("c(orrectness)?")) {
                        // Correctness testing: we want results, no timing and larger pagesize
                        showOutput = true;
                        showStats = false;
                        defaultPageSize = 1000;
                        alwaysSortBy = "after:word:s,hitposition"; // for reproducibility
                        showDocIds = false; // doc ids are randomly assigned
                        showMatchInfo = false; // (temporary)
                    } else if (mode.matches("p(erformance)?")) {
                        // Performance testing: we want timing and no results
                        showOutput = false;
                        showStats = true;
                    } else if (mode.matches("a(ll)?")) {
                        // Regular: we want results and timing
                        showOutput = true;
                        showStats = true;
                        showMatchInfo = true;
                    } else {
                        System.err.println("Unknown mode: " + mode);
                        usage();
                        return;
                    }
                    i++;
                }
            } else if (arg.startsWith("-")) {
                switch (arg) {
                case "-e":
                    if (i + 1 == args.length) {
                        System.err.println("-e option needs argument");
                        usage();
                        return;
                    }
                    encoding = args[i + 1];
                    i++;
                    break;
                case "-f":
                    if (i + 1 == args.length) {
                        System.err.println("-f option needs argument");
                        usage();
                        return;
                    }
                    inputFile = new File(args[i + 1]);
                    i++;
                    System.err.println("Batch mode; reading commands from " + inputFile);
                    break;
                case "-v":
                    verbose = true;
                    showMatchInfo = true;
                    break;
                default:
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

        // By default we don't show stats in batch mode, but we do in interactive mode
        // (batch mode is useful for correctness testing, where you don't want stats;
        //  use --mode performance to get stats but no results in batch mode)
        boolean showStatsDefaultValue = inputFile == null;
        QueryTool.showStats = showStats == null ? showStatsDefaultValue : showStats;
        run(indexDir, inputFile, encoding, verbose);
    }

    /**
     * Run the query tool.
     *
     * @param indexDir the index to query
     * @param inputFile if specified, run in batch mode. If null, run in interactive
     *            mode
     * @param encoding the output encoding to use
     */
    private static void run(File indexDir, File inputFile, String encoding, boolean verbose) throws ErrorOpeningIndex {
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

        if (inputFile != null)
            batchMode = true;
        try (BufferedReader in = inputFile == null ? new BufferedReader(new InputStreamReader(System.in, encoding))
                : FileUtil.openForReading(inputFile, INPUT_FILE_ENCODING)) {
            QueryTool c = new QueryTool(indexDir, in, out, err);
            c.verbose = verbose;
            c.commandProcessor();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private static void usage() {
        System.err.println(
                "Usage: " + QueryTool.class.getName() + " [options] <indexDir>\n" +
                        "\n" +
                        "Options (mostly useful for batch testing):\n" +
                        "-f <file>            Execute batch commands from file and exit\n" +
                        "-v                   Start in verbose mode (show query & rewrite)\n" +
                        "--mode all           Show results and timings (default without -f)\n" +
                        "--mode correctness,  Show results but no timings (default for -f)\n" +
                        "--mode c\n" +
                        "--mode performance,  Show timings but no results\n" +
                        "--mode p,\n" +
                        "-e <encoding>        Specify what output encoding to use [system default]\n" +
                        "\n" +
                        WordUtils.wrap("Batch command files should contain one command per line, or multiple " +
                        "commands on a single line separated by && (use this e.g. to time " +
                        "querying and sorting together). Lines starting with # are comments. " +
                        "Comments are printed on stdout as well. Lines starting with - will " +
                        "not be reported. Start a line with -# for an unreported comment.", 80));
    }

    /**
     * Construct the query tool object.
     *
     * @param indexDir directory our index is in
     * @param in where to read commands from
     * @param out where to write output to
     * @param err where to write errors to
     * @throws ErrorOpeningIndex if we couldn't open the index
     */
    public QueryTool(File indexDir, BufferedReader in, PrintWriter out, PrintWriter err) throws ErrorOpeningIndex {
        this.in = in;
        this.out = out;
        this.err = err;

        assert in != null;
        printProgramHead();
        try {
            out.println("Opening index " + indexDir.getCanonicalPath() + "...");
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        // Create the BlackLab index object
        Timer t = new Timer();
        index = BlackLab.open(indexDir);
        out.println("Opening index took " + t.elapsedDescription());
        contentsField = index.mainAnnotatedField();

        contextSize = index.defaultContextSize();

        wordLists.put("test", Arrays.asList("de", "het", "een", "over", "aan"));
    }

    /**
     * Switch to a different index.
     *
     * @param index the new BlackLabIndex to use
     */
    public void setIndex(BlackLabIndex index) {
        if (shouldCloseIndex)
            index.close();
        this.index = index;
        contentsField = index.mainAnnotatedField();
        shouldCloseIndex = false; // caller is responsible

        // Reset results
        hits = null;
        groups = null;
        sortedHits = null;
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
                throw BlackLabRuntimeException.wrap(e1);
            }
            if (cmd == null)
                break;
            cmd = cmd.trim();
            if (cmd.equals("exit"))
                break;
            if (batchMode && showOutput && !cmd.isEmpty() && !cmd.startsWith("#")) {
                // Verbose batch mode, show command before output
                outprintln("COMMAND: " + cmd);
            }

            boolean printStat = showStats;
            if (cmd.startsWith("-")) {
                // Silent, don't output stats
                printStat = false;
                cmd = cmd.substring(1).trim();
            }

            if (cmd.startsWith("#")) {
                // Comment
                if (printStat)
                    statprintln(cmd);
                continue;
            }

            // Comment after command? Strip.
            cmd = cmd.replaceAll("\\s#.+$", "").trim();
            if (cmd.isEmpty()) {
                statprintln(""); // output empty lines in stats
                continue; // no actual command on line, skip
            }

            Timer t = new Timer();
            timings.clear();
            statInfo = "";
            commandWasQuery = false;
            try {
                processCommand(cmd);
            } catch (Exception e) {
                // Report exception but don't crash right away, so we can try other queries while debugging.
                e.printStackTrace();
            }
            if (printStat)
                statprintln((commandWasQuery ? "" : "@ ") + cmd + "\t" + t.elapsed() + "\t" + statInfo);

            System.err.flush(); // if there were error messages, make sure they are shown right away
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
        if (!fullCmd.isEmpty() && fullCmd.charAt(0) == '#') // comment (batch mode)
            return;

        // See if we want to loop a command
        if (fullCmd.startsWith("repeat ")) {
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
        if (!lcased.isEmpty()) {
            if (lcased.equals("clear") || lcased.equals("reset")) {
                hits = null;
                docs = null;
                groups = null;
                sortedHits = null;
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
                contextSize = ContextSize.get(parseInt(lcased.substring(8), 0), Integer.MAX_VALUE);
                collocations = null;
                showResultsPage();
            } else if (lcased.startsWith("snippet ")) {
                int hitId = parseInt(lcased.substring(8), 1) - 1;
                Hits currentHitSet = getCurrentSortedHitSet();
                if (hitId >= currentHitSet.size()) {
                    errprintln("Hit number out of range.");
                } else {
                    Hits singleHit = currentHitSet.window(hitId, 1);
                    Concordances concordances = singleHit.concordances(snippetSize, concType);
                    Hit h = currentHitSet.get(hitId);
                    Concordance conc = concordances.get(h);
                    String[] concParts;
                    if (stripXML)
                        concParts = conc.partsNoXml();
                    else
                        concParts = conc.parts();
                    outprintln(
                            "\n" + WordUtils.wrap(concParts[0] + hlStart(MATCH) +
                                    concParts[1] + hlEnd(MATCH) + concParts[2], 80));
                }
            } else if (lcased.startsWith("highlight ")) {
                int hitId = parseInt(lcased.substring(8), 1) - 1;
                Hits currentHitSet = getCurrentSortedHitSet();
                if (currentHitSet == null || hitId >= currentHitSet.size()) {
                    errprintln("Hit number out of range.");
                } else {
                    int docId = currentHitSet.get(hitId).doc();
                    Hits hitsInDoc = hits.getHitsInDoc(docId);
                    outprintln(WordUtils.wrap(DocUtil.highlightContent(index, docId, hitsInDoc, -1, -1), 80));
                }
            } else if (lcased.startsWith("snippetsize ")) {
                snippetSize = ContextSize.get(parseInt(lcased.substring(12), 0), Integer.MAX_VALUE);
                outprintln("Snippets will show " + snippetSize + " words of context.");
            } else if (lcased.startsWith("doc ")) {
                int docId = parseInt(lcased.substring(4), 0);
                showMetadata(docId);
            } else if (lcased.startsWith("doccontents ")) {
                int docId = parseInt(lcased.substring(4), 0);
                showContents(docId);
            } else if (lcased.startsWith("filter ") || lcased.equals("filter")) {
                if (cmd.length() <= 7) {
                    filterQuery = null; // clear filter
                    outprintln("Filter cleared.");
                } else {
                    String filterExpr = cmd.substring(7);
                    try {
                        filterQuery = LuceneUtil.parseLuceneQuery(index, filterExpr, index.analyzer(), "title");
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
                concType = parseBoolean(v) ? ConcordanceType.FORWARD_INDEX : ConcordanceType.CONTENT_STORE;
            } else if (lcased.startsWith("stripxml ")) {
                String v = lcased.substring(9);
                stripXML = parseBoolean(v);
            } else if (lcased.startsWith("sensitive ")) {
                String v = lcased.substring(10);
                MatchSensitivity sensitivity;
                switch (v) {
                case "on":
                case "yes":
                case "true":
                    sensitivity = MatchSensitivity.SENSITIVE;
                    break;
                case "case":
                    sensitivity = MatchSensitivity.DIACRITICS_INSENSITIVE;
                    break;
                case "diac":
                case "diacritics":
                    sensitivity = MatchSensitivity.CASE_INSENSITIVE;
                    break;
                default:
                    sensitivity = MatchSensitivity.INSENSITIVE;
                    break;
                }
                index.setDefaultMatchSensitivity(sensitivity);
                outprintln("Search defaults to "
                        + (sensitivity.isCaseSensitive() ? "case-sensitive" : "case-insensitive") + " and "
                        + (sensitivity.isDiacriticsSensitive() ? "diacritics-sensitive" : "diacritics-insensitive"));
            } else if (lcased.startsWith("doctitle ")) {
                String v = lcased.substring(9);
                showDocTitle = parseBoolean(v);
                System.out.println("Show document titles: " + (showDocTitle ? "ON" : "OFF"));
            } else if (lcased.equals("struct") || lcased.equals("structure")) {
                showIndexMetadata();
            } else if (lcased.startsWith("sort by ")) {
                sortBy(cmd.substring(8));
            } else if (lcased.startsWith("sort ")) {
                sortBy(cmd.substring(5));
            } else if (lcased.startsWith("group by ")) {
                String[] parts = lcased.substring(9).split(StringUtil.REGEX_WHITESPACE, 2);
                groupBy(parts[0], parts.length > 1 ? parts[1] : null);
            } else if (lcased.startsWith("group ")) {
                if (lcased.substring(6).matches("\\d+")) {
                    firstResult = 0; // reset for paging through group
                    changeShowSettings(lcased);
                } else {
                    String[] parts = lcased.substring(6).split(StringUtil.REGEX_WHITESPACE, 2);
                    groupBy(parts[0], parts.length > 1 ? parts[1] : null);
                }
            } else if (lcased.equals("groups") || lcased.equals("hits") || lcased.equals("docs")
                    || lcased.startsWith("colloc")) {
                changeShowSettings(cmd);
            } else if (lcased.equals("switch") || lcased.equals("sw")) {
                currentParserIndex++;
                if (currentParserIndex >= parsers.size())
                    currentParserIndex = 0;
                outprintln("Switching to " + parsers.get(currentParserIndex).getName() + ".\n");
                printQueryHelp();
            } else if (lcased.equals("help") || lcased.equals("?")) {
                printHelp();
            } else if (lcased.startsWith("sleep")) {
                try {
                    Thread.sleep((int) (Float.parseFloat(lcased.substring(6)) * 1000));
                } catch (NumberFormatException e1) {
                    errprintln("Sleep takes a float, the number of seconds to sleep");
                } catch (InterruptedException e) {
                    // OK
                }
            } else if (lcased.startsWith("wordlist")) {
                if (cmd.length() == 8) {
                    // Show loaded wordlists
                    outprintln("Available word lists:");
                    for (String listName : wordLists.keySet()) {
                        outprintln(" " + listName);
                    }
                } else {
                    // Load new wordlist or display existing wordlist
                    String[] parts = cmd.substring(9).trim().split(StringUtil.REGEX_WHITESPACE, 2);
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
                            for (String word : wordLists.get(fn)) {
                                outprintln(" " + word);
                            }
                        } else {
                            errprintln("File " + fn + " not found.");
                        }
                    }
                }
            } else if (lcased.startsWith("showconc ")) {
                String v = lcased.substring(9);
                showConc = parseBoolean(v);
                System.out.println("Show concordances: " + (showConc ? "ON" : "OFF"));
            } else if (lcased.equals("v") || lcased.startsWith("verbose ")) {
                if (lcased.equals("v"))
                    verbose = true;
                else
                    verbose = parseBoolean(lcased.substring(8));
                if (verbose)
                    showMatchInfo = true;
                outprintln("Verbose: " + (verbose ? "ON" : "OFF"));
            } else if (lcased.startsWith("total ")) {
                String v = lcased.substring(6);
                determineTotalNumberOfHits = parseBoolean(v);
                outprintln("Determine total number of hits: " + (determineTotalNumberOfHits ? "ON" : "OFF"));
            } else if (lcased.matches("^(patt)?field\\b.*")) {
                String v = lcased.replaceAll("^(patt)?field\\s?", "").trim();
                if (v.isEmpty()) {
                    contentsField = index.mainAnnotatedField();
                    outprintln("Searching main annotated field: " + contentsField.name());
                } else {
                    AnnotatedFields annotatedFields = index.metadata().annotatedFields();
                    if (!annotatedFields.exists(v)) {
                        // See if it's a version (e.g. different language in parallel corpus) of the main annotated field
                        String v2 = AnnotatedFieldNameUtil.changeParallelFieldVersion(index.mainAnnotatedField().name(), v);
                        if (annotatedFields.exists(v2))
                            v = v2;
                    }
                    if (annotatedFields.exists(v)) {
                        contentsField = annotatedFields.get(v);
                        outprintln("Searching annotated field: " + contentsField.name());
                    } else {
                        errprintln("Annotated field '" + v + "' does not exist.");
                    }
                }
            } else {
                // Not a command; assume it's a query
                parseAndExecuteQuery(cmd);
            }
        }

        if (restCommand != null)
            processCommand(restCommand);
    }

    private boolean parseBoolean(String v) {
        return v.equals("on") || v.equals("yes") || v.equals("true");
    }

    private void showContents(int docId) {
        if (!index.docExists(docId)) {
            outprintln("Document " + docId + " does not exist.");
            return;
        }
        Document doc = index.luceneDoc(docId);
        ContentAccessor ca = index.contentAccessor(contentsField);
        String[] content = ca.getSubstringsFromDocument(docId, doc, new int[] { -1 }, new int[] { -1 });
        outprintln(content[0]);
    }

    private void showMetadata(int docId) {
        if (!index.docExists(docId)) {
            outprintln("Document " + docId + " does not exist.");
            return;
        }
        Document doc = index.luceneDoc(docId);
        Map<String, String> metadata = new TreeMap<>(); // sort by key
        for (IndexableField f : doc.getFields()) {
            metadata.put(f.name(), f.stringValue());
        }
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            String value = e.getValue();
            if (value.length() > 255) {
                outprintln(e.getKey() + ": " + StringUtils.abbreviate(value, 255) + " (total length: " + value.length() + ")");
            } else {
                outprintln(e.getKey() + ": " + value);
            }
        }
    }

    public String describeAnnotation(Annotation annotation) {
        String sensitivityDesc;
        if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE)) {
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE)) {
                if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE)) {
                    sensitivityDesc = "case/diacritics sensitivity separate";
                } else {
                    sensitivityDesc = "sensitive and insensitive";
                }
            } else {
                sensitivityDesc = "sensitive only";
            }
        } else {
            sensitivityDesc = "insensitive only";
        }

        MatchSensitivity s = annotation.hasSensitivity(MatchSensitivity.INSENSITIVE) ? MatchSensitivity.INSENSITIVE : MatchSensitivity.SENSITIVE;
        String fieldName = annotation.sensitivity(s).luceneField();
        long maxTermsPerLeafReader = LuceneUtil.getMaxTermsPerLeafReader(index.reader(), fieldName);
        String luceneFieldInfo = " (lucene: " + fieldName + "; max. LR terms = " + maxTermsPerLeafReader + ") ";

        int numberOfUniqueTerms = annotation.hasForwardIndex() ? index.forwardIndex(annotation.field()).get(annotation).terms().numberOfTerms() : 0;
        return annotation.name() + luceneFieldInfo + (annotation.hasForwardIndex() ? " (+FI, " + numberOfUniqueTerms + " unique terms)" : "") + ", " + sensitivityDesc;
    }

    private void showIndexMetadata() {
        IndexMetadata s = index.metadata();
        outprintln("INDEX STRUCTURE FOR INDEX " + index.name() + "\n");
        out.println("ANNOTATED FIELDS");
        showAnnotatedField(s.mainAnnotatedField()); // show the main field first
        for (AnnotatedField cf: s.annotatedFields()) {
            if (cf != s.mainAnnotatedField())
                showAnnotatedField(cf);
        }

        out.println("\nMETADATA FIELDS");
        MetadataFields mf = s.metadataFields();
        for (MetadataField field: mf) {
            String special = "";
            if (field.name().equals(s.custom().get("titleField", "")))
                special = "TITLEFIELD";
            else if (field.name().equals(s.custom().get("authorField", "")))
                special = "AUTHORFIELD";
            else if (field.name().equals(s.custom().get("dateField", "")))
                special = "DATEFIELD";
            else if (mf.pidField() != null && field.name().equals(mf.pidField().name()))
                special = "PIDFIELD";
            if (!special.isEmpty())
                special = " (" + special + ")";
            FieldType type = field.type();
            out.println("- " + field.name() + (type == FieldType.TOKENIZED ? "" : " (" + type + ")")
                    + special);
        }
    }

    private void showAnnotatedField(AnnotatedField cf) {
        out.println("- " + cf.name());
        for (Annotation annot: cf.annotations()) {
            out.println("  * Annotation: " + describeAnnotation(annot));
        }
        out.println("  * " + (cf.hasContentStore() ? "Includes" : "No") + " content store");
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
                jlineConsoleReader = c.getConstructor().newInstance();

                // Disable bell
                c.getMethod("setBellEnabled", boolean.class).invoke(jlineConsoleReader, false);

                // Fetch and store the readLine method
                jlineReadLineMethod = c.getMethod("readLine", String.class);

                outprintln("Command line editing enabled.");
            } catch (ClassNotFoundException e) {
                // Can't init JLine; too bad, fall back to stdin
                outprintln("Command line editing not available; to enable, place jline jar in classpath.");
            } catch (ReflectiveOperationException e) {
                throw new BlackLabRuntimeException("Could not init JLine console reader", e);
            }
        }

        if (jlineConsoleReader != null) {
            try {
                return (String) jlineReadLineMethod.invoke(jlineConsoleReader, prompt);
            } catch (ReflectiveOperationException e) {
                throw new BlackLabRuntimeException("Could not invoke JLine ConsoleReader.readLine()", e);
            }
        }

        if (!batchMode)
            outprint(prompt);
        out.flush();
        return in.readLine();
    }

    /**
     * Print command and query help.
     */
    private void printHelp() {
        if (batchMode)
            return;
        String langAvail = "BCQL, Lucene, ContextQL (EXPERIMENTAL)";

        outprintln("Control commands:");
        outprintln("  p(rev) / n(ext) / page <n>         # Page through results");
        outprintln("  sort {match|before|after} [annot]  # Sort query results  (before = context before match, etc.)");
        outprintln("  sort <property>                    #   (like BLS, e.g. before:lemma:s)");
        outprintln("  group {match|before|after} [annot] # Group query results (annot = e.g. 'word', 'lemma', 'pos')");
        outprintln("  group <property>                   #   (like BLS, e.g. hit:lemma:i or capture:pos:i:A)");
        outprintln("  hits / groups / group <n> / colloc # Switch between results modes");
        outprintln("  field <name>                       # Set the annotated field to search");
        outprintln("  context <n>                        # Set number of words to show around hits");
        outprintln("  pagesize <n>                       # Set number of hits to show per page");
        outprintln("  snippet <x>                        # Show longer snippet around hit x");
        outprintln("  doc <id>                           # Show metadata for doc id");
        outprintln("  doccontents <id>                   # Retrieve contents of doc id");
        outprintln("  snippetsize <n>                    # Words to show around hit in longer snippet");
        outprintln("  sensitive {on|off|case|diac}       # Set case-/diacritics-sensitivity");
        outprintln("  filter <luceneQuery>               # Set document filter, e.g. title:\"Smith\"");
        outprintln("  doctitle {on|off}                  # Show document titles between hits?");
        outprintln("  struct                             # Show index structure");
        outprintln("  help                               # This message");
        outprintln("  sw(itch)                           # Switch languages");
        outprintln("                                     # (" + langAvail + ")");

        outprintln("  exit                               # Exit program");

        outprintln("\nBatch testing commands (start in batch mode with -f <commandfile>):");
        outprintln("  wordlist <file> <listname>         # Load a list of words");
        outprintln("  @@<listname>                       # Substitute a random word from list (use in query)");
        outprintln("  repeat <n> <query>                 # Repeat a query n times (with different random words)");
        outprintln("  sleep <f>                          # Sleep a number of seconds");
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
     * @param query the query
     */
    private void parseAndExecuteQuery(String query) {
        Timer t = new Timer();
        try {

            // See if we want to choose any random words
            if (query.contains("@@")) {
                StringBuilder resultString = new StringBuilder();
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
                    int randomIndex = (int) (Math.random() * list.size());
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
                outprintln("TextPattern: " + pattern);

            // If the query included filter clauses, use those. Otherwise use the global filter, if any.
            Query filter = parser.getIncludedFilterQuery();
            if (filter == null)
                filter = filterQuery;

            // Execute search
            BLSpanQuery spanQuery = pattern.toQuery(QueryInfo.create(index, contentsField), filter);
            if (verbose) {
                outprintln("SpanQuery: " + spanQuery.toString(contentsField.name()));
                try {
                    outprintln("Rewritten: " + spanQuery.rewrite(index.reader()).toString(contentsField.name()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            SearchHits search = index.search(contentsField).find(spanQuery);
            timings = search.queryInfo().timings();

            if (alwaysSortBy != null) {
                search = search.sort(HitProperty.deserialize(index, contentsField, alwaysSortBy));
            }

            hits = search.execute();
            docs = null;
            groups = null;
            sortedHits = null;
            collocations = null;
            showWhichGroup = -1;
            showSetting = ShowSetting.HITS;
            firstResult = 0;
            showResultsPage();
            if (determineTotalNumberOfHits)
                statInfo = Long.toString(hits.size());
            else
                statInfo = "?";
            commandWasQuery = true;
        } catch (InvalidQuery e) {
            // Parse error
            errprintln(e.getMessage());
            errprintln("(Type 'help' for examples or see https://inl.github.io/BlackLab/development/query-tool.html)");
        } catch (UnsupportedOperationException e) {
            // Unimplemented part of query language used
            e.printStackTrace(); // DEBUG createWeight bug
            errprintln("Cannot execute query; " + e.getMessage());
            errprintln("(Type 'help' for examples or see https://inl.github.io/BlackLab/development/query-tool.html)");
        }
    }

    /**
     * Show the a specific page of results.
     *
     * @param pageNumber which page to show
     */
    private void showPage(long pageNumber) {
        if (hits != null) {

            if (determineTotalNumberOfHits) {
                // Clamp page number of total number of hits
                long totalResults;
                switch (showSetting) {
                case COLLOC:
                    totalResults = collocations.size();
                    break;
                case GROUPS:
                    totalResults = groups.size();
                    break;
                default:
                    totalResults = hits.size();
                    break;
                }

                long totalPages = (totalResults + resultsPerPage - 1) / resultsPerPage;
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
     * @param sortBy property to sort by
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
            String[] parts = sortBy.split(StringUtil.REGEX_WHITESPACE, 2);
            String sortByPart = parts[0];
            String propPart = parts.length > 1 ? parts[1] : null;
            sortHits(sortByPart, propPart);
            break;
        }
    }

    /** Desired context size */
    private ContextSize contextSize;

    /** Are we responsible for closing the BlackLabIndex? */
    private boolean shouldCloseIndex = true;

    /**
     * Sort hits by the specified property.
     *
     * @param sortBy hit property to sort by
     * @param annotationName (optional) if sortBy is a context property (say, hit text),
     *            this gives the token annotation to use for the context. Example: if
     *            this is "lemma", will look at the lemma(ta) of the hit text. If
     *            this is null, uses the "main annotation" (word form, usually).
     */
    private void sortHits(String sortBy, String annotationName) {
        Timer t = new Timer();
        timings.start();

        Hits hitsToSort = getCurrentHitSet();

        HitProperty crit = getCrit(sortBy, annotationName, -1);
        if (crit == null) {
            errprintln("Invalid hit sort criterium: " + sortBy
                    + " (valid are: match, before, after, doc, <metadatafield>)");
        } else {
            sortedHits = hitsToSort.sort(crit);
            firstResult = 0;
            timings.record("sort");
            showResultsPage();
        }
    }

    /**
     * Sort groups by the specified property.
     *
     * @param sortBy property to sort by
     */
    private void sortGroups(String sortBy) {
        HitGroupProperty crit = null;
        if (sortBy.equals(HitGroupPropertyIdentity.ID) || sortBy.equals("id"))
            crit = HitGroupProperty.identity();
        else if (sortBy.startsWith(HitGroupPropertySize.ID))
            crit = HitGroupProperty.size();
        if (crit == null) {
            errprintln("Invalid group sort criterium: " + sortBy
                    + " (valid are: id(entity), size)");
        } else {
            groups = groups.sort(crit);
            firstResult = 0;
            showResultsPage();
        }
    }

    /**
     * Group hits by the specified property.
     *
     * @param groupBy hit property to group by
     * @param annotationName (optional) if groupBy is a context property (say, hit text),
     *            this gives the token annotation to use for the context. Example: if
     *            this is "lemma", will look at the lemma(ta) of the hit text. If
     *            this is null, uses the "main annotation" (word form, usually).
     */
    private void groupBy(String groupBy, String annotationName) {
        if (hits == null)
            return;

        Timer t = new Timer();

        // Group results
        HitProperty crit = getCrit(groupBy, annotationName, 1);
        groups = hits.group(crit, -1);
        showSetting = ShowSetting.GROUPS;
        sortGroups(HitGroupPropertySize.ID);
        timings.record("group");
        reportTime();
    }

    private HitProperty getCrit(String critType, String annotationName, int numberOfContextTokens) {
        HitProperty crit;
        if (StringUtils.isEmpty(annotationName) && contentsField.annotation(critType) != null) {
            // Assume we want to group by matched text if we don't specify it explicitly.
            annotationName = critType;
            critType = "match";
        }
        Annotation annotation;
        try {
            annotation = annotationName == null ? contentsField.mainAnnotation() : contentsField.annotation(annotationName);
        } catch (Exception e) {
            errprintln("Unknown annotation: " + annotationName);
            return null;
        }
        switch (critType) {
        case "word":
        case "match":
        case "hit":
            crit = new HitPropertyHitText(index, annotation);
            break;
        case HitPropertyBeforeHit.ID:
            crit = new HitPropertyBeforeHit(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyLeftContext.ID:
            crit = new HitPropertyLeftContext(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyAfterHit.ID:
            crit = new HitPropertyAfterHit(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyRightContext.ID:
            crit = new HitPropertyRightContext(index, annotation, null, numberOfContextTokens);
            break;
        case HitPropertyDocumentId.ID:
            crit = new HitPropertyDocumentId();
            break;
        case "lempos": // special case for testing
            HitProperty p1 = new HitPropertyHitText(index, contentsField.annotation("lemma"));
            HitProperty p2 = new HitPropertyHitText(index, contentsField.annotation("pos"));
            crit = new HitPropertyMultiple(p1, p2);
            break;
        default:
            if (index.metadataFields().exists(critType)) {
                // name of a metadata field. Group/sort on that.
                crit = new HitPropertyDocumentStoredField(index, critType);
            } else {
                // Regular BLS serialized hit property. Decode it.
                crit = HitProperty.deserialize(hits, critType);
            }
            break;
        }
        return crit;
    }

    /**
     * Switch between showing all hits, groups, and the hits in one group.
     *
     * @param showWhat what type of results to show
     */
    private void changeShowSettings(String showWhat) {
        sortedHits = null;
        if (showWhat.equals("hits")) {
            showSetting = ShowSetting.HITS;
            showWhichGroup = -1;
        } else if (showWhat.equals("docs")) {
            showSetting = ShowSetting.DOCS;
        } else if (showWhat.startsWith("colloc") && hits != null) {
            showSetting = ShowSetting.COLLOC;
            if (showWhat.length() >= 7) {
                String newCollocAnnot = showWhat.substring(7);
                if (!newCollocAnnot.equals(collocAnnotation.name())) {
                    collocAnnotation = contentsField.annotation(newCollocAnnot);
                    collocations = null;
                }
            }
        } else if (showWhat.equals("groups") && groups != null) {
            showSetting = ShowSetting.GROUPS;
        } else if (showWhat.startsWith("group ") && groups != null) {
            showWhichGroup = parseInt(showWhat.substring(6), 1) - 1;
            if (showWhichGroup < 0 || showWhichGroup >= groups.size()) {
                errprintln("Group doesn't exist");
                showWhichGroup = -1;
            } else
                showSetting = ShowSetting.HITS; // Show hits in group, not all the groups
        }
        showResultsPage();
    }

    /**
     * Report how long an operation took
     */
    private void reportTime() {
        if (showStats && verbose) {
            //outprintln(describeInterval(time) + " elapsed");
            if (!timings.isEmpty()) {
                outprintln("Query timings:\n" + timings.summarize());
            } else {
                outprintln("Query took <1ms (no timings recorded)");
            }
        }
    }

    private String describeInterval(long time1) {
        if (timeDisplayHumanFriendly)
            return TimeUtil.describeInterval(time1);
        return time1 + " ms";
    }

    /**
     * Close the BlackLabIndex object.
     */
    private void cleanup() {
        if (shouldCloseIndex)
            index.close();
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
        reportTime();
    }

    /**
     * Show the current page of collocations.
     */
    private void showCollocations() {
        if (collocations == null) {
            // Case-sensitive collocations..?
            if (collocAnnotation == null) {
                AnnotatedField field = hits.field();
                collocAnnotation = field.mainAnnotation();
            }

            collocations = hits.collocations(collocAnnotation, contextSize, index.defaultMatchSensitivity(), true);
        }

        int i = 0;
        for (TermFrequency coll : collocations) {
            if (i >= firstResult && i < firstResult + resultsPerPage) {
                long j = i - firstResult + 1;
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
        for (long i = firstResult; i < groups.size() && i < firstResult + resultsPerPage; i++) {
            Group<Hit> g = groups.get(i);
            outprintln(String.format("%4d. %5d %s", i + 1, g.size(), g.identity().toString()));
        }

        // Summarize
        String msg = groups.size() + " groups";
        outprintln(msg);
    }

    private void showDocsPage() {
        Hits currentHitSet = getCurrentHitSet();
        if (docs == null) {
            if (currentHitSet != null)
                docs = currentHitSet.perDocResults(Results.NO_LIMIT);
            else if (filterQuery != null) {
                docs = index.queryDocuments(filterQuery);
            } else {
                System.out.println("No documents to show (set filterquery or search for hits first)");
                return;
            }
        }

        // Limit results to the current page
        DocResults window = docs.window(firstResult, resultsPerPage);

        // Compile hits display info and calculate necessary width of left context column
        String titleField = index.metadata().custom().get("titleField", "");
        long hitNr = window.windowStats().first() + 1;
        for (Group<Hit> result : window) {
            int docId = ((PropertyValueDoc)result.identity()).value();
            Document d = index.luceneDoc(docId);
            String title = d.get(titleField);
            if (title == null)
                title = "(doc #" + docId + ", no " + titleField + " given)";
            else
                title = title + " (doc #" + docId + ")";
            outprintf("%4d. %s\n", hitNr, title);
            hitNr++;
        }

        // Summarize
        long docsCounted = docs.size();
        if (determineTotalNumberOfHits && currentHitSet != null)
            docsCounted = currentHitSet.docsStats().countedTotal();
        outprintln(docsCounted + " docs");
    }

    /**
     * A hit we're about to show.
     * <p>
     * We need a separate structure because we filter out XML tags and need to know
     * the longest left context before displaying.
     */
    static class HitToShow {
        public final int doc;

        public final String left;
        public final String hitText;
        public final String right;

        public final Map<String, MatchInfo> matchInfos;

        /** Hits in other fields (parallel corpora) */
        public final Map<String, HitToShow> foreignHits = new TreeMap<>();

        public HitToShow(int doc, String left, String hitText, String right, Map<String, MatchInfo> matchInfos) {
            super();
            this.doc = doc;
            this.left = left;
            this.hitText = hitText;
            this.right = right;
            this.matchInfos = matchInfos;
        }

        public void addForeign(String fieldName, HitToShow hitToShow) {
            foreignHits.put(fieldName, hitToShow);
        }
    }

    /**
     * Show the current page of hits.
     */
    private void showHitsPage() {
        timings.start();

        Hits hitsToShow = getCurrentSortedHitSet();
        if (!showConc) {
            if (determineTotalNumberOfHits) {
                // Just show total number of hits, no concordances
                outprintln(hitsToShow.size() + " hits");
            } else {
                Iterator<EphemeralHit> it = hitsToShow.ephemeralIterator();
                int i;
                for (i = 0; it.hasNext() && i < resultsPerPage; i++) {
                    it.next();
                }
                outprintln((i == resultsPerPage ? "At least " : "") + i + " hits (total not determined)");
            }
            return;
        }

        if (hitsToShow == null)
            return; // nothing to show

        // Limit results to the current page
        Hits window = hitsToShow.window(firstResult, resultsPerPage);

        // Compile hits display info and calculate necessary width of left context column
        int leftContextMaxSize = 10; // number of characters to reserve on screen for left context
        Concordances concordances = window.concordances(contextSize, concType);
        Kwics kwics = concType == ConcordanceType.FORWARD_INDEX ? concordances.getKwics() : null;
        List<HitToShow> toShow = new ArrayList<>();
        for (Hit hit: window) {
            HitToShow hitToShow;
            if (kwics != null) {
                Map<String, MatchInfo> matchInfo = window.hasMatchInfo() ? window.getMatchInfoMap(hit) :
                        Collections.emptyMap();
                hitToShow = showHitFromForwardIndex(hit, kwics.get(hit), matchInfo, window.field());

                Map<String, Kwic> fkwics = kwics.getForeignKwics(hit);
                if (fkwics != null) {
                    for (Map.Entry<String, Kwic> e: fkwics.entrySet()) {
                        String fieldName = e.getKey();
                        AnnotatedField annotatedField = index.metadata().annotatedFields().get(fieldName);
                        if (annotatedField == null)
                            throw new RuntimeException();
                        Kwic kwic = e.getValue();
                        Hit fhit = Hit.create(hit.doc(), kwic.fragmentStartInDoc(), kwic.fragmentEndInDoc(),
                                hit.matchInfo());
                        hitToShow.addForeign(fieldName, showHitFromForwardIndex(fhit, kwic, matchInfo, annotatedField));
                    }
                }
            } else {
                hitToShow = showHitFromContentStore(hit, concordances, window);
            }
            toShow.add(hitToShow);
            if (leftContextMaxSize < hitToShow.left.length())
                leftContextMaxSize = hitToShow.left.length();
        }

        // Display hits
        String optFormatDocId = showDocTitle || !showDocIds ? "" : " [doc %04d]";
        String format = "%4d." + optFormatDocId +
                " %" + leftContextMaxSize + "s" + hlStart(MATCH) + "%s" +
                hlEnd(MATCH) + "%s\n";
        int currentDoc = -1;
        String titleField = index.metadata().custom().get("titleField", "");
        long hitNr = window.windowStats().first() + 1;
        for (HitToShow hit : toShow) {
            if (showDocTitle && hit.doc != currentDoc) {
                if (currentDoc != -1)
                    outprintln("");
                currentDoc = hit.doc;
                Document d = index.luceneDoc(currentDoc);
                String title = d.get(titleField);
                if (title == null)
                    title = "(doc #" + currentDoc + ", no " + titleField + " given)";
                else
                    title = title + " (doc #" + currentDoc + ")";
                outprintln("--- " + title + " ---");
            }
            if (showDocTitle || !showDocIds)
                outprintf(format, hitNr, hit.left, hit.hitText, hit.right);
            else
                outprintf(format, hitNr, hit.doc, hit.left, hit.hitText, hit.right);
            for (Map.Entry<String, HitToShow> e: hit.foreignHits.entrySet()) {
                String fieldName = e.getKey();
                HitToShow fhit = e.getValue();
                outprintf("    %s: %s%s%s\n", fieldName, fhit.left, fhit.hitText, fhit.right);
            }
            if (hit.matchInfos != null && showMatchInfo) {
                outprintln("MATCH INFO: " + matchInfoToString(hit.matchInfos));
            }
            hitNr++;
        }
        timings.record("kwics");

        // Summarize
        String msg;
        ResultsStats hitsStats = hitsToShow.hitsStats();
        if (!determineTotalNumberOfHits) {
            msg = hitsStats.countedSoFar() + " hits counted so far (total not determined)";
        } else {
            long numberRetrieved = hitsToShow.size();
            ResultsStats docsStats = hitsToShow.docsStats();
            String hitsInDocs = numberRetrieved + " hits in " + docsStats.processedTotal() + " documents";
            if (hits.maxStats().hitsProcessedExceededMaximum()) {
                if (hits.maxStats().hitsCountedExceededMaximum()) {
                    msg = hitsInDocs + " retrieved, more than " + hitsStats.countedTotal() + " ("
                            + docsStats.countedTotal() + " docs) total";
                } else {
                    msg = hitsInDocs + " retrieved, " + hitsStats.countedTotal() + " (" + docsStats.countedTotal()
                            + " docs) total";
                }
            } else {
                msg = hitsInDocs;
            }
            timings.record("count");
        }
        outprintln(msg);
    }

    private HitToShow showHitFromForwardIndex(Hit hit, Kwic kwic, Map<String, MatchInfo> matchInfo, AnnotatedField annotatedField) {
        // NOTE: if annotatedField == null, it means this hit is in the main field searched;
        //       it would only ever be non-null for parallel corpora where some match info can be captured in another
        //       field than the main search field.
        String mainAnnotName = annotatedField.mainAnnotation().name();
        DocFragment fragMatch = kwic.fragMatch();
        DocFragment fragBefore = kwic.fragBefore();
        String punctAfter = fragMatch.getValue(0, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        String before = getContextPart(fragBefore, matchInfo, annotatedField, hit.start() - fragBefore.length(), mainAnnotName, true,
                punctAfter);
        String match = getContextPart(fragMatch, matchInfo, annotatedField, hit.start(), mainAnnotName, false, null);
        String after = getContextPart(kwic.fragAfter(), matchInfo, annotatedField, hit.end(), mainAnnotName, true, null);
        return new HitToShow(hit.doc(), before, match, after, matchInfo);
    }

    private static String getContextPart(DocFragment fragment, Map<String, MatchInfo> matchInfo,
            AnnotatedField annotatedField, int pos, String mainAnnot, boolean includePunctBefore, String punctAfter) {
        StringBuilder contextPart = new StringBuilder();
        for (int i = 0; i < fragment.length(); i++) {
            List<String> open = new ArrayList<>();
            List<String> close = new ArrayList<>();
            for (Map.Entry<String, MatchInfo> e: matchInfo.entrySet()) {
                MatchInfo mi = e.getValue();
                String name = e.getKey();
                addMatchInfoIndicator(annotatedField, pos, mi, name, open, close, -1);
            }
            if (i > 0 || includePunctBefore) {
                contextPart
                        .append(fragment.getValue(i, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
            }
            Collections.reverse(close);
            contextPart
                    .append(StringUtils.join(open, ""))
                    .append(fragment.getValue(i, mainAnnot))
                    .append(StringUtils.join(close, ""));
            pos++;
        }
        if (punctAfter != null) {
            contextPart.append(punctAfter);
        }
        return contextPart.toString();
    }

    private static void addMatchInfoIndicator(AnnotatedField annotatedField, int pos, MatchInfo mi, String name,
            List<String> open, List<String> close, int number) {
        if (number > 0)
            name += "#" + number;
        String namePrefix = "", nameSuffix = "";
        int start = -1, end = -1;
        if (mi instanceof RelationListInfo) {
            int i = 1;
            for (RelationInfo rel: ((RelationListInfo) mi).getRelations()) {
                addMatchInfoIndicator(annotatedField, pos, rel, name, open, close, i);
                i++;
            }
        } else if (mi instanceof RelationInfo && mi.getType() == MatchInfo.Type.RELATION) { // (rel, not tag)
            // For relations, we have to either highlight the source or the target, which may be in different
            // fields.
            RelationInfo rel = (RelationInfo) mi;

            boolean isSourceField = rel.getField().equals(annotatedField.name());
            if ((pos == rel.getSourceStart() || pos + 1 == rel.getSourceEnd()) && isSourceField) {
                // Highlight relation source
                start = rel.getSourceStart();
                end = rel.getSourceEnd();
                String relType = RelationUtil.typeFromFullType(rel.getFullRelationType());
                if (relType.length() > 5) {
                    relType = relType.substring(0, 5).replaceAll("[\\s_\\-]+$", "");
                    relType += "…";
                }
                String optRelType = (name.contains(relType) ? "" : " " + relType);
                nameSuffix = optRelType + " →";
            }
            boolean isTargetField = rel.getTargetField().equals(annotatedField.name());
            if ((pos == rel.getTargetStart() || pos + 1 == rel.getTargetEnd()) && isTargetField) {
                // Highlight relation target
                start = rel.getTargetStart();
                end = rel.getTargetEnd();
                namePrefix = "→";
            }
        } else if (mi.getField().equals(annotatedField.name())) {
            // Match info is in this field; not a relation, so just use the full span
            start = mi.getSpanStart();
            end = mi.getSpanEnd();
        } else {
            // Some other field; ignore
            return;
        }
        if (start == pos)
            open.add(hlStart(namePrefix + name + nameSuffix));
        if (end == pos + 1)
            close.add(hlEnd(name));
    }

    private static String hlStart(String name) {
        return "[" + name + "]";
    }

    private static String hlEnd(String name) {
        return "[/" + name + "]";
    }

    private HitToShow showHitFromContentStore(Hit hit, Concordances concordances, Hits window) {
        HitToShow hitToShow;
        Concordance conc = concordances.get(hit);

        // Filter out the XML tags
        String left, hitText, right;
        left = stripXML ? XmlUtil.xmlToPlainText(conc.left()) : conc.left();
        hitText = stripXML ? XmlUtil.xmlToPlainText(conc.match()) : conc.match();
        right = stripXML ? XmlUtil.xmlToPlainText(conc.right()) : conc.right();

        Map<String, MatchInfo> matchInfo = null;
        if (window.hasMatchInfo())
            matchInfo = window.getMatchInfoMap(hit);
        hitToShow = new HitToShow(hit.doc(), left, hitText, right, matchInfo);
        return hitToShow;
    }

    private String matchInfoToString(Map<String, MatchInfo> matchInfos) {
        return matchInfos.entrySet().stream()
                .sorted( (a, b) -> {
                    MatchInfo ma = a.getValue();
                    MatchInfo mb = b.getValue();
                    if (ma == null && mb == null)
                        return 0;
                    else if (ma == null)
                        return -1;
                    else if (mb == null)
                        return 1;
                    MatchInfo.Type at = ma.getType();
                    MatchInfo.Type bt = mb.getType();
                    // Sort by type
                    if (at != bt)
                        return at.compareTo(bt);
                    if (at == MatchInfo.Type.SPAN) {
                        // Sort capture groups by name
                        return a.getKey().compareTo(b.getKey());
                    } else {
                        // Sort other match info by value
                        // ((list of) relations and inline tags)
                        return a.getValue().compareTo(b.getValue());
                    }
                })
                .map(e -> {
                    MatchInfo mi = e.getValue();
                    if (mi == null)
                        return "(null)";
                    return e.getKey() + "=" + mi.toString(contentsField.name());
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns the hit set we're currently looking at.
     * <p>
     * This is either all hits or the hits in one group.
     * <p>
     * If a sort has been applied, returns the sorted hits.
     *
     * @return the hit set
     */
    private Hits getCurrentSortedHitSet() {
        if (sortedHits != null)
            return sortedHits;
        return getCurrentHitSet();
    }

    /**
     * Returns the hit set we're currently looking at.
     * <p>
     * This is either all hits or the hits in one group.
     *
     * @return the hit set
     */
    private Hits getCurrentHitSet() {
        Hits hitsToShow = hits;
        if (showWhichGroup >= 0) {
            Group<Hit> g = groups.get(showWhichGroup);
            hitsToShow = (Hits)g.storedResults();
        }
        return hitsToShow;
    }

    public void outprintln(String str) {
        if (showOutput)
            out.println(str);
    }

    public void outprint(String str) {
        if (showOutput)
            out.print(str);
    }

    public void outprintf(String str, Object... args) {
        if (showOutput)
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
