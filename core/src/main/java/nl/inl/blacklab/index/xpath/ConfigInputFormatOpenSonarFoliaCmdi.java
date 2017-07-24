package nl.inl.blacklab.index.xpath;

import nl.inl.blacklab.index.xpath.ConfigLinkedDocument.MissingLinkPathAction;

public class ConfigInputFormatOpenSonarFoliaCmdi extends ConfigInputFormat {

    public ConfigInputFormatOpenSonarFoliaCmdi() {
        setName("OpenSonarFoliaCmdi");
        setDisplayName("OpenSoNaR FoLiA file format with CMDI metadata link");

        // Copy everything from this format
        setBaseFormat("OpenSonarFolia");

        // Linked document config
        ConfigLinkedDocument linkedMetadata = getOrCreateLinkedDocument("metadata");
        linkedMetadata.setStore(true);
        linkedMetadata.addLinkPath("metadata[@type='test']/@src");
        linkedMetadata.addLinkPath("metadata[@type='test']/@id");
        linkedMetadata.setIfLinkPathMissing(MissingLinkPathAction.FAIL);
        linkedMetadata.setInputFile("$1");
        linkedMetadata.setDocumentPath("/CMD/Components/SoNaRcorpus/Text[@ComponentId = $2]");
        linkedMetadata.setInputFormat("OpenSonarCmdi");
        addLinkedDocument(linkedMetadata);
    }

}
