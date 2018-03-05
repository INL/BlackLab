package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.YamlJsonReader;
import nl.inl.blacklab.indexers.preprocess.ConvertPlugin;
import nl.inl.blacklab.indexers.preprocess.DocIndexerConvertAndTag;
import nl.inl.blacklab.indexers.preprocess.Plugin;
import nl.inl.blacklab.indexers.preprocess.Plugin.PluginException;
import nl.inl.blacklab.indexers.preprocess.TagPlugin;

/**
 * Responsible for loading file conversion and tagging plugins, and exposing chained series of plugins to {@link DocumentFormats}.
 *<p>
 * It will attempt to load all conversion and tagging plugins on the classpath and initialize them with their respective settings in the main blacklab config.
 * It will then query the plugins to see what sorts of conversions they support (for example from plaintext => tei, or  from tei => folia),
 * and create "chains" of plugins that feed into each other, to eventually end up with a format that is supported by a regular format in the DocumentFormats list.
 * It will then register its own formats that extend/wrap those existing formats, so that users get a selection of available conversion chains automatically.
 *<p>
 * Plugins are loaded from the classpath, according to the {@link ServiceLoader} system.
 * So a jar that wishes to register a plugin must contain a file named "nl.inl.blacklab.indexers.preprocess.Plugin" inside "META-INF/services/",
 * containing the qualified classNames of the implementations they contain.
 */
public class DocIndexerFactoryConvertAndTag implements DocIndexerFactory {
	private static final Logger logger = LogManager.getLogger(DocIndexerFactoryConvertAndTag.class);


	public static class ConvertAndTagFormat extends DocIndexerFactory.Format {
		private ConvertPlugin converter;
		private TagPlugin tagger;
		private Format outputFormat;

		public ConvertAndTagFormat(ConvertPlugin converter, TagPlugin tagger, Format outputFormat) {
			super(id(converter, tagger, outputFormat),
			      displayName(converter, tagger, outputFormat),
			      description(converter, tagger, outputFormat));

			// don't copy the outputFormat's config
			this.converter = converter;
			this.tagger = tagger;
			this.outputFormat = outputFormat;
		}

		// even though our internal format IS config-based (usually at least), we are not, and since the config is internal only
		// don't allow retrieving the config.
		@Override
		public void setConfig(ConfigInputFormat config) {
			throw new UnsupportedOperationException();
		}

		public ConvertPlugin getConverter() {
			return converter;
		}
		public TagPlugin getTagger() {
			return tagger;
		}
		public Format getOutputFormat() {
			return outputFormat;
		}

		// generate id
		private static String id(ConvertPlugin converter, TagPlugin tagger, Format outputFormat) {
			return FORMATIDENTIFIER_PREFIX + converter.getId() + "/" + tagger.getId();
		}

		// generate displayName
		private static String displayName(ConvertPlugin converter, TagPlugin tagger, Format outputFormat) {
			return tagger.getInputFormat() + "<" + tagger.getDisplayName() + "<" + converter.getDisplayName();
		}

		// generate description
		private static String description(ConvertPlugin converter, TagPlugin tagger, Format outputFormat) {
			return "Documents in the '" + tagger.getInputFormat() + "' format. With additional support for automatically converting and tagging " +
			StringUtils.join(converter.getInputFormats().iterator(), ", ") +
			" files using the '" + converter.getDisplayName() + "' converter and the '" + tagger.getDisplayName() + "' tagger.";
		}
	}


	// Valid format identifiers must be of the format  $plugin/convertPluginId/tagPluginId
	// where a pluginId may only contain a-z, A-Z, 0-9, and _
	private static final String FORMATIDENTIFIER_PREFIX = "$plugin/";
	private static final Pattern PLUGINID_PATTERN = Pattern.compile("[\\w]+");
//	private static final Pattern FORMATIDENTIFIER_PATTERN = Pattern.compile("^\\$plugin\\/("+PLUGINID_PATTERN.toString()+")\\/("+PLUGINID_PATTERN.toString()+")$");

	private static boolean isInitialized = false;

	private static Map<String, ConvertPlugin> convertPlugins;
	private static Map<String, TagPlugin> tagPlugins;

	private static Map<String, ConvertAndTagFormat> supported = new HashMap<>();

	/**
	 * Attempts to load and initialize all plugin classes on the classpath, passing the values in the config to the matching plugin.
	 *
	 * @param pluginConfig the plugin configurations collection object. The format for this object is <pre>
	 * {
	 *   "pluginId": {
	 *     // arbitrary plugin config here
	 *   },
	 *
	 *   "anotherPluginId": { ... },
	 *   ...
	 * } </pre>
	 */
	public static void initPlugins(ObjectNode pluginConfig) {
		if (isInitialized)
			throw new IllegalStateException("PluginManager already initialized"); 

		logger.debug("Initializing plugin system");

		convertPlugins = initPlugins(ConvertPlugin.class, pluginConfig);
		tagPlugins = initPlugins(TagPlugin.class, pluginConfig);

		// Discover available combinations of converters and taggers
		for (TagPlugin tagger : tagPlugins.values()) {
			// TODO think about this, should we maybe dynamically disover combinations (i.e. every time isSupported and getFormats() is called?)
			// Currently we will ignore a tagger permanently if no format is available at to process its output at initialization time (i.e. right now)
			// If a matching format is registered in the future, the tagger could then be used, but we've already decided to ignore it here.

			// This create a chicken-and-egg situation for client applications wishing to store information about custom formats in the blacklab config file.
			// since then any of those formats can't be loaded until the config is loaded, but once that has happened it's too late to have those formats
			// disovered by the convertAndTag system here.
			if (!DocumentFormats.isSupported(tagger.getOutputFormatIdentifier())) {
				logger.debug("Ignoring tagger plugin " + tagger.getId() + "; its output format " + tagger.getOutputFormatIdentifier() + " is not currently supported for indexing.");
				continue;
			}

			for (ConvertPlugin converter : convertPlugins.values()) {
				if (!converter.getOutputFormat().equals(tagger.getInputFormat()))
					continue;

				Format taggerOutputFormat = DocumentFormats.getFormat(tagger.getOutputFormatIdentifier());
				ConvertAndTagFormat format = new ConvertAndTagFormat(converter, tagger, taggerOutputFormat);

				supported.put(format.getId(), format);
			}
		}

		logger.debug("Finished Initializing plugin system");
	}

