package nl.inl.blacklab.indexers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocIndexerXmlHandlers.MetadataFetcher;
import nl.inl.util.CapturingReader;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Example of a metadata fetcher, a class used to fetch metadata
 * from an external source in case it's not included in the documents.
 *
 * This class fetches the metadata from a ZIP file with a certain
 * structure (specific to the OpenSonar project), but it should be easy
 * to adapt to your own needs.
 *
 * Note that for accessing large ZIP files, you need Java 7 which supports
 * the ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)"
 * error)
 */
public class MetadataFetcherSonarCmdi extends MetadataFetcher {

	// TODO: improve structure to avoid test-specific code
	final static String TEST_FROM_INPUT_FILE = "SoNaR500.Curated.WR-P-E-A_discussion_lists.20130312.tar.gz\\SONAR500/DATA/WR-P-E-A_discussion_lists/WR-P-E-A-0000008066.folia.xml";

	static private ZipFile metadataZipFile = null;

	private String metadataPathInZip;

	private DocIndexerXmlHandlers ourDocIndexer;

	public MetadataFetcherSonarCmdi(DocIndexer docIndexer) {
		super(docIndexer);

		if (docIndexer instanceof DocIndexerXmlHandlers) {
			// Should always be the case, except when testing
			ourDocIndexer = (DocIndexerXmlHandlers)docIndexer;
		}

		if (metadataZipFile == null) {
			String zipFilePath = docIndexer.getParameter("metadataZipFile");
			if (zipFilePath == null)
				throw new RuntimeException("For OpenSonar metadata, specify metadataZipFile in indexer.properties!");
			File file = new File(zipFilePath);
			try {
				metadataZipFile = new ZipFile(file);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		metadataPathInZip = docIndexer.getParameter("metadataPathInZip", "");
		if (metadataPathInZip.length() > 0 && !metadataPathInZip.endsWith("/"))
			metadataPathInZip += "/";
	}

	@Override
	public void close() throws IOException {
		// TODO: make sure zip file is properly closed when done
		//   (change structure so metadata fetcher isn't instantiated for each document separately)
		//metadataZipFile.close();
	}

	@Override
	public void addMetadata() {

		String fromInputFile;
		Document luceneDoc = ourDocIndexer.getCurrentLuceneDoc();
		if (ourDocIndexer != null)
			fromInputFile = luceneDoc.get("fromInputFile");
		else {
			// TEST
			fromInputFile = TEST_FROM_INPUT_FILE;
		}

		fromInputFile = fromInputFile.replaceAll("\\\\", "/");
		int lastSlash = fromInputFile.lastIndexOf("/");
		int penultimateSlash = fromInputFile.lastIndexOf("/", lastSlash - 1);
		String metadataFile = fromInputFile.substring(penultimateSlash + 1);
		metadataFile = metadataFile.replaceAll("\\.folia\\.", ".cmdi.");

		try {
			ZipEntry e = metadataZipFile.getEntry(metadataPathInZip + metadataFile);
			if (e == null) {
				//throw new RuntimeException("Entry in zip not found: " + metadataPathInZip + metadataFile);
				System.err.println("*** ERROR, metadata entry not found: " + metadataPathInZip + metadataFile);
				return;
			}
			InputStream is = metadataZipFile.getInputStream(e);
			CapturingReader capturingReader = new CapturingReader(new InputStreamReader(is, "utf-8"));
			BufferedReader reader = new BufferedReader(capturingReader);
			try {
				SAXParserFactory factory = SAXParserFactory.newInstance();
				factory.setNamespaceAware(true);
				SAXParser parser;
				parser = factory.newSAXParser();
				parser.parse(new InputSource(reader), new MetadataParser());
			} finally {
				reader.close();
			}

			// Combine AuthorName and Pseudonym fields into
			// fields AuthorNameOrPseudonym / AuthorNameOrPseudonymSearch
			String authorName = luceneDoc.get("AuthorName");
			if (authorName == null)
				authorName = "";
			String pseudonym = luceneDoc.get("Pseudonym");
			if (pseudonym == null)
				pseudonym = "";
			String authorNameAndPseudonym = authorName + " " + pseudonym;
			if (authorName.isEmpty()) {
				authorName = pseudonym;
			}
			luceneDoc.add(new Field("AuthorNameOrPseudonym", authorName, docIndexer.getMetadataFieldType("AuthorNameOrPseudonym")));
			luceneDoc.add(new Field("AuthorNameOrPseudonymSearch", authorNameAndPseudonym, docIndexer.getMetadataFieldType("AuthorNameOrPseudonymSearch")));

			if (ourDocIndexer != null) {
				// Store metadata XML in content store and corresponding id in Lucene document
				ContentStore cs = ourDocIndexer.getIndexer().getContentStore("metadata");
				int id = cs.store(capturingReader.getContent());
				luceneDoc.add(new IntField("metadataCid", id, Store.YES));
			} else {
				// TEST; print start of metadata file
				System.out.println(capturingReader.getContent().substring(0, 1000));
			}



		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Handles the metadata XML and adds it to the Lucene document
	 */
	class MetadataParser extends DefaultHandler {

		private StringBuilder textContent = new StringBuilder();

		private boolean hasChild = false;

		Map<String, String> fieldsToIndex = new HashMap<String, String>();

		public MetadataParser() {
			fieldsToIndex.put("CollectionName", "");
			fieldsToIndex.put("CollectionCode", "");
			fieldsToIndex.put("TextTitle", "");
			fieldsToIndex.put("TextSubTitle", "");
			fieldsToIndex.put("TextDescription", "");
			fieldsToIndex.put("TextType", "");
			fieldsToIndex.put("TextClass", "");
			fieldsToIndex.put("TextKeywords", "");
			fieldsToIndex.put("LanguageName", "");
			fieldsToIndex.put("iso-639-3-code", "Language-iso-code");
			fieldsToIndex.put("SourceName", "");
			fieldsToIndex.put("Continent", "");
			fieldsToIndex.put("Country", "");
			fieldsToIndex.put("Publisher", "");
			fieldsToIndex.put("PublicationDate", "");
			fieldsToIndex.put("Name", "AuthorName");
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			hasChild = false; // we haven't seen a child for this element yet
			textContent.setLength(0); // clear buffer
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {

			if (!hasChild) {
				String indexAs = fieldsToIndex.get(localName);
				if (indexAs == null || indexAs.length() == 0)
					indexAs = localName;
				String content = textContent.toString().trim();
				if (content.length() > 0) {
					// Leaf node with content; store as metadata field
					if (ourDocIndexer != null)
						ourDocIndexer.addMetadataField(indexAs, content);
					else {
						// TEST; print metadata value
						System.out.println(indexAs + ": " + content);
					}
				}
			}

			hasChild = true; // our parent has at least one child
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			if (!hasChild)
				textContent.append(ch, start, length);
		}
	}

	public static void main(String[] args) throws IOException {
		DocIndexer docIndexerTest = new DocIndexer() {
			@Override
			public void index() throws Exception {
				throw new UnsupportedOperationException();
			}

			@Override
			public String getParameter(String name, String defaultValue) {
				if (name.equals("metadataZipFile")) {
					return "G:\\Jan_OpenSonar\\SONAR500TST.zip";
				}
				if (name.equals("metadataPathInZip")) {
					return "SONAR500TST/DATA";
				}
				return defaultValue;
			}

			@Override
			public void setParameter(String name, String value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void setParameters(Map<String, String> param) {
				throw new UnsupportedOperationException();
			}

			@Override
			public String getParameter(String name) {
				return getParameter(name, null);
			}

			@Override
			public boolean hasParameter(String name) {
				return getParameter(name) != null;
			}

			@Override
			public FieldType getMetadataFieldType(String fieldName) {
				return StringField.TYPE_STORED;
			}
		};

		MetadataFetcher fetcher = new MetadataFetcherSonarCmdi(docIndexerTest);
		fetcher.addMetadata();
		fetcher.close();
	}

}
