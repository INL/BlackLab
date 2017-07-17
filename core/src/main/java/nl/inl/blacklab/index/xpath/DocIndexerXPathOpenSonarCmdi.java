package nl.inl.blacklab.index.xpath;

import java.io.File;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Indexer;

/**
 * An indexer for the OpenSonarCmdi format
 */
public class DocIndexerXPathOpenSonarCmdi extends DocIndexerXPath {

    DocIndexerXPathOpenSonarCmdi() {
        setConfigInputFormat(getConfig());
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Specify input file.");
            System.exit(1);
        }
        String defaultFile = File.separatorChar == '/' ? "/home/jan/blacklab/data" : "D:/werk/projects";
        String filePath = args.length == 1 ? args[0] : defaultFile + "/test.cmdi.xml";
        File inputFile = new File(filePath);

        DocIndexer ind = new DocIndexerXPathOpenSonarCmdi();
        ind.setDocumentName(inputFile.getName());
        ind.setDocument(inputFile, Indexer.DEFAULT_INPUT_ENCODING);
        ind.index();
    }

    public static ConfigInputFormat getConfig() {
        ConfigInputFormat config = new ConfigInputFormat();
        config.setName("OpenSonarCmdi");
        config.setDisplayName("OpenSoNaR CMDI metadata");

        // Basic config: namespaces, document element
        config.setNamespaceAware(true);
        config.addNamespace("", "http://www.clarin.eu/cmd/");
        config.setDocumentPath("/CMD/Components/SoNaRcorpus/Text");

        // Metadata fields config
        config.setMetadataContainerPath(".");
        config.addMetadataField(new ConfigMetadataField("name()", ".", "//*[not(*) and text()]")); // "all leaf elements containing text"
        return config;
    }

}
