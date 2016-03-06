package nl.inl.blacklab.indexers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocIndexerXmlHandlers.MetadataFetcher;
import nl.inl.blacklab.indexers.MetadataFetcherKbDpo.GetKbMetadata.Metadata;
import nl.inl.util.XmlUtil;

/**
 * Metadata fetcher for KB DPO metadata.
 *
 * In order for this class to work correctly, you should add the following
 * libraries to the classpath (from Apache Http Components, version 4.1.2 or up):
 * - commons-codec
 * - commons-logging
 * - httpclient
 * - httpcore
 */
public class MetadataFetcherKbDpo extends MetadataFetcher {

	/**
	 * Retrieve metadata from KB.
	 */
	public static class GetKbMetadata {

		public static class Metadata {
			public String title;

			public String author;

			public String date;

			public String ppn;

			public Metadata(String title, String author, String date, String ppn) {
				super();
				this.title = title;
				this.author = author;
				this.date = date;
				this.ppn = ppn;
			}
		}

		/** Has the HTTP client been initialized? */
		static boolean initialised = false;

		/* Xpath expressions used to get metadata fields from XML */
		static XPathExpression xpathPpn;
		static XPathExpression xpathTitle;
		static XPathExpression xpathAuthor;
		static XPathExpression xpathDate;

		/** Apache HTTP components classes, ctors and methods
		 *  (we use reflection to avoid static dependency on these libs) */
		private static Class<?> clsDefaultHttpClient;
		private static Class<?> clsHttpUriRequest;
		private static Class<?> clsHttpGet;
		private static Class<?> clsHttpResponse;
		private static Class<?> clsStatusLine;
		private static Class<?> clsHttpEntity;
		private static Class<?> clsEntityUtils;
		private static Constructor<?> ctorHttpGetUrl;
		private static Method methHttpClientExecute;
		private static Method methHttpResponseGetStatusLine;
		private static Method methHttpResponseGetEntity;
		private static Method methStatusLineGetStatusCode;
		private static Method methStatusLineGetReasonPhrase;
		private static Method methHttpEntityGetContent;
		private static Method methEntityUtilsConsume;

		/** The HTTP client used to get metadata from webservices */
		static Object defaultHttpClient;

		/** Cached metadata (saves URL requests) */
		static Map<String, Metadata> cached = new HashMap<>();

