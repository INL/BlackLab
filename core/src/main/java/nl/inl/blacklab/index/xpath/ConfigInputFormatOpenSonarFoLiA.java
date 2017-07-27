package nl.inl.blacklab.index.xpath;

public class ConfigInputFormatOpenSonarFoLiA extends ConfigInputFormat {

    public ConfigInputFormatOpenSonarFoLiA() {
        setName("BuiltinOpenSonarFolia");
        setDisplayName("OpenSoNaR FoLiA file format");
        setDescription("The file format used by OpenSonar.");

        // Basic config: namespaces, document element
        addNamespace("", "http://ilk.uvt.nl/folia");
        setDocumentPath("//FoLiA");
        setStore(true); // (this is the default)

        // Annotated field config
        ConfigAnnotatedField annotatedField = getOrCreateAnnotatedField("contents");
        annotatedField.setContainerPath("text");
        annotatedField.setWordPath(".//w");
        annotatedField.setTokenPositionIdPath("@xml:id");
        //annotatedField.setPunctPath("//text()[not(ancestor::w)]");
        annotatedField.addInlineTag(".//s");
        annotatedField.addInlineTag(".//p");
        annotatedField.addAnnotation(new ConfigAnnotation("word", "t[not(@class)]"));
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
        addAnnotatedField(annotatedField);

        ConfigMetadataFieldGroup g = new ConfigMetadataFieldGroup("Tab1");
        g.addField("test1");
        g.addField("test2");
        addMetadataFieldGroup(g);
        g = new ConfigMetadataFieldGroup("Tab2");
        g.addField("test3");
        g.addField("test4");
        addMetadataFieldGroup(g);
        g = new ConfigMetadataFieldGroup("Rest");
        g.setAddRemainingFields(true);
        addMetadataFieldGroup(g);

//        // Metadata fields config
//        ConfigMetadataBlock b = new ConfigMetadataBlock();
//        b.setContainerPath("metadata/annotations");
//        ConfigMetadataField f = new ConfigMetadataField("Test", "test");
//        f.setDescription("Een testveld.");
//        f.setType(FieldType.UNTOKENIZED);
//        f.setUiType("select");
//        f.setUnknownCondition(UnknownCondition.MISSING_OR_EMPTY);
//        f.setUnknownValue("(unknown!)");
//        f.addDisplayValue("test2", "Een testwaarde.");
//        f.addDisplayOrder("test3");
//        f.addDisplayOrder("test2");
//        f.addDisplayOrder("test1");
//        b.addMetadataField(f);
//        //b.addMetadataField(new ConfigMetadataField("TokenAnnotation", "concat(token-annotation/@annotator, '-', token-annotation/@annotatortype, '-', token-annotation/@set)"));
//        //b.addMetadataField(new ConfigMetadataField("name()", "@annotator", "*")); // forEach
//        addMetadataBlock(b);
    }

//    public static void main(String[] args) throws Exception {
//        if (args.length > 1) {
//            System.err.println("Specify input file.");
//            System.exit(1);
//        }
//        String defaultFile = File.separatorChar == '/' ? "/home/jan/blacklab/data" : "D:/werk/projects";
//        String filePath = args.length == 1 ? args[0] : defaultFile + "/VanKampenHeel_Clean_Folia.xml";
//        File inputFile = new File(filePath);
//
//        DocIndexerXPath ind = new DocIndexerXPath();
//        ind.setConfigInputFormat(new ConfigInputFormatOpenSonarFoLiA());
//        ind.setDocumentName(inputFile.getName());
//        ind.setDocument(inputFile, Indexer.DEFAULT_INPUT_ENCODING);
//        ind.index();
//    }

}
