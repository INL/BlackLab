package nl.inl.blacklab.index.xpath;

import java.io.File;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.xpath.ConfigLinkedDocument.MissingLinkPathAction;

public class DocIndexerXPathOpenSonarFoLiA extends DocIndexerXPath {

    public DocIndexerXPathOpenSonarFoLiA() {
        setConfigInputFormat(getConfig());
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Specify input file.");
            System.exit(1);
        }
        String defaultFile = File.separatorChar == '/' ? "/home/jan/blacklab/data" : "D:/werk/projects";
        String filePath = args.length == 1 ? args[0] : defaultFile + "/VanKampenHeel_Clean_Folia.xml";
        File inputFile = new File(filePath);

        DocIndexer ind = new DocIndexerXPathOpenSonarFoLiA();
        ind.setDocumentName(inputFile.getName());
        ind.setDocument(inputFile, Indexer.DEFAULT_INPUT_ENCODING);
        ind.index();
    }

    public static ConfigInputFormat getConfig() {
        ConfigInputFormat config = new ConfigInputFormat();
        config.setName("OpenSonarFolia");
        config.setDisplayName("OpenSoNaR FoLiA file format");

        // Basic config: namespaces, document element
        config.setNamespaceAware(true);
        config.addNamespace("", "http://ilk.uvt.nl/folia");
        config.setDocumentPath("//FoLiA");

        // Annotated field config
        ConfigAnnotatedField annotatedField = new ConfigAnnotatedField();
        annotatedField.setFieldName("contents");
        annotatedField.setContainerPath("text");
        annotatedField.setWordsPath("//w");
        annotatedField.setPunctPath("//text()[not(ancestor::w)]");
        annotatedField.addInlineTag(new ConfigInlineTag("//s"));
        annotatedField.addInlineTag(new ConfigInlineTag("//p"));
        annotatedField.addAnnotation(new ConfigAnnotation("word", "t[not(@class)]/text()"));
        annotatedField.addAnnotation(new ConfigAnnotation("lemma", "lemma/@class"));
        annotatedField.addAnnotation(new ConfigAnnotation("pos", "pos/@head"));
        annotatedField.addAnnotation(new ConfigAnnotation("xmlid", "@xml:id"));
        config.addAnnotatedField(annotatedField);

        // Metadata fields config
        config.setMetadataContainerPath("metadata/annotations");
        config.addMetadataField(new ConfigMetadataField("Test", "test"));
        config.addMetadataField(new ConfigMetadataField("TokenAnnotation", "concat(token-annotation/@annotator, '-', token-annotation/@annotatortype, '-', token-annotation/@set)"));
        config.addMetadataField(new ConfigMetadataField("name()", "@annotator", "*")); // forEach

        // Linked document config
        ConfigLinkedDocument linkedMetadata = new ConfigLinkedDocument("metadata");
        linkedMetadata.setStore(true);
        linkedMetadata.addLinkPath("/FoLiA/metadata[@src='test.cmdi.xml']/@id");
        linkedMetadata.setIfLinkPathMissing(MissingLinkPathAction.FAIL);
        linkedMetadata.setInputFile("test.cmdi.xml");
        linkedMetadata.setDocumentPath("/CMD/Components/SoNaRcorpus/Text[@ComponentId = $1]");
        linkedMetadata.setInputFormat("OpenSonarCmdi");
        config.addLinkedDocument(linkedMetadata);

        return config;
    }

}
