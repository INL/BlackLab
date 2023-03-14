package org.ivdnt.blacklab.solr;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.solr.common.util.SolrJSONWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.server.datastream.DataStream;

public class TestDataStreamSolr {

    DataStream solrStream(SolrQueryResponse rsp) {
        return new DataStreamSolr(rsp).startDocument(Constants.SOLR_BLACKLAB_SECTION_NAME);
    }

    @Test
    public void testSimpleValues() throws IOException {
        SolrQueryResponse rsp = new SolrQueryResponse();
        solrStream(rsp).entry("number", 1).endDocument();
        Assert.assertEquals("{\"number\":1}", serialize(rsp));
        rsp = new SolrQueryResponse();
        solrStream(rsp).entry("str", "test\"").endDocument();
        Assert.assertEquals("{\"str\":\"test\\\"\"}", serialize(rsp));
    }

    private static String serialize(SolrQueryResponse rsp) throws IOException {
        StringWriter sw = new StringWriter();
        try (SolrJSONWriter writer = new SolrJSONWriter(sw)) {
            writer.writeObj(rsp.getValues());
        }
        return sw.toString();
    }

    @Test
    public void testList() throws IOException {
        SolrQueryResponse rsp = new SolrQueryResponse();
        solrStream(rsp).entry("list", List.of(1, 2, 3)).endDocument();
        Assert.assertEquals("{\"list\":[1,2,3]}", serialize(rsp));
        rsp = new SolrQueryResponse();
        solrStream(rsp).entry("list", List.of("aap", "noot")).endDocument();
        Assert.assertEquals("{\"list\":[\"aap\",\"noot\"]}", serialize(rsp));
    }

    @Test
    public void testMap() throws IOException {
        SolrQueryResponse rsp = new SolrQueryResponse();
        Map<String, Integer> m = new TreeMap<>(Map.of("test", 1, "noot", 2, "mies", 3));
        solrStream(rsp).entry("map", m).endDocument();
        Assert.assertEquals("{\"map\":{\"mies\":3,\"noot\":2,\"test\":1}}", serialize(rsp));
    }

}
