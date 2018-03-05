package nl.inl.blacklab.indexers.preprocess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConvertPluginOpenConvert implements ConvertPlugin {
	private static final Logger logger = LogManager.getLogger(ConvertPluginOpenConvert.class);

	private ClassLoader openConvertJarClassLoader;

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

	private static final Set<String> inputFormats = new HashSet<>(Arrays.asList("doc", "docx", "txt"));
	@Override
	public Set<String> getInputFormats() {
		return inputFormats;
	}

	private static final String outputFormat = "folia";
	@Override
	public String getOutputFormat() {
		return outputFormat;
	}

	@Override
	public boolean canConvert(PushbackInputStream is, Charset cs, String inputFormat) {
		return getInputFormats().contains(inputFormat.toLowerCase());
	}

	private Class<?>  OpenConvert;
	private Method    OpenConvert_GetConverter;

	private Class<?>  SimpleInputOutputProcess;
	private Method    SimpleInputOutputProcess_handleStream;

	public ConvertPluginOpenConvert() {
		//
	}

	@Override
	public void init(ObjectNode config) throws PluginException {
		logger.info("initializing plugin " + getDisplayName());

		if (config == null)
			throw new PluginException("This plugin requires a configuration.");

		try {
			URL jarUrl = Paths.get(configStr(config, "jarPath")).toUri().toURL();
			openConvertJarClassLoader = new URLClassLoader(new URL[]{jarUrl});

			OpenConvert = openConvertJarClassLoader.loadClass("org.ivdnt.openconvert.converters.OpenConvert");
			OpenConvert_GetConverter = OpenConvert.getMethod("getConverter", String.class, String.class, boolean.class);

			SimpleInputOutputProcess = openConvertJarClassLoader.loadClass("org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess");
			SimpleInputOutputProcess_handleStream = SimpleInputOutputProcess.getMethod("handleStream", InputStream.class, Charset.class, OutputStream.class);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | MalformedURLException e) {
			throw new PluginException("Error loading the OpenConvert jar", e);
		}
	}

	@Override
	public void perform(InputStream is, Charset inputCharset, String inputFormat, OutputStream os) throws PluginException {
		// Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
		// This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as xml transformers)
		// and these locators/providers use the ClassLoader they themselves were loaded with to locate and load this dependency class.
		// This would be fine if not for the fact that we (BlackLab) already created some of those same locators/providers, as they're builtin java classes (such as javax.xml.transform.TransformerFactory)
		// That means it would try to find an OpenConvert dependency through the blacklab ClassLoader, which could never work.
		// As a last resort, that ClassLoader however delegates to the ContextClassLoader, which we set to the OpenConvert UrlClassLoader we created.
		// And so it'll eventually end up looking in the OpenConvert jar and everything is fine.
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(openConvertJarClassLoader);
		try {
			performImpl(is, inputCharset, inputFormat, os);
		} finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
	}

	private void performImpl(InputStream in, Charset inputCharset, String inputFormat, OutputStream out) throws PluginException {
		inputFormat = inputFormat.toLowerCase();

		// TODO handle this on a higher level outside of concrete implementations
		if (inputFormat.equalsIgnoreCase(outputFormat)) {
			try {
				IOUtils.copy(in, out);
				return;
			} catch (IOException e) {
				throw new PluginException(e);
			}
		}

		if (!getInputFormats().contains(inputFormat))
			throw new PluginException("The OpenConvert plugin does not support conversion from '" + inputFormat + "' to '" + getOutputFormat() + "'");

		try {
			Object OpenConvertInstance = OpenConvert.newInstance();
			Object SimpleInputOutputProcessInstance = OpenConvert_GetConverter.invoke(OpenConvertInstance, getOutputFormat(), inputFormat, false);

			SimpleInputOutputProcess_handleStream.invoke(SimpleInputOutputProcessInstance, in, inputCharset, out);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new PluginException("Exception while running OpenConvert: ", e);
		}
	}

	/**
	 * Read a value from our config if present.
	 *
	 * @param config root node of our config object
	 * @param nodeName node to read
	 * @return the value as a string
	 * @throws PluginException on missing key, or null value
	 */
	private static String configStr(ObjectNode config, String nodeName) throws PluginException {
		JsonNode n = config.get(nodeName);
		if (n == null || n instanceof NullNode)
			throw new PluginException("Missing configuration value " + nodeName);

		return n.asText();
	}
}
