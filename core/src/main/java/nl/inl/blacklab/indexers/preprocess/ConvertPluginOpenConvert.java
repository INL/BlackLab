package nl.inl.blacklab.indexers.preprocess;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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

	private static ClassLoader openConvertJarClassLoader;

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

	private Class<?>  OpenConvertClass;
	private Method    OpenConvertGetConverter;

	private Class<?>  SimpleInputOutputProcessClass;

	private Class<?>  DirectoryHandlingClass;
	private Method    DirectoryHandlingTraverseDirectory;

	public ConvertPluginOpenConvert() {
		//
	}

	@Override
	public void init(ObjectNode config) throws PluginException {
		logger.info("initializing plugin " + getDisplayName());

		if (config == null)
			throw new PluginException("This plugin requires a configuration.");

		URL jarUrl;
		try {
			jarUrl = Paths.get(configStr(config, "jarPath")).toUri().toURL();
		} catch (MalformedURLException e1) {
			throw new PluginException(e1);
		}


		openConvertJarClassLoader = new URLClassLoader(new URL[]{jarUrl});
		try {
			OpenConvertClass = openConvertJarClassLoader.loadClass("org.ivdnt.openconvert.converters.OpenConvert");
			OpenConvertGetConverter = OpenConvertClass.getMethod("getConverter", String.class, String.class, boolean.class);

			SimpleInputOutputProcessClass = openConvertJarClassLoader.loadClass("org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess");

			DirectoryHandlingClass = openConvertJarClassLoader.loadClass("org.ivdnt.openconvert.filehandling.DirectoryHandling");
			DirectoryHandlingTraverseDirectory = DirectoryHandlingClass.getMethod("traverseDirectory", SimpleInputOutputProcessClass, File.class, File.class, FileFilter.class);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
			throw new PluginException("Error loading the OpenConvert jar", e);
		}
	}

	@Override
	public void perform(Reader reader, String inputFormat, Writer writer) throws PluginException {
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			// Set the ContextClassLoader to use the UrlClassLoader we pointed at the OpenConvert jar.
			// This is required because OpenConvert implicitly loads some dependencies through locators/providers (such as xml transformers)
			// and these locators/providers use the ClassLoader they themselves were loaded with to locate and load this dependency class.
			// This would be fine if not for the fact that we (BlackLab) already created some of those same locators/providers, as they're builtin java classes (such as javax.xml.transform.TransformerFactory)
			// That means it would try to find an OpenConvert dependency through the blacklab ClassLoader, which could never work.
			// As a last resort, that ClassLoader however delegates to the ContextClassLoader, which we set to the OpenConvert UrlClassLoader we created.
			// And so it'll eventually end up looking in the OpenConvert jar and everything is fine.
			Thread.currentThread().setContextClassLoader(openConvertJarClassLoader);

			// create temp files for openconvert
			// TODO: don't create temp files but pass memory streams to OpenConvert if at all possible, needs to be implemented in OpenConvert first though
			File tmpInput = null, tmpOutput = null;
			try {
				tmpInput = File.createTempFile("input", ".tmp");
				tmpOutput = File.createTempFile("output", ".tmp");
				IOUtils.copy(reader, new FileOutputStream(tmpInput), StandardCharsets.UTF_8);
			} catch (IOException e) {
				if (tmpInput != null)
					tmpInput.delete();
				if (tmpOutput != null)
					tmpOutput.delete();

				throw new PluginException("Could not create temp files for conversion from '" + inputFormat + "' to '" + getOutputFormat() + "'", e);
			}

			performImpl(tmpInput, inputFormat, tmpOutput);

			// read back result and delete temp files
			try (FileInputStream fis = new FileInputStream(tmpOutput)) {
//				List<String> content = IOUtils.readfully(fis, StandardCharsets.UTF_8);

				IOUtils.copy(fis, writer, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new PluginException("Could not read result of conversion from '" + inputFormat + "' to '" + getOutputFormat() + "'", e);
			} finally {
				tmpInput.delete();
				tmpOutput.delete();
			}
		} finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
	}

	private void performImpl(File tmpInput, String inputFormat, File tmpOutput) throws PluginException {
		inputFormat.toLowerCase();

		// TODO handle this on a higher level outside of concrete implementations
		if (inputFormat.toLowerCase().equals("folia")) {
			try {
				IOUtils.copy(new FileInputStream(tmpInput), new FileOutputStream(tmpOutput));
				return;
			} catch (IOException e) {
				throw new PluginException(e);
			}
		}

		if (!getInputFormats().contains(inputFormat))
			throw new PluginException("This converter does not support conversion from '" + inputFormat + "' to '" + getOutputFormat() + "'");

		try {
			Object OpenConvertInstance = OpenConvertClass.newInstance();
			Object SimpleInputOutputProcessInstance = OpenConvertGetConverter.invoke(OpenConvertInstance, getOutputFormat(), inputFormat, false);

			DirectoryHandlingTraverseDirectory.invoke(null, SimpleInputOutputProcessInstance, tmpInput, tmpOutput, null);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new PluginException("Exception while running OpenConvert: ", e);
		}
	}
	private static String configStr(ObjectNode config, String nodeName) throws PluginException {
		JsonNode n = config.get(nodeName);
		if (n == null || n instanceof NullNode)
			throw new PluginException("Missing configuration value " + nodeName);

		return n.asText();
	}
}
