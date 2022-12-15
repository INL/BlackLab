package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;

public class TestStandoffSpans {

    @Test
    public void testDuplicatObjects() throws IOException {
        DocIndexerFactoryConfig factoryConfig = new DocIndexerFactoryConfig();
        ClassLoader classLoader = this.getClass().getClassLoader();
        File file = new File(classLoader.getResource("standoff/tei-standoff-spans.blf.yaml").getFile());
        ConfigInputFormat fmt = factoryConfig.load("tei-standoff-spans", file).orElseThrow();
        System.err.println("BLA");
    }

}
