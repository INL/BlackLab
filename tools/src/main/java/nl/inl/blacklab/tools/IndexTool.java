package nl.inl.blacklab.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.WordUtils;
import org.apache.lucene.queryparser.classic.ParseException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataExternal;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldsWriter;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexTool {

    static final Map<String, String> indexerParam = new TreeMap<>();

    public static void main(String[] args) throws ErrorOpeningIndex, ParseException {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        // If the current directory contains indexer.properties, read it
        File propFile = new File(".", "indexer.properties");
        if (propFile.canRead())
            readParametersFromPropertiesFile(propFile);

        // Parse command line
        int maxDocsToIndex = 0;
        File indexDir = null, inputDir = null;
        String glob = "*";
        String formatIdentifier = null;
        boolean forceCreateNew = false;
        String command = "";
        Set<String> commands = new HashSet<>(Arrays.asList("add", "create", "delete", "indexinfo"));
        boolean addingFiles = true;
        String deleteQuery = null;
        int numberOfThreadsToUse = BlackLab.config().getIndexing().getNumberOfThreads();
        List<File> linkedFileDirs = new ArrayList<>();
        IndexType indexType = null; // null means "use default"
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("---")) {
                String name = arg.substring(3);
                if (i + 1 == args.length) {
                    System.err.println("Passing parameter to indexer: argument needed!");
                    usage();
                    return;
                }
                // create --nothreads --integrate-external-files true E:/code/ivdnt/data/corpora/gysseling E:/code/ivdnt/data/to-import/gysseling E:/code/ivdnt/data/interface/gysseling/gysseling.blf.yaml
                i++;
                String value = args[i];
                indexerParam.put(name, value);
            } else if (arg.startsWith("--")) {
                String name = arg.substring(2);
                switch (name) {
                case "integrate-external-files":
                    if (i + 1 == args.length || !List.of("true", "false").contains(args[i + 1].toLowerCase())) {
                        System.err.println("--integrate-external-files needs a parameter, true or false.");
                        usage();
                        return;
                    }
                    indexType = Boolean.parseBoolean(args[i + 1]) ? IndexType.INTEGRATED : IndexType.EXTERNAL_FILES;
                    i++;
                    break;
                case "threads":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            numberOfThreadsToUse = Integer.parseInt(args[i + 1]);
                            i++;
                        } catch (NumberFormatException e) {
                            System.err.println("Specify a valid integer for --threads option. Using default of 2.");
                            numberOfThreadsToUse = 2;
                        }
                    } else
                        numberOfThreadsToUse = 2;
                    break;
                case "nothreads":
                    numberOfThreadsToUse = 1;
                    break;
                case "linked-file-dir":
                    if (i + 1 == args.length) {
                        System.err.println("--linked-file-dir option needs argument");
                        usage();
                        return;
                    }
                    linkedFileDirs.add(new File(args[i + 1]));
                    i++;
                    break;
                case "maxdocs":
                    if (i + 1 == args.length) {
                        System.err.println("--maxdocs option needs argument");
                        usage();
                        return;
                    }
                    try {
                        maxDocsToIndex = Integer.parseInt(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        System.err.println("--maxdocs option needs integer argument");
                        usage();
                        return;
                    }
                    break;
                case "create":
                    System.err.println("Option --create is deprecated; use create command (--help for details)");
                    forceCreateNew = true;
                    break;
                case "indexparam":
                    if (i + 1 == args.length) {
                        System.err.println("--indexparam option needs argument");
                        usage();
                        return;
                    }
                    propFile = new File(args[i + 1]);
                    if (!propFile.canRead()) {
                        System.err.println("Cannot read " + propFile);
                        usage();
                        return;
                    }
                    readParametersFromPropertiesFile(propFile);
                    i++;
                    break;
                case "help":
                    usage();
                    return;
                default: {
                    System.err.println("Unknown option --" + name);
                    usage();
                    return;
                }
                }
            } else {
                if (command.length() == 0 && commands.contains(arg)) {
                    command = arg;
                    addingFiles = command.equals("add") || command.equals("create");
                } else if (indexDir == null) {
                    indexDir = new File(arg);
                } else if (addingFiles && inputDir == null) {
                    if (arg.startsWith("\"") && arg.endsWith("\"")) {
                        // Trim off extra quotes needed to pass file glob to
                        // Windows JVM.
                        arg = arg.substring(1, arg.length() - 1);
                    }
                    if (arg.contains("*") || arg.contains("?") || new File(arg).isFile()) {
                        // Contains file glob. Separate the two components.
                        int n = arg.lastIndexOf('/', arg.length() - 2);
                        if (n < 0)
                            n = arg.lastIndexOf('\\', arg.length() - 2);
                        if (n < 0) {
                            glob = arg;
                            inputDir = new File(".");
                        } else {
                            glob = arg.substring(n + 1);
                            inputDir = new File(arg.substring(0, n));
                        }
                    } else {
                        inputDir = new File(arg);
                    }
                } else if (addingFiles && formatIdentifier == null) {
                    formatIdentifier = arg;
                } else if (command.equals("delete") && deleteQuery == null) {
                    deleteQuery = arg;
                } else {
                    System.err.println("Too many arguments!");
                    usage();
                    return;
                }
            }
        }
        if (indexDir == null) {
            System.err.println("No index dir given.");
            usage();
            return;
        }

        // Check the command
        if (command.length() == 0) {
            System.err.println("No command specified; specify 'create' or 'add'. (--help for details)");
            usage();
            return;
            //System.err.println("No command specified; assuming \"add\" (--help for details)");
            //command = "add";
        }
        switch (command) {
        case "indexinfo":
            exportIndexInfo(indexDir);
            return;
        case "delete":
            commandDelete(indexDir, deleteQuery);
            return;
        case "create":
            forceCreateNew = true;
            break;
        }

        // We're adding files. Do we have an input dir/file and file format name?
        if (inputDir == null) {
            System.err.println("No input dir given.");
            usage();
            return;
        }

        // Init log4j
        LogUtil.setupBasicLoggingConfig();

        File indexDirParent = indexDir.getAbsoluteFile().getParentFile();
        File inputDirParent = inputDir.getAbsoluteFile().getParentFile();
        List<File> dirs = new ArrayList<>(Arrays.asList(new File("."), inputDir, indexDir));
        if (inputDirParent != null)
            dirs.add(2, inputDirParent);
        if (indexDirParent != null && !dirs.contains(indexDirParent))
            dirs.add(indexDirParent);
        propFile = FileUtil.findFile(dirs, "indexer", List.of("properties"));
        if (propFile != null && propFile.canRead())
            readParametersFromPropertiesFile(propFile);

        File indexTemplateFile = null;
        if (forceCreateNew) {
            indexTemplateFile = FileUtil.findFile(dirs, "indextemplate", Arrays.asList("json", "yaml", "yml"));
        }

        String op = forceCreateNew ? "Creating new" : "Appending to";
        String strGlob = File.separator;
        if (glob != null && glob.length() > 0 && !glob.equals("*")) {
            strGlob += glob;
        }
        System.out.println(op + " index in " + indexDir + File.separator + " from " + inputDir + strGlob +
                (formatIdentifier != null ? " (using format " + formatIdentifier + ")" : "(using autodetected format)"));
        if (!indexerParam.isEmpty()) {
            System.out.println("Indexer parameters:");
            for (Map.Entry<String, String> e : indexerParam.entrySet()) {
                System.out.println("  " + e.getKey() + ": " + e.getValue());
            }
        }

        // Make sure BlackLab can find our format configuration files
        // (by default, it will already look in $BLACKLAB_CONFIG_DIR/formats, $HOME/.blacklab/formats
        //  and /etc/blacklab/formats, but we also want it to look in the current dir, the input dir,
        //  and the parent(s) of the input and index dirs)
        File currentWorkingDir = new File(System.getProperty("user.dir"));
        List<File> formatDirs = new ArrayList<>(Arrays.asList(currentWorkingDir, inputDirParent, inputDir));
        if (!formatDirs.contains(indexDirParent))
            formatDirs.add(indexDirParent);

        DocumentFormats.registerFormatsInDirectories(formatDirs);


        // Create the indexer and index the files
        if (!forceCreateNew || indexTemplateFile == null || !indexTemplateFile.canRead()) {
            indexTemplateFile = null;
        }
        // First check if the format is a file: if so, load it before continuing.
        if (formatIdentifier != null && !DocumentFormats.isSupported(formatIdentifier)) {
            File maybeFormatFile = new File(formatIdentifier);
            if (maybeFormatFile.isFile() && maybeFormatFile.canRead()) {
                try {
                    ConfigInputFormat format = new ConfigInputFormat(maybeFormatFile, null);
                    DocumentFormats.registerFormat(format);
                    formatIdentifier = format.getName();
                } catch (IOException e) {
                    System.err.println("Not a format, not a valid file: " + formatIdentifier + " . " + e.getMessage());
                    System.err.println("Please specify a correct format on the command line.");
                    usage();
                    return;
                }
            }
        }

        Indexer indexer = null;
        try {
            BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, forceCreateNew,
                formatIdentifier, indexTemplateFile, indexType);
            indexer = Indexer.create(indexWriter, formatIdentifier);
        } catch (DocumentFormatNotFound e) {
            System.err.println(e.getMessage());
            usage();
            return;
        }

        indexer.setNumberOfThreadsToUse(numberOfThreadsToUse);
        if (forceCreateNew)
            indexer.indexWriter().metadata().setDocumentFormat(formatIdentifier);
        indexer.setIndexerParam(indexerParam);
        if (maxDocsToIndex > 0)
            indexer.setMaxNumberOfDocsToIndex(maxDocsToIndex);
        indexer.setLinkedFileDirs(linkedFileDirs);
        try {
            if (glob.contains("*") || glob.contains("?")) {
                // Real wildcard glob
                indexer.index(inputDir, glob);
            } else {
                // Single file.
                indexer.index(new File(inputDir, glob));
                MetadataFieldsWriter mf = indexer.indexWriter().metadata().metadataFields();
            }
        } catch (Exception e) {
            System.err.println(
                    "An error occurred, aborting indexing (changes will be rolled back). Error details follow.");
            e.printStackTrace();
            indexer.rollback();
        } finally {
            System.out.println("Saving index, please wait...");
            // Close the index.
            indexer.close();
            System.out.println("Finished!");
        }
    }

    private static void readParametersFromPropertiesFile(File propFile) {
        Properties p = readPropertiesFromFile(propFile);
        for (Map.Entry<Object, Object> e : p.entrySet()) {
            indexerParam.put(e.getKey().toString(), e.getValue().toString());
        }
    }

    private static void exportIndexInfo(File indexDir) {
        try (BlackLabIndex index = BlackLab.open(indexDir)) {

            String indexmetadata = index.metadata().getIndexMetadataAsString();
            File indexMetadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ".json");
            System.out.println("Writing " + indexMetadataFile);
            FileUtils.write(indexMetadataFile, indexmetadata, StandardCharsets.UTF_8);

            String indexInfo =
                    "documentCount: " + index.metadata().documentCount() + "\n" +
                    "tokenCount: " + index.metadata().tokenCount() + "\n";
            File indexInfoFile = new File(indexDir, "indexinfo.yaml");
            System.out.println("Writing " + indexInfoFile);
            FileUtils.write(indexInfoFile, indexInfo, StandardCharsets.UTF_8);

            ForkJoinPool.commonPool().shutdownNow(); // terminate any background initialization (e.g. sort terms)
            ForkJoinPool.commonPool().awaitTermination(10, TimeUnit.SECONDS);
            System.err.println("BLA");

        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    private static void commandDelete(File indexDir, String deleteQuery) throws ErrorOpeningIndex, ParseException {
        if (deleteQuery == null) {
            System.err.println("No delete query given.");
            usage();
            return;
        }
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, false)) {
            System.out.println("Doing delete: " + deleteQuery);
            indexWriter.delete(LuceneUtil.parseLuceneQuery(deleteQuery, indexWriter.analyzer(), "nonExistentDefaultField"));
        }
    }

    private static void usage() {
        System.err.flush();
        System.out.flush();
        System.out
                .println("Usage:\n"
                        + "  IndexTool {add|create} [options] <indexdir> <inputdir> <format>\n"
                        + "  IndexTool delete <indexdir> <filterQuery>\n"
                        + "\n"
                        + "Options:\n"
                        + "  --maxdocs <n>                  Stop after indexing <n> documents\n"
                        + "  --linked-file-dir <d>          Look in directory <d> for linked (e.g. metadata) files\n"
                        + "  --nothreads                    Disable multithreaded indexing (enabled by default)\n"
                        + "  --threads <n>                  Number of threads to use\n"
                        + "  --integrate-external-files <b> Enable integrating external files into lucene index (disabled by default)\n"
                        + "\n"
                        + "Available input format configurations:");
        for (Format format : DocumentFormats.getFormats()) {
            String name = format.getId();
            String displayName = format.getDisplayName();
            String desc = format.getDescription();
            String url = format.getHelpUrl();
            if (!url.isEmpty())
                url = "\n      (see " + url + ")";
            if (displayName.length() > 0)
                displayName = " (" + displayName + ")";
            if (desc.length() > 0) {
                desc = "\n      " + WordUtils.wrap(desc, 75, "\n      ", false);
            }
            System.out.println("  " + name + displayName + desc + url);
        }
    }

    /**
     * Read Properties from the specified file
     *
     * @param file the file to read
     * @return the Properties read
     */
    public static Properties readPropertiesFromFile(File file) {
        try {
            if (!file.isFile()) {
                throw new IllegalArgumentException("Annotation file " + file.getCanonicalPath()
                        + " does not exist or is not a regular file!");
            }

            try (Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1))) {
                Properties properties = new Properties();
                properties.load(in);
                return properties;
            }
        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }
}
