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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConvertPluginOpenConvert implements ConvertPlugin {
	private static final Logger logger = LogManager.getLogger(ConvertPluginOpenConvert.class);

	private ClassLoader loader;

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
			loader = new URLClassLoader(new URL[]{jarUrl});

			OpenConvert = loader.loadClass("org.ivdnt.openconvert.converters.OpenConvert");
			OpenConvert_GetConverter = OpenConvert.getMethod("getConverter", String.class, String.class);

			SimpleInputOutputProcess = loader.loadClass("org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess");
			SimpleInputOutputProcess_handleStream = SimpleInputOutputProcess.getMethod("handleStream", InputStream.class, Charset.class, OutputStream.class);
		} catch (ClassNotFoundException | NoSuchMethodException | SecurityException | MalformedURLException e) {
			throw new PluginException("Error loading the OpenConvert jar", e);
		}
	}

	@Override
	public void perform(InputStream is, Charset inputCharset, String inputFormat, OutputStream os) throws PluginException {
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
			performImpl(is, inputCharset, inputFormat, os);
		} finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
	}

	private void performImpl(InputStream _in, Charset inputCharset, String inputFormat, OutputStream out) throws PluginException {
		try (PushbackInputStream in = new PushbackInputStream(_in, 251)) {
			// important to let openconvert know what we want to do
			inputFormat = getActualFormat(in, inputFormat);
			if (!canConvert(in, inputCharset, inputFormat))
				throw new PluginException("The OpenConvert plugin does not support conversion from '" + inputFormat + "' to '" + getOutputFormat() + "'");
			
			Object OpenConvertInstance = OpenConvert.newInstance();
			Object SimpleInputOutputProcessInstance = OpenConvert_GetConverter.invoke(OpenConvertInstance, getOutputFormat(), inputFormat);
			
			SimpleInputOutputProcess_handleStream.invoke(SimpleInputOutputProcessInstance, in, inputCharset, out);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | IOException e) {
			throw new PluginException("Exception while running OpenConvert: ", e);
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
    
    private static final Set<String> inputFormats = new HashSet<>(Arrays.asList("doc", "docx", "txt", "epub", "html", "alto")); // TODO (not supported in openconvert yet): rtf, odt, pdf
    @Override
    public Set<String> getInputFormats() {
        return Collections.unmodifiableSet(inputFormats);
    }

    private static final String outputFormat = "tei";
    @Override
    public String getOutputFormat() {
        return outputFormat;
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
}
