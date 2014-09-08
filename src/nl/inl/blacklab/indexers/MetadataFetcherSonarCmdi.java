package nl.inl.blacklab.indexers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
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

	static private File metadataDir = null;

	private String metadataPathInZip;

	DocIndexerXmlHandlers ourDocIndexer;

	public MetadataFetcherSonarCmdi(DocIndexer docIndexer) {
		super(docIndexer);

		if (docIndexer instanceof DocIndexerXmlHandlers) {
			// Should always be the case, except when testing
			ourDocIndexer = (DocIndexerXmlHandlers)docIndexer;
		}

		if (metadataZipFile == null) {
			String zipFilePath = docIndexer.getParameter("metadataZipFile");
			if (zipFilePath == null) {
				zipFilePath = docIndexer.getParameter("metadataDir");
				if (zipFilePath == null)
					throw new RuntimeException("For OpenSonar metadata, specify metadataZipFile or metadataDir in indexer.properties!");
				metadataDir = new File(zipFilePath);
			} else {
				try {
					metadataZipFile = new ZipFile(new File(zipFilePath));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		metadataPathInZip = docIndexer.getParameter("metadataPath", "");
		if (metadataPathInZip.length() == 0)
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
		if (ourDocIndexer != null) {
			fromInputFile = luceneDoc.get("fromInputFile");
		} else {
			// TEST
			fromInputFile = TEST_FROM_INPUT_FILE;
		}

		fromInputFile = fromInputFile.replaceAll("\\\\", "/");
		int lastSlash = fromInputFile.lastIndexOf("/");
		int penultimateSlash = fromInputFile.lastIndexOf("/", lastSlash - 1);
		String metadataFile = fromInputFile.substring(penultimateSlash + 1);
		metadataFile = metadataFile.replaceAll("\\.folia\\.", ".cmdi.");

		try {
			InputStream is;
			if (metadataZipFile != null) {
				ZipEntry e = metadataZipFile.getEntry(metadataPathInZip + metadataFile);
				if (e == null) {
					//throw new RuntimeException("Entry in zip not found: " + metadataPathInZip + metadataFile);
					System.err.println("*** ERROR, metadata entry not found: " + metadataPathInZip + metadataFile);
					return;
				}
				is = metadataZipFile.getInputStream(e);
			} else {
				File f = new File(new File(metadataDir, metadataPathInZip), metadataFile);
				is = new FileInputStream(f);
			}

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
			ourDocIndexer.addMetadataField("AuthorNameOrPseudonym", authorName);
			ourDocIndexer.addMetadataField("AuthorNameOrPseudonymSearch", authorNameAndPseudonym);

			/*
			// DISABLED because this can be specified in indextemplate.json now
			String sex = luceneDoc.get("Sex");
			if (sex == null || sex.length() == 0) {
				ourDocIndexer.addMetadataField("Sex", "unknown");
			}
			String translated = luceneDoc.get("Translated");
			if (translated == null || translated.length() == 0) {
				ourDocIndexer.addMetadataField("Translated", "unknown");
			}
			*/

			if (ourDocIndexer != null) {
				// Store metadata XML in content store and corresponding id in Lucene document
				ContentStore cs = ourDocIndexer.getIndexer().getContentStore("metadata");
				int id = cs.store(capturingReader.getContent());
				luceneDoc.add(new IntField("metadataCid", id, Store.YES));
			} else {
				// TEST; print start of metadata file
				System.out.println(capturingReader.getContent().substring(0, 1000));
			}

			if (metadataZipFile == null)
				is.close();
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

		Map<String, String> indexFieldAs = new HashMap<String, String>();

		public MetadataParser() {
			indexFieldAs.put("iso-639-3-code", "Language-iso-code");
			indexFieldAs.put("Name", "AuthorName");
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
				String indexAs = indexFieldAs.get(localName);
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

}
