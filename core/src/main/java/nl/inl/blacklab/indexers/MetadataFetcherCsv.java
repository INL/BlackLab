package nl.inl.blacklab.indexers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.MetadataFetcher;

/**
 * Metadata fetcher for csv files. The path to the csv file is read from
 * parameter 'metadataFile'. The csv file should be utf-8 encoded. This metadata
 * fetcher assumes a csv file with a header. It also assumes the csv file
 * contains a column 'id'. The id should be equal to the file name of the
 * indexed document.
 *
 * Example indexer.properties:
 * metadataFetcherClass=nl.inl.blacklab.indexers.MetadataFetcherCsv
 * metadataFile=metadata.csv
 */
public class MetadataFetcherCsv extends MetadataFetcher {
    private Iterable<CSVRecord> metadata;

    @SuppressWarnings("deprecation")
    public MetadataFetcherCsv(DocIndexer docIndexer) {
        super(docIndexer);

        String metadataFileName = docIndexer.getParameter("metadataFile");

        try (
                Reader reader = new InputStreamReader(new BOMInputStream(new FileInputStream(metadataFileName)),
                        "UTF-8");

                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                        .withFirstRecordAsHeader()
                        .withIgnoreHeaderCase()
                        .withTrim());) {

            metadata = csvParser.getRecords();
            reader.close();
        } catch (FileNotFoundException ex) {
            System.err.println("Metadata file \"" + metadataFileName + "\" not found. Not adding metadata.");
        } catch (IOException ex) {
            System.err.println("Error while reading \"" + metadataFileName + "\". Is it a valid csv file?");
        }
    }

    @Override
    public void addMetadata() {

        String fromInputFile;
        Map<String, String> map;
        Document luceneDoc = docIndexer.getCurrentLuceneDoc();
        fromInputFile = luceneDoc.get("fromInputFile");

        for (CSVRecord row : metadata) {
            // TODO: use document id instead of document file name (it is unclear to me how to get the
            // document id from the lucene doc)
            // TODO: what happens if the csv file does not contain an id-column?
            // TODO: some texts have multiple authors. How to deal with that?
            if (fromInputFile.equals(row.get("id"))) {
                map = row.toMap();
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    // Do not add the id (again)
                    if (!entry.getKey().equals("id")) {
                        docIndexer.addMetadataField(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }
}
