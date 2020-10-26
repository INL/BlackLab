package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import io.dropwizard.metrics5.Clock;
import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.Histogram;
import io.dropwizard.metrics5.Meter;
import io.dropwizard.metrics5.MetricFilter;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.ScheduledReporter;
import io.dropwizard.metrics5.Snapshot;
import io.dropwizard.metrics5.Timer;
import nl.inl.blacklab.config.BLConfigIndexing;
import nl.inl.blacklab.config.BLConfigLog;
import nl.inl.blacklab.config.BlackLabConfig;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.DownloadCache;
import nl.inl.blacklab.index.PluginManager;
import nl.inl.blacklab.index.ZipHandleManager;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.util.FileUtil;

/**
 * Main BlackLab class, from which indexes can be opened.
 *
 * You can either open indices using the static methods in this class,
 * or you can create() a BlackLabEngine and use that to open indices.
 *
 * The first approach will implicitly create a default BlackLabEngine
 * in the background, with 4 search threads. If you want a different
 * number of search threads, call create() to create your own instance
 * of BlackLabEngine.
 *
 * Don't try to mix these two methods; if an implicit engine exists and
 * you call create(), or if you call e.g. BlackLab.open() when you've
 * already created an engine explicitly, an exception will be thrown.
 *
 * If you explicitly create an engine, make sure to close it when you're
 * done. For the implicit engine, this is done automatically when you
 * close your last index.
 */
public final class BlackLab {
    // REPORTING
    public static final MetricRegistry metrics = new MetricRegistry();


    private static final Logger logger = LogManager.getLogger(BlackLab.class);

    private static final int DEFAULT_NUM_SEARCH_THREADS = 4;

    private static final int DEFAULT_MAX_THREADS_PER_SEARCH = 2;

    /**
     * If client doesn't explicitly create a BlackLab instance, one will be instantiated
     * automatically.
     */
    static BlackLabEngine implicitInstance = null;

    /**
     * Have we called create()? If so, don't create an implicit instance, but throw an exception.
     */
    private static boolean explicitlyCreated = false;

    /**
     * Map from IndexReader to BlackLab, for use from inside SpanQuery/Spans classes
     */
    static final Map<IndexReader, BlackLabEngine> blackLabFromIndexReader = new IdentityHashMap<>();

    /** Cache for getConfigDirs() */
    private static List<File> configDirs;

    /** BlackLab configuration */
    private static BlackLabConfig blackLabConfig = null;

    private static boolean globalSettingsApplied = false;