		private static void init() {

			// Init XPath
			XmlUtil.setNamespaceAware(true);
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			xpath.setNamespaceContext(new NamespaceContext() {
				@Override
				public Iterator<?> getPrefixes(String arg0) {
					throw new UnsupportedOperationException();
				}

				@Override
				public String getPrefix(String arg0) {
					throw new UnsupportedOperationException();
				}

				@Override
				public String getNamespaceURI(String prefix) {
					if (prefix == null)
						throw new NullPointerException("Null prefix");
					if (prefix.equals("xsi"))
						return "http://www.w3.org/2001/XMLSchema-instance";
					if (prefix.equals("dc"))
						return "http://purl.org/dc/elements/1.1/";
					if (prefix.equals("dcx"))
						return "http://krait.kb.nl/coop/tel/handbook/telterms.html";
					if (prefix.equals(""))
						return "http://www.openarchives.org/OAI/2.0/";
					if (prefix.equals("xml"))
						return XMLConstants.XML_NS_URI;
					return XMLConstants.NULL_NS_URI;
				}
			});

			// Compile XPath expressions
			try {
				xpathPpn = xpath.compile("//dcx:recordIdentifier[@xsi:type='dcx:PPN']");
				xpathTitle = xpath.compile("//dc:title");
				xpathAuthor = xpath.compile("//dc:creator");
				xpathDate = xpath.compile("//dc:date");
			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			}

			try {
				// Use reflection to get handle to classes, ctors and methods
				// (so we avoid static dependencies on the Apache HTTP libraries)
				clsDefaultHttpClient = Class.forName("org.apache.http.impl.client.DefaultHttpClient");
				clsHttpUriRequest = Class.forName("org.apache.http.client.methods.HttpUriRequest");
				clsHttpGet = Class.forName("org.apache.http.client.methods.HttpGet");
				clsHttpResponse = Class.forName("org.apache.http.HttpResponse");
				clsStatusLine = Class.forName("org.apache.http.StatusLine");
				clsHttpEntity = Class.forName("org.apache.http.HttpEntity");
				clsEntityUtils = Class.forName("org.apache.http.util.EntityUtils");
				ctorHttpGetUrl = clsHttpGet.getConstructor(String.class);
				methHttpClientExecute = clsDefaultHttpClient.getMethod("execute", clsHttpUriRequest);
				methHttpResponseGetStatusLine = clsHttpResponse.getMethod("getStatusLine");
				methHttpResponseGetEntity = clsHttpResponse.getMethod("getEntity");
				methStatusLineGetStatusCode = clsStatusLine.getMethod("getStatusCode");
				methStatusLineGetReasonPhrase = clsStatusLine.getMethod("getReasonPhrase");
				methHttpEntityGetContent = clsHttpEntity.getMethod("getContent");
				methEntityUtilsConsume = clsEntityUtils.getMethod("consume", clsHttpEntity);

				// Instantiate HTTP client object
				defaultHttpClient = clsDefaultHttpClient.newInstance();

			} catch (Exception e) {
				throw new RuntimeException("Error finding (some of the) Apache HTTP libraries."
					+ "Make sure Apache commons-codec, commons-logging, httpclient, httpcore (4.1.2 or higher) are on the classpath.",
					e);
			}

			initialised = true;
		}

		private static Document getMetadataByDpo(String dpo) {
			String url = "http://services.kb.nl/mdo/oai?verb=GetRecord&identifier=DPO:dpo:" + dpo
					+ ":mpeg21&metadataPrefix=didl";
			return fetchDomDocument(url);
		}

		private static Document getMetadataByPpn(String ppn) {
			String url = "http://services.kb.nl/mdo/oai?verb=GetRecord&identifier=DPO:DPO:" + ppn
					+ "&metadataPrefix=dcx";
			return fetchDomDocument(url);
		}

		private static String fetchDocument(String url) {
			int code = -1;
			String reason;

			// HTTP
			try {

				// HttpGet httpGet = new HttpGet(url);
				Object httpGet = ctorHttpGetUrl.newInstance(url);

				// HttpResponse response = defaultHttpClient.execute(httpGet);
				Object httpResponse = methHttpClientExecute.invoke(defaultHttpClient, httpGet);

				// StatusLine statusLine = httpResponse.getStatusLine();
				Object statusLine = methHttpResponseGetStatusLine.invoke(httpResponse);

				// code = statusLine.getStatusCode();
				code = (Integer) methStatusLineGetStatusCode.invoke(statusLine);

				// reason = statusLine.getReasonPhrase();
				reason = (String) methStatusLineGetReasonPhrase.invoke(statusLine);

				// HttpEntity httpEntity = httpResponse.getEntity();
				Object httpEntity = methHttpResponseGetEntity.invoke(httpResponse);

				if (code == 200) {
					// InputStream is = httpEntity.getContent();
					InputStream is = (InputStream) methHttpEntityGetContent.invoke(httpEntity);

					BufferedReader b = new BufferedReader(new InputStreamReader(is, "utf-8"));
					try {
						StringBuilder content = new StringBuilder();
						while (true) {
							String line = b.readLine();
							if (line == null)
								break;
							content.append(line);
						}
						return content.toString();
					} finally {
						b.close();
					}
				}

				try {
					// Some HTTP error occurred (e.g. 403 / 404)
					// Make sure the entity content is fully consumed and the content stream, if
					// exists, is closed. The process is done, quietly , without throwing any
					// IOException.
					methEntityUtilsConsume.invoke(httpEntity);
				} catch (Exception e) {
					// Not important, ignore
				}

			} catch (UnknownHostException e) {
				reason = "Unknown host";
			} catch (IOException e) {
				reason = "IOException: " + e.getMessage();
			} catch (Exception e) {
				e.printStackTrace();
				if (e.getCause() != null)
					reason = e.getCause().getMessage();
				else
					reason = e.getMessage();
			}
			if (code != 200)
				System.err.println("Could not fetch " + url + " (" + code + " " + reason + ")");

			return null;
		}

