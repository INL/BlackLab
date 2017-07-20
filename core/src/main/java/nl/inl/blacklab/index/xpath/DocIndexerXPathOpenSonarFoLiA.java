package nl.inl.blacklab.index.xpath;

import java.io.File;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.xpath.ConfigLinkedDocument.MissingLinkPathAction;

public class DocIndexerXPathOpenSonarFoLiA extends DocIndexerXPath {

    public DocIndexerXPathOpenSonarFoLiA() {
        setConfigInputFormat(getConfig());
    }

    public static ConfigInputFormat getConfig() {
        ConfigInputFormat config = new ConfigInputFormat();
        config.setName("OpenSonarFolia");
        config.setDisplayName("OpenSoNaR FoLiA file format");

        // Basic config: namespaces, document element
        config.setNamespaceAware(true);
        config.addNamespace("", "http://ilk.uvt.nl/folia");
        config.setDocumentPath("//FoLiA");
        config.setStore(true); // (this is the default)

        // Annotated field config
        ConfigAnnotatedField annotatedField = new ConfigAnnotatedField();
        annotatedField.setFieldName("contents");
        annotatedField.setContainerPath("text");
        annotatedField.setWordsPath(".//w");
        annotatedField.setTokenPositionIdPath("@xml:id");
        //annotatedField.setPunctPath("//text()[not(ancestor::w)]");
        annotatedField.addInlineTag(new ConfigInlineTag(".//s"));
        annotatedField.addInlineTag(new ConfigInlineTag(".//p"));
        annotatedField.addAnnotation(new ConfigAnnotation("word", "t[not(@class)]/text()"));
        annotatedField.addAnnotation(new ConfigAnnotation("lemma", "lemma/@class"));
        ConfigAnnotation pos = new ConfigAnnotation("pos", "@class");
        pos.setBasePath("pos");
        pos.addSubAnnotation(new ConfigAnnotation("head", "@head"));
        pos.addSubAnnotation(new ConfigAnnotation("@subset", "@class", "feat"));
        annotatedField.addAnnotation(pos);
        annotatedField.addAnnotation(new ConfigAnnotation("xmlid", "@xml:id"));
        //annotatedField.addAnnotation(new ConfigAnnotation("rating", "/FoLiA/standoff-annotations/rating[@id = '$1']/@value", Arrays.asList("@xml:id")));
        ConfigStandoffAnnotations standoff = new ConfigStandoffAnnotations("/FoLiA/standoff-annotations/rating", "@id");
        standoff.addAnnotation(new ConfigAnnotation("rating", "@value"));
        annotatedField.addStandoffAnnotation(standoff);
        config.addAnnotatedField(annotatedField);

//        // Metadata fields config
//        ConfigMetadataBlock b = new ConfigMetadataBlock();
//        b.setContainerPath("metadata/annotations");
//        b.addMetadataField(new ConfigMetadataField("Test", "test"));
//        b.addMetadataField(new ConfigMetadataField("TokenAnnotation", "concat(token-annotation/@annotator, '-', token-annotation/@annotatortype, '-', token-annotation/@set)"));
//        b.addMetadataField(new ConfigMetadataField("name()", "@annotator", "*")); // forEach
//        config.addMetadataBlock(b);

        // Linked document config
        ConfigLinkedDocument linkedMetadata = new ConfigLinkedDocument("metadata");
        linkedMetadata.setStore(true);
        linkedMetadata.addLinkPath("metadata[@type='test']/@src");
        linkedMetadata.addLinkPath("metadata[@type='test']/@id");
        linkedMetadata.setIfLinkPathMissing(MissingLinkPathAction.FAIL);
        linkedMetadata.setInputFile("$1");
        linkedMetadata.setDocumentPath("/CMD/Components/SoNaRcorpus/Text[@ComponentId = $2]");
        linkedMetadata.setInputFormat("OpenSonarCmdi");
        config.addLinkedDocument(linkedMetadata);

        return config;
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

}
