package nl.inl.blacklab.indexers.preprocess;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TagPluginDutchTagger implements TagPlugin {
    private static final Logger logger = LogManager.getLogger(TagPluginDutchTagger.class);

    private static final String PROP_JAR     = "jarPath";
    private static final String PROP_VECTORS = "vectorFile";
    private static final String PROP_MODEL   = "modelFile";
    private static final String PROP_LEXICON = "lexiconFile";

    private ClassLoader loader;

    /** The object doing the actual conversion */
    private Object converter = null;

    Method handleFile;

    @Override
    public void init(ObjectNode config) throws PluginException {
        logger.info("initializing plugin " + getDisplayName());

        if (config == null)
            throw new PluginException("This plugin requires configuration");

        try {
            URL jarUrl = Paths.get(configStr(config, PROP_JAR)).toUri().toURL();
            loader = new URLClassLoader(new URL[]{jarUrl});

            Properties base = new Properties();
            base.setProperty("word2vecFile", configStr(config, PROP_VECTORS));
            base.setProperty("taggingModel", configStr(config, PROP_MODEL));
            base.setProperty("lexiconPath", configStr(config, PROP_LEXICON));
            base.setProperty("tokenize", "true");

            Class<?> converterClass = loader.loadClass("nl.namescape.tagging.ImpactTaggerLemmatizerClient");
            Method setProperties = converterClass.getMethod("setProperties", Properties.class);
            handleFile = converterClass.getMethod("handleFile", String.class, String.class);

            converter = converterClass.newInstance();
            setProperties.invoke(converter, base);
        } catch (Exception e) {
            throw new PluginException("Error initializing DutchTaggerLemmatizer plugin", e);
        }
    }

    @Override
    public synchronized void perform(Reader reader, Writer writer) throws PluginException {
        // Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
        // This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as its xml transformers)
        // and these locators/providers use the ClassLoader they themselves were loaded with to locate and load this dependency class.
        // This would be fine if not for the fact that we (BlackLab) already created some of those same locators/providers, as they're builtin java classes (such as javax.xml.transform.TransformerFactory)
        // That means it would try to find an OpenConvert dependency through the blacklab ClassLoader, which could never work.
        // As a last resort, that ClassLoader however delegates to the ContextClassLoader, which we set to the OpenConvert UrlClassLoader we created.
        // And so it'll eventually end up looking in the OpenConvert jar and everything is fine.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            performImpl(reader, writer);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private synchronized void performImpl(Reader reader, Writer writer) throws PluginException {
        Path tmpInput = null;
        Path tmpOutput = null;
        try {
            tmpInput = Files.createTempFile("", ".xml");
            tmpOutput = Files.createTempFile("", ".xml");

            final Charset intermediateCharset = Charset.defaultCharset(); // Use this, as the tagger is a little dumb and doesn't allow us to specify a charset
            try (FileOutputStream os = new FileOutputStream(tmpInput.toFile())) {
                IOUtils.copy(reader, os, intermediateCharset);
            }

            handleFile.invoke(converter, tmpInput.toString(), tmpOutput.toString());

            try (FileInputStream fis = new FileInputStream(tmpOutput.toFile())) {
                IOUtils.copy(fis, writer, intermediateCharset);
            }
        } catch (Exception e) {
            throw new PluginException(e);
        } finally {
            if (tmpInput != null) FileUtils.deleteQuietly(tmpInput.toFile());
            if (tmpOutput != null) FileUtils.deleteQuietly(tmpOutput.toFile());
        }
    }

    /**
     * Read a value from our config if present.
     *
     * @param config root node of our config object
     * @param nodeName node to read
     * @return the value as a string
     * @throws PluginException on missing key or null value
     */
    private static String configStr(ObjectNode config, String nodeName) throws PluginException {
        JsonNode n = config.get(nodeName);
        if (n == null || n instanceof NullNode)
            throw new PluginException("Missing configuration value " + nodeName);

        return n.asText();
    }

    @Override
    public String getInputFormat() {
        return "tei";
    }

    @Override
    public String getOutputFormatIdentifier() {
        return "tei";
    }

    @Override
    public String getOutputFileName(String inputFileName) {
        return FilenameUtils.removeExtension(inputFileName).concat(".xml");
    }

    @Override
    public String getId() {
        return "DutchTagger";
    }

    @Override
    public String getDisplayName() {
        return "DutchTagger";
    }

    @Override
    public String getDescription() {
        return "";
    }
}
