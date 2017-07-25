package nl.inl.blacklab.index.xpath;

import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;

/**
 * An indexer for the OpenSonarCmdi format
 */
public class ConfigInputFormatOpenSonarCmdi extends ConfigInputFormat {

    public ConfigInputFormatOpenSonarCmdi() {
        setName("OpenSonarCmdi");
        setDisplayName("OpenSoNaR CMDI metadata");

        // Basic config: namespaces, document element
        addNamespace("", "http://www.clarin.eu/cmd/");
        setDocumentPath("/CMD/Components/SoNaRcorpus/Text");

        ConfigMetadataBlock b;

        // Metadata fields config
        b = new ConfigMetadataBlock();
        b.setContainerPath("metadata/annotations");
        ConfigMetadataField f = new ConfigMetadataField("Test", "test");
        f.setDescription("Een testveld.");
        f.setType(FieldType.UNTOKENIZED);
        f.setUiType("select");
        f.setUnknownCondition(UnknownCondition.MISSING_OR_EMPTY);
        f.setUnknownValue("(unknown!)");
        f.addDisplayValue("test2", "Een testwaarde.");
        f.addDisplayOrder("test3");
        f.addDisplayOrder("test2");
        f.addDisplayOrder("test1");
        b.addMetadataField(f);
        //b.addMetadataField(new ConfigMetadataField("TokenAnnotation", "concat(token-annotation/@annotator, '-', token-annotation/@annotatortype, '-', token-annotation/@set)"));
        //b.addMetadataField(new ConfigMetadataField("name()", "@annotator", "*")); // forEach
        addMetadataBlock(b);

        // Metadata fields config
        b = createMetadataBlock();
        b.setContainerPath(".");
        b.addMetadataField(new ConfigMetadataField("name()", ".", "//*[not(*) and text()]")); // "all leaf elements containing text"

        addIndexFieldAs("AnnotationType-SoNaR", "SonarAnnotationType");
    }

}
