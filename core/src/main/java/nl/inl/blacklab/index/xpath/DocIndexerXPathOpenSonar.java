package nl.inl.blacklab.index.xpath;

import java.io.File;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Indexer;

public class DocIndexerXPathOpenSonar extends DocIndexerXPath {

    @Override
    protected void configure(ConfigInputFormat config) {
        // Basic config: namespaces, document element
        config.setNamespaceAware(true);
        config.addNamespace("", "http://ilk.uvt.nl/folia");
        config.setXPathDocument("//FoLiA");

        // Annotated field config
        ConfigAnnotatedField annotatedField = new ConfigAnnotatedField();
        annotatedField.setFieldName("contents");
        annotatedField.setXPathBody("text");
        annotatedField.setXPathWords("//w");
        annotatedField.setXPathPunct("//text()[not(ancestor::w)]");
        annotatedField.addXPathInlineTag("//s");
        annotatedField.addXPathInlineTag("//p");
        annotatedField.addAnnotation(new ConfigAnnotation("word", "t[not(@class)]/text()"));
        annotatedField.addAnnotation(new ConfigAnnotation("lemma", "lemma/@class"));
        annotatedField.addAnnotation(new ConfigAnnotation("pos", "pos/@head"));
        annotatedField.addAnnotation(new ConfigAnnotation("xmlid", "@xml:id"));
        config.addAnnotatedField(annotatedField);

        // Metadata fields config
        config.setXPathMetadataContainer("metadata/annotations");
        config.addMetadataField(new ConfigMetadataField("Test", "test"));
        config.addMetadataField(new ConfigMetadataField("TokenAnnotation", "concat(token-annotation/@annotator, '-', token-annotation/@annotatortype, '-', token-annotation/@set)"));
        config.addMetadataField(new ConfigMetadataField("name()", "@annotator", "*")); // forEach
        
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            System.err.println("Specify input file.");
            System.exit(1);
        }
        String defaultFile = File.separatorChar == '/' ? "/home/jan/blacklab/data" : "D:/werk/projects";
        String filePath = args.length == 1 ? args[0] : defaultFile + "/VanKampenHeel_Clean_Folia.xml";
        File inputFile = new File(filePath);

        DocIndexer ind = new DocIndexerXPathOpenSonar();
        ind.setDocumentName(inputFile.getName());
        ind.setDocument(inputFile, Indexer.DEFAULT_INPUT_ENCODING);
        ind.index();
    }

}