    public static BlackLabEngine createEngine(int searchThreads, int maxThreadsPerSearch) {
        if (implicitInstance != null)
            throw new UnsupportedOperationException("BlackLab.create() called, but an implicit instance exists already! Don't mix implicit and explicit BlackLabEngine!");
        explicitlyCreated = true;


        ScheduledReporter rep = new ScheduledReporter(BlackLab.metrics, "reporter", MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS) {
            private PrintStream output = System.out;
            private Locale locale = Locale.getDefault();
            private DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale);
            private Clock clock = Clock.defaultClock();

            @Override
            public void report(SortedMap<MetricName, Gauge> gauges, SortedMap<MetricName, Counter> counters,
                    SortedMap<MetricName, Histogram> histograms, SortedMap<MetricName, Meter> meters,
                    SortedMap<MetricName, Timer> timers)
            {
                final String dateTime = dateFormat.format(new Date(clock.getTime()));
                printWithBanner(dateTime, '=');
                output.println();

                if (!gauges.isEmpty()) {
                    printWithBanner("-- Gauges", '-');
                    for (Entry<MetricName, Gauge> entry : gauges.entrySet()) {
                        output.println(entry.getKey());
                        printGauge(entry);
                    }
                    output.println();
                }

                if (!counters.isEmpty()) {
                    printWithBanner("-- Counters", '-');
                    for (Entry<MetricName, Counter> entry : counters.entrySet()) {
                        output.println(entry.getKey());
                        printCounter(entry);
                    }
                    output.println();
                }

                if (!histograms.isEmpty()) {
                    printWithBanner("-- Histograms", '-');
                    for (Entry<MetricName, Histogram> entry : histograms.entrySet()) {
                        output.println(entry.getKey());
                        printHistogram(entry.getValue());
                    }
                    output.println();
                }

                if (!meters.isEmpty()) {
                    printWithBanner("-- Meters", '-');
                    for (Entry<MetricName, Meter> entry : meters.entrySet()) {
                        output.println(entry.getKey());
                        printMeter(entry.getValue());
                    }
                    output.println();
                }

                if (!timers.isEmpty()) {
                    printWithBanner("-- Timers", '-');
                    for (Entry<MetricName, Timer> entry : timers.entrySet()) {
                        output.println(entry.getKey());
                        printTimer(entry.getValue());
                    }
                    output.println();
                }

                output.println();
                output.flush();
            }

            private void printMeter(Meter meter) {
                output.printf(locale, "             count = %d%n", meter.getCount());
                output.printf(locale, "         mean rate = %2.2f events/%s%n", convertRate(meter.getMeanRate()), getRateUnit());
                output.printf(locale, "     1-minute rate = %2.2f events/%s%n", convertRate(meter.getOneMinuteRate()), getRateUnit());
                output.printf(locale, "     5-minute rate = %2.2f events/%s%n", convertRate(meter.getFiveMinuteRate()), getRateUnit());
                output.printf(locale, "    15-minute rate = %2.2f events/%s%n", convertRate(meter.getFifteenMinuteRate()), getRateUnit());
            }

            private void printCounter(Entry<MetricName, Counter> entry) {
                output.printf(locale, "             count = %d%n", entry.getValue().getCount());            }

            private void printGauge(Entry<MetricName, Gauge> entry) {
                output.printf(locale, "             value = %s%n", entry.getValue().getValue());            }

            private void printHistogram(Histogram histogram) {
                output.printf(locale, "             count = %d%n", histogram.getCount());
                Snapshot snapshot = histogram.getSnapshot();
                output.printf(locale, "               min = %d%n", snapshot.getMin());
                output.printf(locale, "               max = %d%n", snapshot.getMax());
                output.printf(locale, "              mean = %2.2f%n", snapshot.getMean());
                output.printf(locale, "            stddev = %2.2f%n", snapshot.getStdDev());
                output.printf(locale, "            median = %2.2f%n", snapshot.getMedian());
                output.printf(locale, "              75%% <= %2.2f%n", snapshot.get75thPercentile());
                output.printf(locale, "              95%% <= %2.2f%n", snapshot.get95thPercentile());
                output.printf(locale, "              98%% <= %2.2f%n", snapshot.get98thPercentile());
                output.printf(locale, "              99%% <= %2.2f%n", snapshot.get99thPercentile());
                output.printf(locale, "            99.9%% <= %2.2f%n", snapshot.get999thPercentile());
            }

            private void printTimer(Timer timer) {
                final Snapshot snapshot = timer.getSnapshot();
                output.printf(locale, "             count = %d%n", timer.getCount());
//                output.printf(locale, "         mean rate = %2.2f calls/%s%n", convertRate(timer.getMeanRate()), getRateUnit());
//                output.printf(locale, "     1-minute rate = %2.2f calls/%s%n", convertRate(timer.getOneMinuteRate()), getRateUnit());
//                output.printf(locale, "     5-minute rate = %2.2f calls/%s%n", convertRate(timer.getFiveMinuteRate()), getRateUnit());
//                output.printf(locale, "    15-minute rate = %2.2f calls/%s%n", convertRate(timer.getFifteenMinuteRate()), getRateUnit());

                output.printf(locale, "               min = %2.2f %s%n", convertDuration(snapshot.getMin()), getDurationUnit());
                output.printf(locale, "               max = %2.2f %s%n", convertDuration(snapshot.getMax()), getDurationUnit());
                output.printf(locale, "              mean = %2.2f %s%n", convertDuration(snapshot.getMean()), getDurationUnit());
                output.printf(locale, "             total = %2.2f %n", timer.getSum() / 1000000f);
//                output.printf(locale, "            stddev = %2.2f %s%n", convertDuration(snapshot.getStdDev()), getDurationUnit());
//                output.printf(locale, "            median = %2.2f %s%n", convertDuration(snapshot.getMedian()), getDurationUnit());
//                output.printf(locale, "              75%% <= %2.2f %s%n", convertDuration(snapshot.get75thPercentile()), getDurationUnit());
//                output.printf(locale, "              95%% <= %2.2f %s%n", convertDuration(snapshot.get95thPercentile()), getDurationUnit());
//                output.printf(locale, "              98%% <= %2.2f %s%n", convertDuration(snapshot.get98thPercentile()), getDurationUnit());
//                output.printf(locale, "              99%% <= %2.2f %s%n", convertDuration(snapshot.get99thPercentile()), getDurationUnit());
//                output.printf(locale, "            99.9%% <= %2.2f %s%n", convertDuration(snapshot.get999thPercentile()), getDurationUnit());
            }

            private void printWithBanner(String s, char c) {
                output.print(s);
                output.print(' ');
                for (int i = 0; i < (80 - s.length() - 1); i++) {
                    output.print(c);
                }
                output.println();
            }


        };

