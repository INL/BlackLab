/**
 * Embedded Solr test.
 *
 * Inspired by code from Mtas, https://github.com/meertensinstituut/mtas/
 */

package org.ivdnt.blacklab.solr;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class TestSearchComponent {

    static final String CORE_NAME = "testcore";

    public static final String FIELD_TEST1 = "test1";

    public static final String FIELD_TEST2 = "test2";

    @BeforeClass
    public static void prepareClass() throws Exception
    {
        Path resourcePath = Paths.get("src", "test", "resources", "solrDir");
        Path confTemplatePath = resourcePath.resolve("conf");

        // Create srver, core and add document
        SolrTestServer.createEmbeddedServer(CORE_NAME, resourcePath);
        SolrTestServer.setLogLevel("WARN"); // show log messages
        SolrTestServer.createCore(CORE_NAME, confTemplatePath);
        SolrClient client = SolrTestServer.client();
        client.add(CORE_NAME, testDoc());
        client.commit(CORE_NAME);

        // (component is already added in solrconfig.xml, so this call is not needed,
        //  and the method unfortunately doesn't work yet anyway, see comment. we'll look at it later)
        //SolrTestServer.addSearchComponent(CORE_NAME, "apply-xslt", ApplyXsltComponent.class.getCanonicalName());
    }

    private static SolrInputDocument testDoc() {
        SolrInputDocument testDoc = new SolrInputDocument();
        testDoc.addField("id", "THIS-IS-A-TEST-0002");
        testDoc.addField(FIELD_TEST1, "The quick brown fox");
        testDoc.addField(FIELD_TEST2, "woohoo");
        return testDoc;
    }

    @AfterClass
    public static void cleanUpClass() {
        SolrTestServer.close();
    }

    @Test
    public void testDisableBlackLab() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        QueryResponse queryResponse = SolrTestServer.client().query(CORE_NAME, solrParams);
        Assert.assertNull(queryResponse.getResponse().get("blacklabResponse"));
    }

    @Ignore // we're getting errors
    @Test
    public void testEnableBlackLabButNoOps() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        solrParams.add("bl", "true"); // activate our component
        QueryResponse queryResponse = SolrTestServer.client().query(CORE_NAME, solrParams);
        Assert.assertNull(queryResponse.getResponse().get("blacklabResponse"));
    }

    @Ignore // we don't have a BlackLab index we can load yet
    @Test
    public void testSearch() throws SolrServerException, IOException {
        ModifiableSolrParams solrParams = new ModifiableSolrParams();
        solrParams.add(CommonParams.Q, "*:*");
        solrParams.add("bl", "true"); // activate our component
        solrParams.add("bl.pattfield", FIELD_TEST1); // activate our component
        solrParams.add("bl.patt", "[]"); // activate our component

        System.err.println(CORE_NAME);
        QueryResponse queryResponse = SolrTestServer.client().query(CORE_NAME, solrParams);
        System.err.println("RESPONSE: " + queryResponse.getResponse());
        /*for (SolrDocument document: queryResponse.getResults()) {
            System.out.println(document);
        }*/
    }
}
