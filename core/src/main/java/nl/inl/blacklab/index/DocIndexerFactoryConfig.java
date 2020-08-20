package nl.inl.blacklab.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.DocIndexerConfig;
import nl.inl.blacklab.indexers.config.InputFormatReader;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;

/**
 * A DocIndexerFactory implementation that automatically adds configurations in
 * the formats/ dir in the jar and scans the default config directories for
 * subdirectories called "config", then loads any configurations within those
 * directories. Instances of this class can be used to add additional configs
 * within customized directories.
 */
public class DocIndexerFactoryConfig implements DocIndexerFactory {
    static final Logger logger = LogManager.getLogger(DocIndexerConfig.class);

    protected boolean isInitialized = false;

    // Entries here are short-lived, the list should be clean as long as there is no scanDirectories call running
    protected Map<String, File> unloaded = new HashMap<>();

    protected Map<String, ConfigInputFormat> supported = new HashMap<>();

    protected Map<String, String> formatErrors = new HashMap<>();
    
    /**
     * Return a config from the supported list, or load it if it's in the unloaded
     * list.
     * <p>
     * Why this function? Formats can depend on each other, and this is a mechanism
     * to allow formats to get/lazy-load other formats registered with this factory.
     * Why lazy-loading? Formats only refer to other formats by their name, not
     * their file location, so we need to find them all before we actually load
     * them. Those found config files are kept in the
     * {@link DocIndexerFactoryConfig#unloaded} map until they are loaded
     */
    protected Function<String, Optional<ConfigInputFormat>> finder = formatIdentifier -> {
        // Give our wrapping DocIndexerFactory a chance to load a new format (in case it's a derived class)
        if (!isSupported(formatIdentifier))
            return Optional.empty();

        if (unloaded.containsKey(formatIdentifier)) {
            File f = unloaded.get(formatIdentifier);
            // remove before load to avoid infinite recursion on circular dependencies
            unloaded.remove(formatIdentifier);
            try {
                return load(formatIdentifier, f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // If unknown return null, can't help it.
        return Optional.ofNullable(supported.get(formatIdentifier));
    };

    @Override
    public void init() throws InvalidInputFormatConfig {
        if (isInitialized)
            return;
        isInitialized = true;

        // Note that these names should not collide with the abbreviations used by DocIndexerFactoryClass, 
        // or this will override those classes.
        String[] formats = { "chat", "cmdi", "csv", "eaf", "folia", "sketch-wpl", "tcf", "tei-p4", "tei", "tsv-frog",
                "tsv", "txt" };
        for (String formatIdentifier : formats) {
            try (InputStream is = DocumentFormats.class.getClassLoader()
                    .getResourceAsStream("formats/" + formatIdentifier + ".blf.yaml")) {
                if (is == null)
                    continue; // not found

                try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    ConfigInputFormat format = new ConfigInputFormat(formatIdentifier);

                    format.setReadFromFile(new File("$BLACKLAB_JAR/formats/" + formatIdentifier + ".blf.yaml"));
                    InputFormatReader.read(reader, false, format, finder);
                    addFormat(format);
                }
            } catch (InvalidInputFormatConfig | IOException e) {
                formatErrors.put(formatIdentifier, e.getMessage());
                throw BlackLabRuntimeException.wrap(e);
            }
        }

        List<File> configDirs = BlackLab.defaultConfigDirs();
        List<File> formatsDirs = new ArrayList<>();
        for (File dir : configDirs) {
            formatsDirs.add(new File(dir, "formats"));
        }
        addFormatsInDirectories(formatsDirs);

        // Load any configs that may have been added using addFormatsInDirectories, from before we were initialized
        loadUnloaded();
    }

    @Override
    public boolean isSupported(String formatIdentifier) {
        // Do not load yet, this is called during discovery of new formats (to detect duplicates)
        // We shouldn't load any new format until we've discovered them all,
        // so that if a format depends on another yet to be loaded format, we know where to find it
        return (supported.containsKey(formatIdentifier) || unloaded.containsKey(formatIdentifier));
    }

    @Override
    public List<Format> getFormats() {
        List<Format> ret = new ArrayList<>();
        for (Entry<String, ConfigInputFormat> e : this.supported.entrySet()) {
            ConfigInputFormat config = e.getValue();
            Format desc = new Format(config.getName(), config.getDisplayName(), config.getDescription(),
                    config.getHelpUrl());
            desc.setConfig(config);
            desc.setVisible(config.isVisible());
            ret.add(desc);
        }

        return ret;
    }

    @Override
    public Format getFormat(String formatIdentifier) {
        if (!isSupported(formatIdentifier))
            return null;

        ConfigInputFormat config = this.supported.get(formatIdentifier);
        if (config == null) {
            loadUnloaded();
            config = this.supported.get(formatIdentifier);
        }
        Format desc = new Format(config.getName(), config.getDisplayName(), config.getDescription(),
                config.getHelpUrl());
        desc.setConfig(config);
        desc.setVisible(config.isVisible());
        return desc;
    }

    protected void addFormat(ConfigInputFormat format) throws InvalidInputFormatConfig {
        if (isSupported(format.getName()))
            throw new IllegalArgumentException("A config format with this name already exists.");

        format.validate();
        supported.put(format.getName(), format);
    }

    /**
     * Locate all config files (files ending with .blf.yaml, .blf.yml, .blf.json)
     * within the list of directories and load them. Formats will use their filename
     * (excluding the .blf.* extension) as a unique identifier. Duplicate formats
     * are ignored. Note that the actual loading of the configs is deferred until
     * this factory is initialized by registering it with {@link DocumentFormats},
     * if not already done.
     *
     * This is a one-time scan, so configs placed in these directories after this
     * scan will not be picked up. <br>
     * <br>
     * If any of the config files depend on other config files, the locations of
     * those dependencies must also be provided in this list of directories. If
     * config A refers to config B, then the directory where config B is located
     * must also be present in the dirs list.
     * 
     * @param dirs
     * @throws InvalidInputFormatConfig when one of the formats could not be
     *             loaded
     */
    public void addFormatsInDirectories(List<File> dirs) throws InvalidInputFormatConfig {
        // Finds all new configs and add them to the "unloaded" list
        FileTask configLocator = new FileTask() {
            @Override
            public void process(File f) {
                if (f.getName().matches("^[\\-\\w]+\\.blf\\.(ya?ml|json)$")) {
                    String formatIdentifier = ConfigInputFormat.stripExtensions(f.getName());
                    if (isSupported(formatIdentifier)) {
                        logger.info("Skipping config format " + f + "; a format with this name already exists.");
                        return;
                    }

                    if (!Files.isReadable(f.toPath()) || !Files.isRegularFile(f.toPath())) {
                        logger.trace("Skipping unreadable config file " + f);
                        return;
                    }

                    unloaded.put(formatIdentifier, f);
                }
            }
        };

        // Run the configLocation on the directory - not recursive
        for (File dir : dirs) {
            if (Files.isReadable(dir.toPath()) && Files.isDirectory(dir.toPath())) {
                FileUtil.processTree(dir, "*", false, configLocator);
            }
        }

        // Don't load until we're initialized,
        // or the configs won't be able to depend on one of the default configs (which are not loaded until 
        // after initialization)
        if (isInitialized)
            loadUnloaded();
    }

    protected Optional<ConfigInputFormat> load(String formatIdentifier, File f) throws IOException {
        try {
            ConfigInputFormat format = new ConfigInputFormat(formatIdentifier);
            InputFormatReader.read(f, format, finder);
            format.setReadFromFile(f);

            addFormat(format);
            return Optional.of(format);
        } catch (InvalidInputFormatConfig | IOException e) {
            formatErrors.put(formatIdentifier, e.getMessage());
            throw e;
        }
    }

    protected void loadUnloaded() {
        // use !isEmpty so we can remove entries during iteration (using the finder above) without messing up iterators
        while (!unloaded.isEmpty()) {
            Entry<String, File> e = unloaded.entrySet().iterator().next();
            unloaded.remove(e.getKey());

            try {
                load(e.getKey(), e.getValue());
            } catch (IOException | InvalidInputFormatConfig ex) {
                ex.printStackTrace();
                logger.warn("Cannot load user format " + e.getValue() + ": " + ex.getMessage());
                // an invalid format somehow got saved, or something else went wrong, just ignore this file then
            }
        }
    }

    @Override
    @Deprecated
    public DocIndexerConfig get(String formatIdentifier, DocWriter indexer, String documentName, Reader reader) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        DocIndexerConfig d = DocIndexerConfig.fromConfig(supported.get(formatIdentifier));
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(reader);
        return d;
    }

    @Override
    public DocIndexerConfig get(String formatIdentifier, DocWriter indexer, String documentName, InputStream is,
            Charset cs) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        DocIndexerConfig d = DocIndexerConfig.fromConfig(supported.get(formatIdentifier));
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(is, cs);
        return d;
    }

    @Override
    public DocIndexerConfig get(String formatIdentifier, DocWriter indexer, String documentName, File f, Charset cs)
            throws FileNotFoundException {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        DocIndexerConfig d = DocIndexerConfig.fromConfig(supported.get(formatIdentifier));
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(f, cs);
        return d;
    }

    @Override
    public DocIndexerConfig get(String formatIdentifier, DocWriter indexer, String documentName, byte[] b, Charset cs) {
        if (!isSupported(formatIdentifier))
            throw new UnsupportedOperationException("Unknown format '" + formatIdentifier
                    + "', call isSupported(formatIdentifier) before attempting to get()");

        DocIndexerConfig d = DocIndexerConfig.fromConfig(supported.get(formatIdentifier));
        d.setDocWriter(indexer);
        d.setDocumentName(documentName);
        d.setDocument(b, cs);
        return d;
    }

    @Override
    public String formatError(String formatIdentifier) {
        if (isSupported(formatIdentifier))
            return null;
        return formatErrors.get(formatIdentifier);
    }
}
