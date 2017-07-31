package nl.inl.blacklab.index.xpath;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.util.ExUtil;
import nl.inl.util.FileUtil;

/**
 * An indexer for tabular file formats, such as tab-separated
 * or comma-separated values.
 */
public class DocIndexerTabular extends DocIndexerConfig {

    Iterable<CSVRecord> records;

    CSVFormat tabularFormat = CSVFormat.EXCEL;

    StringBuilder csvData;

    public DocIndexerTabular() {
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() > 1)
            throw new InputFormatConfigException("Tabular type can only have 1 annotated field");
        super.setConfigInputFormat(config);
        switch (config.getTabularOptions().getType()) {
        case TSV:
            tabularFormat = CSVFormat.TDF;
            break;
        case CSV:
            tabularFormat = CSVFormat.EXCEL;
            break;
        default:
            throw new InputFormatConfigException("Unknown tabular type " + config.getTabularOptions().getType() + " (use csv or tsv)");
        }
    }

    @Override
    public void setDocument(File file, Charset defaultCharset) throws FileNotFoundException {
        Reader r = FileUtil.openForReading(file, defaultCharset);
        setDocument(r);
    }

    @Override
    public void setDocument(byte[] contents, Charset defaultCharset) {
        setDocument(new ByteArrayInputStream(contents), defaultCharset);
    }

    @Override
    public void setDocument(InputStream is, Charset defaultCharset) {
        setDocument(new InputStreamReader(new BOMInputStream(is), defaultCharset));
    }

    @Override
    public void setDocument(Reader reader) {
        try {
            BufferedReader br = reader instanceof BufferedReader ? (BufferedReader)reader : new BufferedReader(reader);
            records = tabularFormat.parse(br);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void index() throws Exception {
        super.index();

        startDocument();

        csvData = new StringBuilder();
        try (CSVPrinter p = new CSVPrinter(csvData, tabularFormat)) {
            // For the configured annotated field...
            for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields().values()) {
                setCurrentComplexField(annotatedField.getName());

                // For each token position
                for (CSVRecord record: records) {

                    beginWord();
                    if (getStoreDocuments()) {
                        p.printRecord(record);
                    }

                    // For each annotation
                    for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                        // Either column number of name
                        String vp = annotation.getValuePath();
                        String value;
                        if (vp.matches("\\d+"))
                            value = record.get(Integer.parseInt(vp) - 1);
                        else
                            value = record.get(vp);
                        annotation(annotation.getName(), value, 1, null);
                    }
                    endWord();

                }
            }
        }

        endDocument();
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // documentExpr is ignored because tabular format files always contain 1 document
        try {
            index();
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(csvData.toString());
    }

    @Override
	public int getCharacterPosition() {
        return csvData.length();
	}

}
