package nl.inl.blacklab.search.datastream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamAbstract;

public class TestDataStream {

    DataStream jsonStream(StringWriter sw) {
        PrintWriter pw = new PrintWriter(sw);
        return DataStreamAbstract.create(DataFormat.JSON, pw, false, null);
    }

    @Test
    public void testSimpleValues() {
        StringWriter sw = new StringWriter();
        jsonStream(sw).value(1);
        Assert.assertEquals("1", sw.toString());
        sw = new StringWriter();
        jsonStream(sw).value("test\"");
        Assert.assertEquals("\"test\\\"\"", sw.toString());
    }

    @Test
    public void testList() {
        StringWriter sw = new StringWriter();
        jsonStream(sw).value(List.of(1, 2, 3));
        Assert.assertEquals("[1,2,3]", sw.toString());

        sw = new StringWriter();
        jsonStream(sw).value(List.of("aap", "noot"));
        Assert.assertEquals("[\"aap\",\"noot\"]", sw.toString());
    }

    @Test
    public void testMap() {
        StringWriter sw = new StringWriter();
        Map<String, Integer> m = new TreeMap<>(Map.of("test", 1, "noot", 2, "mies", 3));
        jsonStream(sw).value(m);
        Assert.assertEquals("{\"mies\":3,\"noot\":2,\"test\":1}", sw.toString());
    }

}
