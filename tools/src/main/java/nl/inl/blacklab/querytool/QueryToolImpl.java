package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
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
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.QueryTimings;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.Timer;

/**
 * Simple command-line querying tool for BlackLab indices.
 */
public class QueryToolImpl {

    static final Charset INPUT_FILE_ENCODING = StandardCharsets.UTF_8;
    public static final String START_MATCH = "[match]";
    public static final String END_MATCH = "[/match]";

    public static final String MATCH = "match";

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

    /** Show total number of hits (takes extra time for large sets) */
    private boolean determineTotalNumberOfHits = true;

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

    private final List<Parser> parsers = Arrays.asList(new ParserCorpusQl(), new ParserContextQl());

    private int currentParserIndex = 0;

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
     * Run the query tool.
     *
     * @param args program arguments
     */
    public static void run(String[] args) throws ErrorOpeningIndex {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that
        LogUtil.setupBasicLoggingConfig(Level.WARN);

        // Parse command line
        File indexDir = null;
        File inputFile = null;
        String encoding = Charset.defaultCharset().name();
        Boolean showStats = null; // default not overridden (default depends on batch mode or not)
        Output output = new Output();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--")) {
                if (arg.equals("--mode")) {
                    if (i + 1 == args.length) {
                        System.err.println("--mode option needs argument");
                        Output.usage();
                        return;
                    }
                    String mode = args[i + 1].toLowerCase();
                    if (mode.matches("c(orrectness)?")) {
                        // Correctness testing: we want results, no timing and larger pagesize
                        output.setShowOutput(true);
                        showStats = false;
                        defaultPageSize = 1000;
                        alwaysSortBy = "after:word:s,hitposition"; // for reproducibility
                        output.setShowDocIds(false); // doc ids are randomly assigned
                        output.setShowMatchInfo(false); // (temporary)
                    } else if (mode.matches("p(erformance)?")) {
                        // Performance testing: we want timing and no results
                        output.setShowOutput(false);
                        showStats = true;
                    } else if (mode.matches("a(ll)?")) {
                        // Regular: we want results and timing
                        output.setShowOutput(true);
                        showStats = true;
                        output.setShowMatchInfo(true);
                    } else {
                        System.err.println("Unknown mode: " + mode);
                        Output.usage();
                        return;
                    }
                    i++;
                }
            } else if (arg.startsWith("-")) {
                switch (arg) {
                case "-e":
                    if (i + 1 == args.length) {
                        System.err.println("-e option needs argument");
                        Output.usage();
                        return;
                    }
                    encoding = args[i + 1];
                    i++;
                    break;
                case "-f":
                    if (i + 1 == args.length) {
                        System.err.println("-f option needs argument");
                        Output.usage();
                        return;
                    }
                    inputFile = new File(args[i + 1]);
                    i++;
                    System.err.println("Batch mode; reading commands from " + inputFile);
                    break;
                case "-v":
                    output.setVerbose(true);
                    output.setShowMatchInfo(true);
                    break;
                default:
                    System.err.println("Unknown option: " + arg);
                    Output.usage();
                    return;
                }
            } else {
                if (indexDir != null) {
                    System.err.println("Can only specify 1 index directory");
                    Output.usage();
                    return;
                }
                indexDir = new File(arg);
            }
        }
        if (indexDir == null) {
            Output.usage();
            return;
        }

        // By default we don't show stats in batch mode, but we do in interactive mode
        // (batch mode is useful for correctness testing, where you don't want stats;
        //  use --mode performance to get stats but no results in batch mode)
        boolean showStatsDefaultValue = inputFile == null;
        output.setShowStats(showStats == null ? showStatsDefaultValue : showStats);


        if (!indexDir.exists() || !indexDir.isDirectory()) {
            System.err.println("Index dir " + indexDir.getPath() + " doesn't exist.");
            return;
        }

        // Use correct output encoding
        try {
            // Yes
            output.setOutputWriter(new PrintWriter(new OutputStreamWriter(System.out, encoding), true));
            output.setErrorWriter(new PrintWriter(new OutputStreamWriter(System.err, encoding), true));
            output.line("Using output encoding " + encoding + "\n");
        } catch (UnsupportedEncodingException e) {
            // Nope; fall back to default
            output.setOutputWriter(new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true));
            output.setErrorWriter(new PrintWriter(new OutputStreamWriter(System.err, Charset.defaultCharset()), true));
            output.error("Unknown encoding " + encoding + "; using default");
        }

        if (inputFile != null)
            output.setBatchMode(true);
        try (BufferedReader in = inputFile == null ? new BufferedReader(new InputStreamReader(System.in, encoding))
                : FileUtil.openForReading(inputFile, INPUT_FILE_ENCODING)) {
            QueryToolImpl c = new QueryToolImpl(indexDir, in, output);
            output.printHelp(c.getCurrentParser());
            c.commandProcessor();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Construct the query tool object.
     *
     * @param indexDir directory our index is in
     * @param in where to read commands from
     * @param output where and how to produce output
     * @throws ErrorOpeningIndex if we couldn't open the index
     */
    public QueryToolImpl(File indexDir, BufferedReader in, Output output) throws ErrorOpeningIndex {
        this.output = output;
        printProgramHead();
        try {
            output.line("Opening index " + indexDir.getCanonicalPath() + "...");
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        // Create the BlackLab index object
        Timer t = new Timer();
        index = BlackLab.open(indexDir);
        output.line("Opening index took " + t.elapsedDescription());
        contentsField = index.mainAnnotatedField();

        contextSize = index.defaultContextSize();

        wordLists.put("test", Arrays.asList("de", "het", "een", "over", "aan"));
        commandReader = new CommandReader(in, output);
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

        while (true) {
            String prompt = getCurrentParser().getPrompt() + "> ";
            String cmd;
            try {
                cmd = commandReader.readCommand(prompt);
            } catch (IOException e1) {
                throw BlackLabRuntimeException.wrap(e1);
            }
            if (cmd == null)
                break;
            cmd = cmd.trim();
            if (cmd.equals("exit"))
                break;
            output.command(cmd); // (in batch mode, commands are shown in output)

            boolean printStat = true;
            if (cmd.startsWith("-")) {
                // Silent, don't output stats
                printStat = false;
                cmd = cmd.substring(1).trim();
            }

            if (cmd.startsWith("#")) {
                // Comment
                if (printStat)
                    output.stats(cmd);
                continue;
            }

            // Comment after command? Strip.
            cmd = cmd.replaceAll("\\s#.+$", "").trim();
            if (cmd.isEmpty()) {
                output.stats(""); // output empty lines in stats
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
                output.stats((commandWasQuery ? "" : "@ ") + cmd + "\t" + t.elapsed() + "\t" + statInfo);

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
                output.line("Repeating " + repCount + " times: " + fullCmd);
                for (int i = 0; i < repCount; i++) {
                    processCommand(fullCmd);
                }
            } else {
                output.error("Repeat command should have a repetition count.");
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
                output.line("Query and results cleared.");
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
                    output.error("Hit number out of range.");
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
                    output.line(
                            "\n" + WordUtils.wrap(concParts[0] + Output.hlStart(MATCH) +
                                    concParts[1] + Output.hlEnd(MATCH) + concParts[2], 80));
                }
            } else if (lcased.startsWith("snippetsize ")) {
                snippetSize = ContextSize.get(parseInt(lcased.substring(12), 0), Integer.MAX_VALUE);
                output.line("Snippets will show " + snippetSize + " words of context.");
            } else if (lcased.startsWith("doc ")) {
                int docId = parseInt(lcased.substring(4), 0);
                showMetadata(docId);
            } else if (lcased.startsWith("doccontents ")) {
                // Get plain document contents (no highlighting)
                int docId = parseInt(lcased.substring(4), 0);
                showContents(docId);
            } else if (lcased.startsWith("highlight ")) {
                // Get highlighted document contents
                int docId = parseInt(lcased.substring(8), 1) - 1;
                Hits currentHitSet = getCurrentSortedHitSet();
                if (currentHitSet == null) {
                    output.error("No set of hits for highlighting.");
                } else {
                    Hits hitsInDoc = hits.getHitsInDoc(docId);
                    output.line(WordUtils.wrap(DocUtil.highlightDocument(index, contentsField, docId, hitsInDoc), 80));
                }
            } else if (lcased.startsWith("filter ") || lcased.equals("filter")) {
                if (cmd.length() <= 7) {
                    filterQuery = null; // clear filter
                    output.line("Filter cleared.");
                } else {
                    String filterExpr = cmd.substring(7);
                    try {
                        filterQuery = LuceneUtil.parseLuceneQuery(index, filterExpr, index.analyzer(), "title");
                        output.line("Filter created: " + filterQuery);
                        output.verbose(filterQuery.getClass().getName());
                    } catch (org.apache.lucene.queryparser.classic.ParseException e) {
                        output.error("Error parsing filter query: " + e.getMessage());
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
                output.line("Search defaults to "
                        + (sensitivity.isCaseSensitive() ? "case-sensitive" : "case-insensitive") + " and "
                        + (sensitivity.isDiacriticsSensitive() ? "diacritics-sensitive" : "diacritics-insensitive"));
            } else if (lcased.startsWith("doctitle ")) {
                boolean b = parseBoolean(lcased.substring(9));
                output.setShowDocTitle(b);
                System.out.println("Show document titles: " + (b ? "ON" : "OFF"));
            } else if (lcased.equals("struct") || lcased.equals("structure")) {
                output.showIndexMetadata(index);
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
                output.line("Switching to " + getCurrentParser().getName() + ".\n");
                output.printQueryHelp(getCurrentParser());
            } else if (lcased.equals("help") || lcased.equals("?")) {
                output.printHelp(getCurrentParser());
            } else if (lcased.startsWith("sleep")) {
                try {
                    Thread.sleep((int) (Float.parseFloat(lcased.substring(6)) * 1000));
                } catch (NumberFormatException e1) {
                    output.error("Sleep takes a float, the number of seconds to sleep");
                } catch (InterruptedException e) {
                    // OK
                }
            } else if (lcased.startsWith("wordlist")) {
                if (cmd.length() == 8) {
                    // Show loaded wordlists
                    output.line("Available word lists:");
                    for (String listName : wordLists.keySet()) {
                        output.line(" " + listName);
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
                        output.line("Loaded word list '" + name + "'");
                    } else {
                        if (wordLists.containsKey(fn)) {
                            // Display existing wordlist
                            for (String word : wordLists.get(fn)) {
                                output.line(" " + word);
                            }
                        } else {
                            output.error("File " + fn + " not found.");
                        }
                    }
                }
            } else if (lcased.startsWith("showconc ")) {
                boolean b = parseBoolean(lcased.substring(9));
                output.setShowConc(b);
                System.out.println("Show concordances: " + (b ? "ON" : "OFF"));
            } else if (lcased.equals("v") || lcased.startsWith("verbose ")) {
                if (lcased.equals("v"))
                    output.setVerbose(true);
                else
                    output.setVerbose(parseBoolean(lcased.substring(8)));
                if (output.isVerbose())
                    output.setShowMatchInfo(true);
                output.line("Verbose: " + (output.isVerbose() ? "ON" : "OFF"));
            } else if (lcased.startsWith("total ")) {
                String v = lcased.substring(6);
                determineTotalNumberOfHits = parseBoolean(v);
                output.line("Determine total number of hits: " + (determineTotalNumberOfHits ? "ON" : "OFF"));
            } else if (lcased.matches("^(patt)?field\\b.*")) {
                String v = lcased.replaceAll("^(patt)?field\\s?", "").trim();
                if (v.isEmpty()) {
                    contentsField = index.mainAnnotatedField();
                    output.line("Searching main annotated field: " + contentsField.name());
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
                        output.line("Searching annotated field: " + contentsField.name());
                    } else {
                        output.error("Annotated field '" + v + "' does not exist.");
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

    private Parser getCurrentParser() {
        return parsers.get(currentParserIndex);
    }

    private boolean parseBoolean(String v) {
        return v.equals("on") || v.equals("yes") || v.equals("true");
    }

    private void showContents(int docId) {
        if (!index.docExists(docId)) {
            output.line("Document " + docId + " does not exist.");
            return;
        }
        Document doc = index.luceneDoc(docId);
        ContentAccessor ca = index.contentAccessor(contentsField);
        output.line(ca.getDocumentContents(contentsField.name(), docId, doc));
    }

    private void showMetadata(int docId) {
        if (!index.docExists(docId)) {
            output.line("Document " + docId + " does not exist.");
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
                output.line(e.getKey() + ": " + StringUtils.abbreviate(value, 255) + " (total length: " + value.length() + ")");
            } else {
                output.line(e.getKey() + ": " + value);
            }
        }
    }

    Output output;

    CommandReader commandReader;

    /**
     * Show the program head.
     */
    private void printProgramHead() {
        output.line("BlackLab Query Tool");
        output.line("===================");
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
                        output.error("Word list '" + wordListName + "' not found!");
                        return;
                    }
                    int randomIndex = (int) (Math.random() * list.size());
                    regexMatcher.appendReplacement(resultString, list.get(randomIndex));
                }
                regexMatcher.appendTail(resultString);
                query = resultString.toString();
            }

            Parser parser = getCurrentParser();
            TextPattern pattern = parser.parse(index, query);
            if (pattern == null) {
                output.error("No query to execute.");
                return;
            }
            //pattern = pattern.rewrite();
            output.verbose("TextPattern: " + pattern);

            // If the query included filter clauses, use those. Otherwise use the global filter, if any.
            Query filter = parser.getIncludedFilterQuery();
            if (filter == null)
                filter = filterQuery;

            // Execute search
            BLSpanQuery spanQuery = pattern.toQuery(QueryInfo.create(index, contentsField), filter);
            output.verbose("SpanQuery: " + spanQuery.toString(contentsField.name()));
            try {
                output.verbose("Rewritten: " + spanQuery.rewrite(index.reader()).toString(contentsField.name()));
            } catch (IOException e) {
                throw new RuntimeException(e);
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
            output.error(e.getMessage());
            output.error("(Type 'help' for examples or see https://inl.github.io/BlackLab/development/query-tool.html)");
        } catch (UnsupportedOperationException e) {
            // Unimplemented part of query language used
            e.printStackTrace(); // DEBUG createWeight bug
            output.error("Cannot execute query; " + e.getMessage());
            output.error("(Type 'help' for examples or see https://inl.github.io/BlackLab/development/query-tool.html)");
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
            output.error("Sorting collocations not supported");
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
            output.error("Invalid hit sort criterium: " + sortBy
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
            output.error("Invalid group sort criterium: " + sortBy
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
        output.timings(timings);
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
            output.error("Unknown annotation: " + annotationName);
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
                output.error("Group doesn't exist");
                showWhichGroup = -1;
            } else
                showSetting = ShowSetting.HITS; // Show hits in group, not all the groups
        }
        showResultsPage();
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
            output.groups(groups, firstResult, resultsPerPage);
            break;
        case DOCS:
            showDocsPage();
            break;
        default:
            showHitsPage();
            break;
        }
        output.timings(timings);
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
        output.collocations(collocations, firstResult, resultsPerPage);
    }

    private void showDocsPage() {
        Hits currentHitSet = getCurrentHitSet();
        if (docs == null) {
            if (currentHitSet != null)
                docs = currentHitSet.perDocResults(Results.NO_LIMIT);
            else if (filterQuery != null) {
                docs = index.queryDocuments(filterQuery);
            } else {
                output.line("No documents to show (set filterquery or search for hits first)");
                return;
            }
        }
        output.docs(docs.window(firstResult, resultsPerPage), docs.size());
    }

    /**
     * Show the current page of hits.
     */
    private void showHitsPage() {
        timings.start();
        Hits hitsToShow = getCurrentSortedHitSet();
        if (hitsToShow == null)
            return; // nothing to show
        output.hits(hitsToShow.window(firstResult, resultsPerPage), this);
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

    public QueryTimings getTimings() {
        return timings;
    }

    public ConcordanceType getConcType() {
        return concType;
    }

    public ContextSize getContextSize() {
        return contextSize;
    }

    public boolean isStripXml() {
        return stripXML;
    }

    public AnnotatedField getContentsField() {
        return contentsField;
    }

    public boolean isDetermineTotalNumberOfHits() {
        return determineTotalNumberOfHits;
    }

    public long getResultsPerPage() {
        return resultsPerPage;
    }

}
