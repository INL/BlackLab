package nl.inl.blacklab.index.config;

import nl.inl.blacklab.index.config.ConfigLinkedDocument.MissingLinkPathAction;

public class TEST_ConfigInputFormatOpenSonarFoliaCmdi extends ConfigInputFormat {

    public TEST_ConfigInputFormatOpenSonarFoliaCmdi() {
        setName("BuiltinOpenSonarFoliaCmdi");
        setDisplayName("OpenSoNaR FoLiA file format with CMDI metadata link");

        // Copy everything from this format
        setBaseFormat("BuiltinOpenSonarFolia");

        // Linked document config
        ConfigLinkedDocument linkedMetadata = getOrCreateLinkedDocument("metadata");
        linkedMetadata.setStore(true);
        linkedMetadata.addLinkValue(new ConfigLinkValue("metadata[@type='test']/@src"));
        linkedMetadata.addLinkValue(new ConfigLinkValue("metadata[@type='test']/@id"));
        linkedMetadata.setIfLinkPathMissing(MissingLinkPathAction.FAIL);

        // File
        linkedMetadata.setInputFile("$1");

        // HTTP
        //linkedMetadata.setInputFile("http://localhost/$1");

        // Zip
        //linkedMetadata.setInputFile("test.cmdi.zip");
        //linkedMetadata.setPathInsideArchive("$1");

        // HTTP+ZIP
        //linkedMetadata.setInputFile("http://localhost/test.cmdi.zip");
        //linkedMetadata.setPathInsideArchive("$1");

        // TGZ
        //linkedMetadata.setInputFile("test.cmdi.tgz");
        //linkedMetadata.setPathInsideArchive("$1");

        // HTTP+TGZ
        //linkedMetadata.setInputFile("http://localhost/test.cmdi.tgz");
        //linkedMetadata.setPathInsideArchive("$1");

        linkedMetadata.setDocumentPath("/CMD/Components/SoNaRcorpus/Text[@ComponentId = $2]");
        linkedMetadata.setInputFormat("OpenSonarCmdi");
        addLinkedDocument(linkedMetadata);
    }

}