        rep.start(5, TimeUnit.SECONDS);

        return new BlackLabEngine(searchThreads, maxThreadsPerSearch);
    }

    public static BlackLabEngine createEngine() {
        return createEngine(DEFAULT_NUM_SEARCH_THREADS, DEFAULT_MAX_THREADS_PER_SEARCH);
    }

    public static BlackLabIndex open(File dir) throws ErrorOpeningIndex {
        return BlackLabIndex.open(implicitInstance(), dir);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @return index writer
     * @throws ErrorOpeningIndex if the index could not be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex) throws ErrorOpeningIndex {
        return openForWriting(indexDir, createNewIndex, (File) null);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param indexTemplateFile JSON template to use for index structure / metadata
     * @return index writer
     * @throws ErrorOpeningIndex if index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(implicitInstance(), indexDir, true, createNewIndex, indexTemplateFile);
    }

    /**
     * Open an index for writing ("index mode": adding/deleting documents).
     *
     * @param indexDir the index directory
     * @param createNewIndex if true, create a new index even if one existed there
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @return index writer
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public static BlackLabIndexWriter openForWriting(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws ErrorOpeningIndex {
        return new BlackLabIndexImpl(BlackLab.implicitInstance(), indexDir, true, createNewIndex, config);
    }

    /**
     * Create an empty index.
     *
     * @param indexDir where to create the index
     * @param config format configuration for this index; used to base the index
     *            metadata on
     * @return a BlackLabIndexWriter for the new index, in index mode
     * @throws ErrorOpeningIndex if the index couldn't be opened
     */
    public static BlackLabIndexWriter create(File indexDir, ConfigInputFormat config) throws ErrorOpeningIndex {
        return openForWriting(indexDir, true, config);
    }


    /**
     * Return the implicitly created instance of BlackLab.
     *
     * Only used by BlackLabIndex if no BlackLab instance is provided during opening.
     * The instance will have 4 search threads.
     *
     * @return implicitly instantiated BlackLab instance
     */
    public static synchronized BlackLabEngine implicitInstance() {
        if (explicitlyCreated)
            throw new UnsupportedOperationException("Already called create(); cannot create an implicit instance anymore! Don't mix implicit and explicit BlackLabEngine!");
        if (implicitInstance == null) {
            implicitInstance = new BlackLabEngine(DEFAULT_NUM_SEARCH_THREADS, DEFAULT_MAX_THREADS_PER_SEARCH);
        }
        return implicitInstance;
    }

    public static synchronized BlackLabIndex fromIndexReader(IndexReader reader) {
        return blackLabFromIndexReader.get(reader).indexFromReader(reader);
    }

    /**
     * Return a list of directories that should be searched for BlackLab-related
     * configuration files.
     *
     * May be used by applications to locate BlackLab-related configuration, such as
     * input format definition files or other configuration files. IndexTool and
     * BlackLab Server use this.
     *
     * The directories returned are (in decreasing priority): - $BLACKLAB_CONFIG_DIR
     * (if env. var. is defined) - $HOME/.blacklab - /etc/blacklab -
     * /vol1/etc/blacklab (legacy, will be removed) - /tmp (legacy, will be removed)
     *
     * A convenient method to use with this is
     * {@link FileUtil#findFile(List, String, List)}.
     *
     * @return list of directories to search in decreasing order of priority
     */
    public synchronized static List<File> defaultConfigDirs() {
        if (configDirs == null) {
            configDirs = new ArrayList<>();
            String strConfigDir = System.getenv("BLACKLAB_CONFIG_DIR");
            if (strConfigDir != null && strConfigDir.length() > 0) {
                File configDir = new File(strConfigDir);
                if (configDir.exists()) {
                    if (!configDir.canRead())
                        logger.warn("BLACKLAB_CONFIG_DIR points to a unreadable directory: " + strConfigDir);
                    configDirs.add(configDir);
                } else {
                    logger.warn("BLACKLAB_CONFIG_DIR points to a non-existent directory: " + strConfigDir);
                }
            }
            configDirs.add(new File(System.getProperty("user.home"), ".blacklab"));
            configDirs.add(new File("/etc/blacklab"));
            configDirs.add(new File("/vol1/etc/blacklab")); // TODO: remove, INT-specific
            configDirs.add(new File(System.getProperty("java.io.tmpdir")));
        }
        return new ArrayList<>(configDirs);
    }

    /**
     * Get the BlackLab config.
     *
     * If no config has been set, return a default configuration.
     *
     * @return currently set config
     */
    public synchronized static BlackLabConfig config() {
        if (blackLabConfig == null) {
            blackLabConfig = new BlackLabConfig();
        }
        return blackLabConfig;
    }

    /**
     * Read blacklab.yaml and set the configuration from that.
     *
     * This must be called before you open the first index, or an exception will be thrown,
     * because another default config has been applied already.
     */
    public static void setConfigFromFile() {
        if (globalSettingsApplied)
            throw new UnsupportedOperationException("Cannot set default configuration - another configuration has already been applied.");

        File file = FileUtil.findFile(defaultConfigDirs(), "blacklab", Arrays.asList("yaml", "yml", "json"));
        if (file != null) {
            try {
                blackLabConfig = BlackLabConfig.readConfigFile(file);
            } catch (IOException e) {
                logger.warn("Could not load default blacklab configuration file " + file + ": "
+ e.getMessage());
            }
        }
    }

    /**
     * Set the BlackLab configuration to use.
     *
     * This must be called before you open the first index, or an exception will be thrown,
     * because another default config has been applied already.
     *
     * @param config configuration to use
     */
    public static void setConfig(BlackLabConfig config) {
        if (globalSettingsApplied)
            throw new UnsupportedOperationException("Cannot set default configuration - another configuration has already been applied.");

        blackLabConfig = config;
    }

    /**
     * Configure the index according to the blacklab configuration.
     *
     * @param index
     * @throws IOException
     */
    public synchronized static void applyConfigToIndex(BlackLabIndex index) throws IOException {        ensureGlobalConfigApplied();

        // Apply search settings from the config to this BlackLabIndex
        blackLabConfig.getSearch().apply(index);
    }

    private synchronized static void ensureGlobalConfigApplied() {
        if (!globalSettingsApplied) {

            BlackLabConfig blackLabConfig = config();
            // Indexing settings
            BLConfigIndexing indexing = blackLabConfig.getIndexing();
            DownloadCache.setDownloadAllowed(indexing.isDownloadAllowed());
            DownloadCache.setMaxFileSizeMegs(indexing.getDownloadCacheMaxFileSizeMegs());
            if (indexing.getDownloadCacheDir() != null)
                    DownloadCache.setDir(new File(indexing.getDownloadCacheDir()));
            ZipHandleManager.setMaxOpen(indexing.getZipFilesMaxOpen());

            // Plugins settings
            PluginManager.initPlugins(blackLabConfig.getPlugins());

            // Log settings
            BLConfigLog log = blackLabConfig.getLog();
            if (log.getTrace().isIndexOpening())
                BlackLabIndexImpl.setTraceIndexOpening(true);
            if (log.getTrace().isOptimization())
                BlackLabIndexImpl.setTraceOptimization(true);
            if (log.getTrace().isQueryExecution())
                BlackLabIndexImpl.setTraceQueryExecution(true);

            globalSettingsApplied = true;
        }
    }

    private BlackLab() { }

}