package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import nl.inl.util.FileUtil;

class Config {
    /**
     * Get a Config object with an error message.
     *
     * @param error the error message
     * @return the Config object
     */
    public static Config error(String error) {
        Config config = new Config();
        config.error = error;
        return config;
    }

    /**
     * Get Config object from command line arguments AND configure output object.
     *
     * @param args   command line arguments
     * @param output output object to configure (and write messages to)
     * @return the Config object
     */
    public static Config fromCommandline(String[] args, Output output) {
        // Parse command line
        Config config = new Config();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--")) {
                if (arg.equals("--mode")) {
                    if (i + 1 == args.length) {
                        return error("--mode option needs argument");
                    }
                    String mode = args[i + 1].toLowerCase();
                    if (mode.matches("c(orrectness)?")) {
                        // Correctness testing: we want results, no timing and larger pagesize
                        output.setShowOutput(true);
                        config.showStats = false;
                        QueryToolImpl.defaultPageSize = 1000;
                        QueryToolImpl.alwaysSortBy = "after:word:s,hitposition"; // for reproducibility
                        output.setShowDocIds(false); // doc ids are randomly assigned
                        output.setShowMatchInfo(false); // (temporary)
                    } else if (mode.matches("p(erformance)?")) {
                        // Performance testing: we want timing and no results
                        output.setShowOutput(false);
                        config.showStats = true;
                    } else if (mode.matches("a(ll)?")) {
                        // Regular: we want results and timing
                        output.setShowOutput(true);
                        config.showStats = true;
                        output.setShowMatchInfo(true);
                    } else {
                        return error("Unknown mode: " + mode);
                    }
                    i++;
                }
            } else if (arg.startsWith("-")) {
                switch (arg) {
                case "-e":
                    if (i + 1 == args.length) {
                        return error("-e option needs argument");
                    }
                    config.encoding = args[i + 1];
                    i++;
                    break;
                case "-f":
                    if (i + 1 == args.length) {
                        return error("-f option needs argument");
                    }
                    config.inputFile = new File(args[i + 1]);
                    i++;
                    output.error("Batch mode; reading commands from " + config.inputFile);
                    break;
                case "-v":
                    output.setVerbose(true);
                    output.setShowMatchInfo(true);
                    break;
                default:
                    return error("Unknown option: " + arg);
                }
            } else {
                if (config.indexDir != null) {
                    return error("Can only specify 1 index directory");
                }
                config.indexDir = new File(arg);
            }
        }
        if (config.indexDir == null)
            return error("No index directory specified");
        if (!config.indexDir.exists() || !config.indexDir.isDirectory())
            return error("Index dir " + config.indexDir.getPath() + " doesn't exist.");

        // By default we don't show stats in batch mode, but we do in interactive mode
        // (batch mode is useful for correctness testing, where you don't want stats;
        //  use --mode performance to get stats but no results in batch mode)
        boolean showStatsDefaultValue = config.inputFile == null;
        output.setShowStats(config.showStats == null ? showStatsDefaultValue : config.showStats);

        // Use correct output encoding
        try {
            // Yes
            output.setOutputWriter(new PrintWriter(new OutputStreamWriter(System.out, config.encoding), true));
            output.setErrorWriter(new PrintWriter(new OutputStreamWriter(System.err, config.encoding), true));
            output.line("Using output encoding " + config.encoding + "\n");
        } catch (UnsupportedEncodingException e) {
            // Nope; fall back to default
            output.setOutputWriter(new PrintWriter(new OutputStreamWriter(System.out, Charset.defaultCharset()), true));
            output.setErrorWriter(new PrintWriter(new OutputStreamWriter(System.err, Charset.defaultCharset()), true));
            output.error("Unknown encoding " + config.encoding + "; using default");
        }

        if (config.inputFile != null)
            output.setBatchMode(true);

        return config;
    }

    private String error = null;
    private File indexDir = null;
    private File inputFile = null;
    private String encoding = Charset.defaultCharset().name();
    private Boolean showStats = null; // default not overridden (default depends on batch mode or not)

    public String getError() {
        return error;
    }

    public File getIndexDir() {
        return indexDir;
    }

    public BufferedReader getInput() throws UnsupportedEncodingException, FileNotFoundException {
        return inputFile == null ?
                new BufferedReader(new InputStreamReader(System.in, encoding)) :
                FileUtil.openForReading(inputFile, QueryToolImpl.INPUT_FILE_ENCODING);
    }
}