		private static Document fetchDomDocument(String url) {
			String content = fetchDocument(url);
			try {
				return XmlUtil.parseXml(new StringReader(content));
			} catch (SAXException e) {
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * Fetch metadata for a document from its DPO number
		 *
		 * @param dpo the DPO number
		 *
		 * @return the metadata fetched
		 */
		public static Metadata getMetadataFieldsFromDpo(String dpo) {
			if (!initialised)
				init();

			if (cached.containsKey(dpo))
				return cached.get(dpo);

			// Retrieve and parse metadata
			Document doc = getMetadataByDpo(dpo);

			try {

				Node nodePpn = (Node) xpathPpn.evaluate(doc, XPathConstants.NODE);
				String ppn = nodePpn == null ? "?" : nodePpn.getTextContent();
				Document doc2 = getMetadataByPpn(ppn);

				Node nodeTitle = (Node) xpathTitle.evaluate(doc2, XPathConstants.NODE);
				Node nodeAuthor = (Node) xpathAuthor.evaluate(doc2, XPathConstants.NODE);
				Node nodeDate = (Node) xpathDate.evaluate(doc2, XPathConstants.NODE);
				String title = nodeTitle == null ? "?" : nodeTitle.getTextContent();
				String author = nodeAuthor == null ? "?" : nodeAuthor.getTextContent();
				String date = nodeDate == null ? "?" : nodeDate.getTextContent();

				Metadata metadata = new Metadata(title, author, date, ppn);
				cached.put(dpo, metadata); // save in cache so we don't query it twice
				return metadata;

			} catch (XPathExpressionException e) {
				throw new RuntimeException(e);
			} catch (DOMException e) {
				throw new RuntimeException(e);
			}
		}

		private GetKbMetadata() {
			// Cannot instantiate
		}

	}

	// TODO: improve structure to avoid test-specific code
	final static String TEST_FROM_INPUT_FILE = "dpo_123_0002_master.jpf";

	/** Pattern for getting DPO number from image file name */
	private final static Pattern PATT_DPO = Pattern.compile("^dpo_(\\d+)_");

	private DocIndexerXmlHandlers ourDocIndexer;

	public MetadataFetcherKbDpo(DocIndexer docIndexer) {
		super(docIndexer);

		if (docIndexer instanceof DocIndexerXmlHandlers) {
			// Should always be the case, except when testing
			ourDocIndexer = (DocIndexerXmlHandlers) docIndexer;
		}
	}

	@Override
	public void addMetadata() {
		String fileName;
		if (ourDocIndexer != null)
			fileName = ourDocIndexer.getCurrentLuceneDoc().get("imageFileName");
		else {
			// TEST
			fileName = TEST_FROM_INPUT_FILE;
		}

		Matcher m = PATT_DPO.matcher(fileName);
		if (m.find()) {
			String dpo;
			dpo = m.group(1);
			Metadata metadata = GetKbMetadata.getMetadataFieldsFromDpo(dpo);
			ourDocIndexer.addMetadataField("title", metadata.title);
			ourDocIndexer.addMetadataField("author", metadata.author);
			ourDocIndexer.addMetadataField("date", metadata.date);
			ourDocIndexer.addMetadataField("ppn", metadata.ppn);
		} else {
			// TODO: improve error reporting
			System.err.println("DPO number not found for imageFileName " + fileName);
			return;
		}

	}

}