	private static <T extends Plugin> Map<String , T> initPlugins(Class<T> pluginClass, ObjectNode pluginConfig) {
		Map<String, T> plugins = new HashMap<>();

		Iterator<T> it = ServiceLoader.load(pluginClass).iterator();
		while (it.hasNext()) {
			String id = null;

			try {
				T plugin = it.next();
				id = plugin.getId();
				if (!PLUGINID_PATTERN.matcher(id).matches()) {
					logger.info("Plugin id " + id + " (class " + plugin.getClass().getCanonicalName() + ") is not a valid id; ignoring plugin.");
					continue;
				}

				JsonNode config = pluginConfig.get(plugin.getId());
				if (config == null || config instanceof NullNode)
					plugin.init(null);
				else
					plugin.init(YamlJsonReader.obj(config, plugin.getId()));

				plugins.put(id, plugin);
				logger.debug("Initialized plugin " + plugin.getDisplayName());
			} catch (ServiceConfigurationError e) {
				logger.error("Plugin failed to load: " + e.getMessage(), e);
			} catch (PluginException e) {
				logger.error("Plugin "+id+" failed to initialize: " + e.getMessage(), e);
			} catch (Exception e) {
				logger.error("Plugin " + (id == null ? "(unknown)" : id) + " failed to load: " + e.getMessage(), e);
			}
		}

		return plugins;
	}

	public DocIndexerFactoryConvertAndTag() {
		// nothing to do
	}

	@Override
	public void init() {
		/* nothing to do; initialization happens when the blacklab config is loaded.
		 The blacklab Config is automatically loaded when the first Searcher is opened, or earlier by a user library. So plugin formats should always be visible by the time they're needed.
		 (except when trying to query available formats before opening a searcher or loading a config...this is an edge case) */
	}

	@Override
	public boolean isSupported(String formatIdentifier) {
		return supported.containsKey(formatIdentifier);
	}

	@Override
	public List<ConvertAndTagFormat> getFormats() {
		return Collections.unmodifiableList(new ArrayList<>(supported.values()));
	}

	@Override
	public ConvertAndTagFormat getFormat(String formatIdentifier) {
		return supported.get(formatIdentifier);
	}

	@Override
	public DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, Reader reader)
			throws UnsupportedOperationException {
		if (!isSupported(formatIdentifier))
			throw new UnsupportedOperationException("Unknown format '"+formatIdentifier+"', call isSupported(formatIdentifier) before attempting to get()");

		ConvertAndTagFormat fmt = getFormat(formatIdentifier);
		DocIndexerConvertAndTag di = new DocIndexerConvertAndTag(fmt.getConverter(), fmt.getTagger());
		di.setDocument(reader);
		di.setDocumentName(documentName);
		di.setIndexer(indexer);
		return di;
	}

	@Override
	public DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, InputStream is, Charset cs)
			throws UnsupportedOperationException {
		if (!isSupported(formatIdentifier))
			throw new UnsupportedOperationException("Unknown format '"+formatIdentifier+"', call isSupported(formatIdentifier) before attempting to get()");

		ConvertAndTagFormat fmt = getFormat(formatIdentifier);
		DocIndexerConvertAndTag di = new DocIndexerConvertAndTag(fmt.getConverter(), fmt.getTagger());
		di.setDocument(is, cs);
		di.setDocumentName(documentName);
		di.setIndexer(indexer);
		return di;
	}

	@Override
	public DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, File f, Charset cs)
			throws UnsupportedOperationException, FileNotFoundException {
		if (!isSupported(formatIdentifier))
			throw new UnsupportedOperationException("Unknown format '"+formatIdentifier+"', call isSupported(formatIdentifier) before attempting to get()");

		ConvertAndTagFormat fmt = getFormat(formatIdentifier);
		DocIndexerConvertAndTag di = new DocIndexerConvertAndTag(fmt.getConverter(), fmt.getTagger());
		di.setDocument(f, cs);
		di.setDocumentName(documentName);
		di.setIndexer(indexer);
		return di;
	}

	@Override
	public DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, byte[] b, Charset cs)
			throws UnsupportedOperationException {
		if (!isSupported(formatIdentifier))
			throw new UnsupportedOperationException("Unknown format '"+formatIdentifier+"', call isSupported(formatIdentifier) before attempting to get()");

		ConvertAndTagFormat fmt = getFormat(formatIdentifier);
		DocIndexerConvertAndTag di = new DocIndexerConvertAndTag(fmt.getConverter(), fmt.getTagger());
		di.setDocument(b, cs);
		di.setDocumentName(documentName);
		di.setIndexer(indexer);
		return di;
	}
}
