package nl.inl.blacklab.indexers.preprocess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConvertPluginOpenConvert implements ConvertPlugin {
    private static final String PROP_JAR = "jarPath";

    private static final String VERSION = "0.2";

    private ClassLoader loader;

    private Class<?> OpenConvert;
    private Method OpenConvert_GetConverter;

    private Class<?> SimpleInputOutputProcess;
    private Method SimpleInputOutputProcess_handleStream;

    public ConvertPluginOpenConvert() {
        //
    }

    @Override
    public void init(Optional<ObjectNode> configNode) throws PluginException {
        ObjectNode config = configNode.orElseThrow(() -> new PluginException("This plugin requires a configuration."));

        File jar = new File(configStr(config, PROP_JAR));
        if (!jar.exists())
            throw new PluginException("Could not find the openConvert jar at location " + jar.toString());
        if (!jar.canRead())
            throw new PluginException("Could not read the openConvert jar at location " + jar.toString());

        try {
            URL jarUrl = jar.toURI().toURL();
            loader = new URLClassLoader(new URL[] { jarUrl }, null);
            assertVersion(loader);

            OpenConvert = loader.loadClass("org.ivdnt.openconvert.converters.OpenConvert");
            OpenConvert_GetConverter = OpenConvert.getMethod("getConverter", String.class, String.class);

            SimpleInputOutputProcess = loader.loadClass("org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess");
            SimpleInputOutputProcess_handleStream = SimpleInputOutputProcess.getMethod("handleStream",
                    InputStream.class, Charset.class, OutputStream.class);
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | MalformedURLException e) {
            throw new PluginException("Error loading the OpenConvert jar: " + e.getMessage(), e);
        }
    }

    @Override
    public void perform(InputStream is, Charset inputCharset, String inputFormat, OutputStream os)
            throws PluginException {
        // Set the ContextClassLoader to use the UrlClassLoader we pointed at the
        // OpenConvert jar.
        // This is required because OpenConvert implicitly loads some dependencies
        // through locators/providers (such as its xml transformers)
        // and these locators/providers sometimes prefer to use the ContextClassLoader,
        // which may have been set by a servlet container or the like.
        // If those cases, the contextClassLoader does not have the jar we loaded on its
        // classpath, and so it cannot find the correct classes.
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            performImpl(is, inputCharset, inputFormat, os);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void performImpl(InputStream _in, Charset inputCharset, String inputFormat, OutputStream out)
            throws PluginException {
        try (PushbackInputStream in = new PushbackInputStream(_in, 251)) {
            // important to let openconvert know what we want to do
            inputFormat = getActualFormat(in, inputFormat);
            if (!canConvert(in, inputCharset, inputFormat))
                throw new PluginException("The OpenConvert plugin does not support conversion from '" + inputFormat
                        + "' to '" + getOutputFormat() + "'");

            Object OpenConvertInstance = OpenConvert.getConstructor().newInstance();
            Object SimpleInputOutputProcessInstance = OpenConvert_GetConverter.invoke(OpenConvertInstance,
                    getOutputFormat(), inputFormat);

            SimpleInputOutputProcess_handleStream.invoke(SimpleInputOutputProcessInstance, in, inputCharset, out);
        } catch (ReflectiveOperationException | IllegalArgumentException | IOException | SecurityException e) {
            throw new PluginException("Exception while running OpenConvert: " + e.getMessage(), e);
        }
    }

    @Override
    public String getId() {
        return "OpenConvert";
    }

    @Override
    public String getDisplayName() {
        return "OpenConvert";
    }

    @Override
    public String getDescription() {
        return "File converter using the OpenConvert library";
    }

    private static final Set<String> inputFormats = new HashSet<>(
            Arrays.asList("doc", "docx", "txt", "epub", "html", "alto", "rtf", "odt")); // TODO (not supported in
                                                                                        // openconvert yet): pdf

    @Override
    public Set<String> getInputFormats() {
        return Collections.unmodifiableSet(inputFormats);
    }

    @Override
    public String getOutputFormat() {
        return "tei";
    }

    @Override
    public boolean canConvert(PushbackInputStream is, Charset cs, String inputFormat) {
        return inputFormats.contains(getActualFormat(is, inputFormat));
    }

    private static String getActualFormat(PushbackInputStream is, String reportedFormat) {
        reportedFormat = reportedFormat.toLowerCase();
        if (reportedFormat.equals("xhtml"))
            return "html";
        if (reportedFormat.equals("xml") && isAlto(is)) {
            return "alto";
        }

        return reportedFormat;
    }

    private static boolean isAlto(PushbackInputStream i) {
        try {
            byte[] buffer = new byte[250];
            int bytesRead = i.read(buffer);
            String head = new String(buffer, StandardCharsets.US_ASCII).toLowerCase();
            i.unread(buffer, 0, bytesRead);
            return head.contains("<alto");
        } catch (IOException e) {
            return false;
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

    /**
     * Ensure that the maven artifact version matches VERSION
     *
     * @param loader
     * @throws PluginException
     */
    private static void assertVersion(ClassLoader loader) throws PluginException {
        try (InputStream is = loader.getResourceAsStream("META-INF/MANIFEST.MF")) {
            Manifest manifest = new Manifest(is);
            String version = manifest.getMainAttributes().getValue("Specification-Version");
            if (!version.equals(VERSION))
                throw new PluginException("Mismatched version! Expected " + VERSION + " but found " + version);
        } catch (IOException e) {
            throw new PluginException("Could not read manifest: " + e.getMessage(), e);
        }
    }
}
