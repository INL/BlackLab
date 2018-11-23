package nl.inl.blacklab.indexers.config;

import org.junit.Assert;
import org.junit.Test;

public class TestProcessingOperations {
    
    @Test
    public void testOpPartOfSpeechParse() {
        Assert.assertEquals("NOU-C", DocIndexerConfig.opParsePartOfSpeech("NOU-C()", "_"));
        Assert.assertEquals("NOU-C", DocIndexerConfig.opParsePartOfSpeech("NOU-C(gender=f,number=pl)", "_"));
        Assert.assertEquals("f", DocIndexerConfig.opParsePartOfSpeech("NOU-C(gender=f,number=pl)", "gender"));
        Assert.assertEquals("pl", DocIndexerConfig.opParsePartOfSpeech("NOU-C(gender=f,number=pl)", "number"));
        Assert.assertEquals("", DocIndexerConfig.opParsePartOfSpeech("NOU-C(gender=f,number=pl)", "type"));
    }
    
}
